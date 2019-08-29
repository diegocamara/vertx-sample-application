package com.example.users;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class RegisterTest extends DefaultTest {

  private String USERS_COLLECTION_NAME = "users";

  @Test
  void verticle_deployed(Vertx vertx, VertxTestContext testContext) throws Throwable {
    testContext.completeNow();
  }

  @Test
  void givenAValidUser_shouldReturnACreatedUser_and_httpStatusCode_201(Vertx vertx, VertxTestContext vertxTestContext) {

    JsonObject jsonObject = new JsonObject();
    jsonObject.put("name", "User");
    jsonObject.put("email", "user@mail.com");
    jsonObject.put("password", "123456s");
    jsonObject.put("phones", new JsonArray().add(new JsonObject().put("number", "123456789").put("ddd", "81")));

    webClient.post(8080, "localhost", "/api/users/register").as(BodyCodec.jsonObject())
      .putHeader("Content-Type", "application/json")
      .putHeader("X-Requested-With", "XMLHttpRequest")
      .sendJsonObject(jsonObject, vertxTestContext.succeeding(response -> {
        vertxTestContext.verify(() -> {
            Assertions.assertEquals(HttpResponseStatus.CREATED.code(), response.statusCode());
            Assertions.assertNotNull(response.body().getString("created"));
            Assertions.assertNotNull(response.body().getString("modified"));
            Assertions.assertNotNull(response.body().getString("lastLogin"));
            Assertions.assertNotNull(response.body().getString("token"));
          }
        );
        vertxTestContext.completeNow();
      }));

  }

  @Test
  void givenAExistingUser_shouldReturnAlreadyExistsMessageObject_and_httpStatusCode_409(Vertx vertx, VertxTestContext vertxTestContext) {

    JsonObject jsonObject = new JsonObject();
    jsonObject.put("name", "User");
    jsonObject.put("email", "user@mail.com");
    jsonObject.put("password", "123456s");
    jsonObject.put("phones", new JsonArray().add(new JsonObject().put("number", "123456789").put("ddd", "81")));

    mongoClient.save(USERS_COLLECTION_NAME, jsonObject, asyncResult -> {
      if (asyncResult.succeeded()) {

        webClient.post(8080, "localhost", "/api/users/register").as(BodyCodec.jsonObject())
          .putHeader("Content-Type", "application/json")
          .putHeader("X-Requested-With", "XMLHttpRequest")
          .sendJsonObject(jsonObject, vertxTestContext.succeeding(response -> {
            vertxTestContext.verify(() -> {
                Assertions.assertEquals(HttpResponseStatus.CONFLICT.code(), response.statusCode());
                Assertions.assertEquals(ConstantsMessage.EMAIL_ALREADY_EXISTS, response.body().getString("message"));
              }
            );
            vertxTestContext.completeNow();
          }));

      }
    });

  }

}
