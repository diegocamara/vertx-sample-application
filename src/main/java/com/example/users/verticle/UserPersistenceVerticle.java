package com.example.users.verticle;

import com.example.users.Actions;
import com.example.users.ConstantsMessage;
import com.example.users.Events;
import com.example.users.JWTUtil;
import com.example.users.MessageProperties;
import com.example.users.entity.User;

import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

public class UserPersistenceVerticle extends AbstractVerticle {

  public static final String EMPTY = "";
  private final String COLLECTION_NAME = "users";

  private MongoClient mongoClient;

  @Override
  public void start(Promise<Void> startPromise) throws Exception {

    Json.mapper.findAndRegisterModules();
    Json.prettyMapper.findAndRegisterModules();

    mongoClient = MongoClient.createShared(vertx, new JsonObject().put("db_name", config().getString("dbName")).put("connection_string", "mongodb://" + config().getString("dbUrl") + ":" + config().getInteger("dbPort")));

    EventBus eventBus = vertx.eventBus();

    MessageConsumer<JsonObject> userConsumer = eventBus.consumer(Events.USER_EVENTS);

    userConsumer.handler(message -> {

      String action = message.body().getString(MessageProperties.MESSAGE_ACTION);

      switch (action) {
        case Actions.REGISTER: {
          checkExistingUser(message).setHandler(asyncResult -> {
            if (asyncResult.result()) {
              message.fail(HttpResponseStatus.CONFLICT.code(), ConstantsMessage.EMAIL_ALREADY_EXISTS);
            } else {
              createUser(message);
            }
          });
          break;
        }
        case Actions.LOGIN: {
          findUser(message);
          break;
        }
        case Actions.LOAD_USER: {
          findUserById(message);
          break;
        }
        default: {
          message.fail(1, "Unknown action" + message.body());
          break;
        }

      }


    });

    startPromise.complete();

  }

  private void findUserById(Message<JsonObject> message) {

    String userId = message.body().getString(MessageProperties.MESSAGE_VALUE_USER_ID);

    findUserBy("_id", userId).setHandler(findUserByIdAsyncResponse -> {

      if (findUserByIdAsyncResponse.succeeded()) {

        if (findUserByIdAsyncResponse.result() != null) {
          JsonObject replyMessage = new JsonObject();
          replyMessage.put(MessageProperties.MESSAGE_RESPONSE_FIND_USER, findUserByIdAsyncResponse.result());
          message.reply(replyMessage);
        } else {
          message.fail(HttpResponseStatus.NOT_FOUND.code(), HttpResponseStatus.NOT_FOUND.toString());
        }
      } else {
        message.fail(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), HttpResponseStatus.INTERNAL_SERVER_ERROR.toString());
      }

    });

  }

  private void findUser(Message<JsonObject> message) {

    JsonObject login = message.body().getJsonObject(MessageProperties.MESSAGE_VALUE_LOGIN);

    findUserBy("email", login.getString("email")).setHandler(findUserByEmailResult -> {

      if (findUserByEmailResult.succeeded()) {

        JsonObject storedUser = findUserByEmailResult.result();

        if (storedUser != null) {

          String plainTextPassword = login.getString("password");
          String hashedPassword = storedUser.getString("password");

          if (BCrypt.checkpw(plainTextPassword, hashedPassword)) {

            JsonObject resultUser = findUserByEmailResult.result();
            resultUser.remove("_id");
            resultUser.remove("password");

            JsonObject replyMessage = new JsonObject();
            replyMessage.put(MessageProperties.MESSAGE_RESPONSE_LOGIN, resultUser);
            message.reply(replyMessage);

          } else {

            message.fail(HttpResponseStatus.UNAUTHORIZED.code(), ConstantsMessage.USER_OR_EMAIL_DO_NOT_EXISTS);

          }


        } else {

          message.fail(HttpResponseStatus.UNAUTHORIZED.code(), ConstantsMessage.USER_OR_EMAIL_DO_NOT_EXISTS);

        }

      }

    });


  }

  private Future<JsonObject> findUserBy(String key, String value) {
    Promise<JsonObject> findUserByEmail = Promise.promise();

    JsonObject query = new JsonObject();
    query.put(key, value);

    mongoClient.findOne(COLLECTION_NAME, query, null, findUserByEmailAsyncResponse -> {
      if (findUserByEmailAsyncResponse.succeeded()) {
        findUserByEmail.complete(findUserByEmailAsyncResponse.result());
      } else {
        findUserByEmail.fail(findUserByEmailAsyncResponse.cause());
      }
    });

    return findUserByEmail.future();
  }

  private Future<Boolean> checkExistingUser(Message<JsonObject> message) {
    Promise<Boolean> checkExistingUserPromise = Promise.promise();
    JsonObject user = message.body().getJsonObject(MessageProperties.MESSAGE_VALUE_USER);
    user.put("password", BCrypt.hashpw(user.getString("password"), BCrypt.gensalt()));
    JsonObject query = new JsonObject();
    query.put("email", user.getString("email"));

    mongoClient.findOne(COLLECTION_NAME, query, null, res -> {
      if (res.succeeded()) {
        if (res.result() != null) {
          checkExistingUserPromise.complete(true);
        } else {
          checkExistingUserPromise.complete(false);
        }
      } else {
        checkExistingUserPromise.fail(res.cause());
      }
    });
    return checkExistingUserPromise.future();
  }

  private void createUser(Message<JsonObject> message) {
    JsonObject user = message.body().getJsonObject(MessageProperties.MESSAGE_VALUE_USER);
    insertUser(user).setHandler(asyncResult -> {
      if (asyncResult.succeeded()) {
        JsonObject replyMessage = new JsonObject();
        replyMessage.put(MessageProperties.MESSAGE_RESPONSE_USER, Json.encode(asyncResult.result()));
        message.reply(replyMessage);
      } else {
        message.fail(1, "Insert Failed: " + asyncResult.cause().getMessage());
      }
    });
  }

  private Future<User> insertUser(JsonObject user) {

    Promise<User> insertUserPromise = Promise.promise();

    String now = nowAsString();

    user.put("created", now);
    user.put("modified", now);
    user.put("lastLogin", now);

    String userId = UUID.randomUUID().toString();
    user.put("_id", userId);
    user.put("token", JWTUtil.sign(userId, null));

    mongoClient.save(COLLECTION_NAME, user, asyncResult -> {

      if (asyncResult.succeeded()) {
        User resultUser = Json.decodeValue(user.toString(), User.class);
        insertUserPromise.complete(resultUser);
      } else {
        insertUserPromise.fail(asyncResult.cause());
      }

    });

    return insertUserPromise.future();
  }

  private String nowAsString() {
    return nowAsString(DateTimeFormatter.ofPattern(User.LOCAL_DATE_TIME_PATTERN));
  }

  private String nowAsString(DateTimeFormatter dateTimeFormatter) {
    return LocalDateTime.now().format(dateTimeFormatter);
  }

}
