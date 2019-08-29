package com.example.users;

import com.example.users.entity.User;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class LoginTest extends DefaultTest {

  private String USERS_COLLECTION_NAME = "users";

  @Test
  void givenAValidLoginPayload_shouldReturnUser(Vertx vertx, VertxTestContext vertxTestContext) {

    JsonObject persistedUser = new JsonObject();
    persistedUser.put("_id", UUID.randomUUID().toString());
    persistedUser.put("name", "User");
    persistedUser.put("email", "user@mail.com");
    String userPassword = "123456s";
    String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern(User.LOCAL_DATE_TIME_PATTERN));
    persistedUser.put("created", now);
    persistedUser.put("modified", now);
    persistedUser.put("lastLogin", now);
    persistedUser.put("token", JWTUtil.sign(new JsonObject().put("id", persistedUser.getString("_id")), null));
    persistedUser.put("password", BCrypt.hashpw(userPassword, BCrypt.gensalt()));
    persistedUser.put("phones", new JsonArray().add(new JsonObject().put("number", "123456789").put("ddd", "81")));

    JsonObject login = new JsonObject()
      .put("email", persistedUser.getString("email"))
      .put("password", userPassword);

    mongoClient.save(USERS_COLLECTION_NAME, persistedUser, asyncResult -> {
      if (asyncResult.succeeded()) {

        webClient.post(8080, "localhost", "/api/login").as(BodyCodec.jsonObject())
          .putHeader("Content-Type", "application/json")
          .putHeader("X-Requested-With", "XMLHttpRequest")
          .sendJsonObject(login, vertxTestContext.succeeding(response -> {
            vertxTestContext.verify(() -> {

                Assertions.assertEquals(HttpResponseStatus.OK.code(), response.statusCode());
                JsonObject userResponse = response.body();
                Assertions.assertNotNull(userResponse.getString("created"));
                Assertions.assertNotNull(userResponse.getString("modified"));
                Assertions.assertNotNull(userResponse.getString("lastLogin"));
                Assertions.assertNotNull(userResponse.getString("token"));
              }
            );
            vertxTestContext.completeNow();
          }));

      }
    });
  }

  @Test
  void givenAInvalidLoginPayload_shouldReturnCode401(Vertx vertx, VertxTestContext vertxTestContext) {

    JsonObject login = new JsonObject()
      .put("email", "user@mail.com")
      .put("password", "123");

    webClient.post(8080, "localhost", "/api/login").as(BodyCodec.jsonObject())
      .putHeader("Content-Type", "application/json")
      .putHeader("X-Requested-With", "XMLHttpRequest")
      .sendJsonObject(login, vertxTestContext.succeeding(response -> {
        vertxTestContext.verify(() -> {
            Assertions.assertEquals(HttpResponseStatus.UNAUTHORIZED.code(), response.statusCode());
            JsonObject errorResponse = response.body();
            Assertions.assertEquals(ConstantsMessage.USER_OR_EMAIL_DO_NOT_EXISTS, errorResponse.getString("message"));
          }
        );
        vertxTestContext.completeNow();
      }));

  }

  @Test
  void givenAValidEmailAndInvalidPassword_shouldReturnCode401(Vertx vertx, VertxTestContext vertxTestContext) {

    JsonObject persistedUser = new JsonObject();
    persistedUser.put("_id", UUID.randomUUID().toString());
    persistedUser.put("name", "User");
    persistedUser.put("email", "user@mail.com");
    String userPassword = "123456s";
    String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern(User.LOCAL_DATE_TIME_PATTERN));
    persistedUser.put("created", now);
    persistedUser.put("modified", now);
    persistedUser.put("lastLogin", now);
    persistedUser.put("token", JWTUtil.sign(new JsonObject().put("id", persistedUser.getString("_id")), null));
    persistedUser.put("password", BCrypt.hashpw(userPassword, BCrypt.gensalt()));
    persistedUser.put("phones", new JsonArray().add(new JsonObject().put("number", "123456789").put("ddd", "81")));

    JsonObject login = new JsonObject()
      .put("email", persistedUser.getString("email"))
      .put("password", "123");

    mongoClient.save(USERS_COLLECTION_NAME, persistedUser, asyncResult -> {
      if (asyncResult.succeeded()) {

        webClient.post(8080, "localhost", "/api/login").as(BodyCodec.jsonObject())
          .putHeader("Content-Type", "application/json")
          .putHeader("X-Requested-With", "XMLHttpRequest")
          .sendJsonObject(login, vertxTestContext.succeeding(response -> {
            vertxTestContext.verify(() -> {
                Assertions.assertEquals(HttpResponseStatus.UNAUTHORIZED.code(), response.statusCode());
                JsonObject errorResponse = response.body();
                Assertions.assertEquals(ConstantsMessage.USER_OR_EMAIL_DO_NOT_EXISTS, errorResponse.getString("message"));
              }
            );
            vertxTestContext.completeNow();
          }));

      }
    });
  }

}
