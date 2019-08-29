package com.example.users.verticle;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.users.Actions;
import com.example.users.Events;
import com.example.users.JWTUtil;
import com.example.users.MessageProperties;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class HttpVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    Json.mapper.registerModule(new JavaTimeModule());
    Json.prettyMapper.registerModule(new JavaTimeModule());

    Router baseRouter = Router.router(vertx);

    baseRouter.route("/").handler(routingContext -> {
      HttpServerResponse response = routingContext.response();
      response.putHeader("content-type", "text/plain");
      response.end("Super vert.x application!");
    });

    Router usersRouter = Router.router(vertx);

    usersRouter.route().handler(BodyHandler.create());

    usersRouter.route("/users").handler(this::jwtHandler);
    usersRouter.get("/users").handler(this::findUserById);
    usersRouter.post("/users/register").handler(this::register);
    usersRouter.post("/login").handler(this::login);

    baseRouter.mountSubRouter("/api", usersRouter);

    vertx.createHttpServer().requestHandler(baseRouter).listen(8080, http -> {
      if (http.succeeded()) {
        startPromise.complete();
        System.out.println("HTTP server started on port 8080");
      } else {
        startPromise.fail(http.cause());
      }
    });
  }

  private void unauthorizedResponse(RoutingContext routingContext) {
    routingContext.response()
      .setStatusCode(HttpResponseStatus.UNAUTHORIZED.code())
      .end(errorMessage(HttpResponseStatus.UNAUTHORIZED.toString()));
  }

  private void jwtHandler(RoutingContext routingContext) {

    String authorization = routingContext.request().headers().get("Authorization");

    if (authorization == null || !authorization.contains("Bearer")) {
      unauthorizedResponse(routingContext);
    } else {

      authorization = authorization.replace("Bearer ", "");

      try {

        DecodedJWT decodedJWT = JWTUtil.verify(authorization);

        routingContext.put("userId", decodedJWT.getSubject().replaceAll("\"", ""));
        routingContext.next();

      } catch (JWTVerificationException ex) {

        unauthorizedResponse(routingContext);

      }

    }

  }

  private void register(RoutingContext routingContext) {

    JsonObject message = new JsonObject();
    message.put(MessageProperties.MESSAGE_ACTION, Actions.REGISTER);
    message.put(MessageProperties.MESSAGE_VALUE_USER, routingContext.getBodyAsJson());

    EventBus eventBus = vertx.eventBus();

    eventBus.request(Events.USER_EVENTS, message, messageAsyncResult -> {

      if (messageAsyncResult.succeeded()) {
        // User resultUser = Json.decodeValue(((JsonObject) asyncResult.result().body()).getString(MessageProperties.MESSAGE_RESPONSE_USER), User.class);
        routingContext.response()
          .setStatusCode(HttpResponseStatus.CREATED.code())
          .putHeader("Content-Type", "application/json; charset=utf-8")
          .end(((JsonObject) messageAsyncResult.result().body()).getString(MessageProperties.MESSAGE_RESPONSE_USER));
      } else {
        replyExceptionHandler(routingContext, messageAsyncResult);
      }

    });

  }

  private void findUserById(RoutingContext routingContext) {
    String userId = routingContext.get("userId");

    JsonObject message = new JsonObject();
    message.put(MessageProperties.MESSAGE_ACTION, Actions.LOAD_USER);
    message.put(MessageProperties.MESSAGE_VALUE_USER_ID, userId);

    EventBus eventBus = vertx.eventBus();

    eventBus.request(Events.USER_EVENTS, message, messageAsyncResult -> {

      if (messageAsyncResult.succeeded()) {
        JsonObject resultUser = ((JsonObject) messageAsyncResult.result().body()).getJsonObject(MessageProperties.MESSAGE_RESPONSE_FIND_USER);
        routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end(resultUser.toString());
      } else {
        replyExceptionHandler(routingContext, messageAsyncResult);
      }

    });

  }

  private void replyExceptionHandler(RoutingContext routingContext, AsyncResult<Message<Object>> messageAsyncResult) {
    if (messageAsyncResult.cause() instanceof ReplyException) {
      ReplyException replyException = (ReplyException) messageAsyncResult.cause();
      routingContext.response().setStatusCode(replyException.failureCode()).end(errorMessage(replyException));
    }
  }

  private void login(RoutingContext routingContext) {

    JsonObject message = new JsonObject();
    message.put(MessageProperties.MESSAGE_ACTION, Actions.LOGIN);
    message.put(MessageProperties.MESSAGE_VALUE_LOGIN, routingContext.getBodyAsJson());

    EventBus eventBus = vertx.eventBus();

    eventBus.request(Events.USER_EVENTS, message, messageAsyncResult -> {

      if (messageAsyncResult.succeeded()) {
        JsonObject user = ((JsonObject) messageAsyncResult.result().body()).getJsonObject(MessageProperties.MESSAGE_RESPONSE_LOGIN);
        routingContext.response()
          .setStatusCode(HttpResponseStatus.OK.code())
          .end(user.toString());
      } else {
        replyExceptionHandler(routingContext, messageAsyncResult);
      }
    });

  }

  private String errorMessage(ReplyException replyException) {
    return new JsonObject().put("message", replyException.getMessage()).toString();
  }

  private String errorMessage(String message) {
    return new JsonObject().put("message", message).toString();
  }

}
