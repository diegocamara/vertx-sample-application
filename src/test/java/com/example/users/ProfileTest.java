package com.example.users;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.UUID;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class ProfileTest extends DefaultTest {

  @Test
  void whenGetUserWithoutAuthorization_returnHttpStatus401(VertxTestContext vertxTestContext) {

    webClient.get(8080, "localhost", "/api/users").as(BodyCodec.jsonObject())
      .putHeader("Content-Type", "application/json")
      .send(vertxTestContext.succeeding(getUserAsyncResult -> vertxTestContext.verify(() -> {
        JsonObject errorResponse = getUserAsyncResult.body();
        Assertions.assertEquals(HttpResponseStatus.UNAUTHORIZED.code(), getUserAsyncResult.statusCode());
        Assertions.assertEquals(HttpResponseStatus.UNAUTHORIZED.toString(), errorResponse.getString("message"));
        vertxTestContext.completeNow();
      })));

  }

  @Test
  void whenGetUserWithoutBearerIdentifier_returnHttpStatus401(VertxTestContext vertxTestContext) {

    String userId = UUID.randomUUID().toString();
    String token = JWTUtil.sign(userId, null);

    webClient.get(8080, "localhost", "/api/users").as(BodyCodec.jsonObject())
      .putHeader("Content-Type", "application/json")
      .putHeader("Authorization", token)
      .send(vertxTestContext.succeeding(getUserAsyncResult -> vertxTestContext.verify(() -> {
        JsonObject errorResponse = getUserAsyncResult.body();
        Assertions.assertEquals(HttpResponseStatus.UNAUTHORIZED.code(), getUserAsyncResult.statusCode());
        Assertions.assertEquals(HttpResponseStatus.UNAUTHORIZED.toString(), errorResponse.getString("message"));
        vertxTestContext.completeNow();
      })));

  }

  @Test
  void whenGetUserWithValidToken_returnUser(VertxTestContext vertxTestContext) {

    String usersCollectionName = "users";
    String userId = UUID.randomUUID().toString();
    String token = "Bearer " + JWTUtil.sign(userId, null);


    JsonObject persistedUser = new JsonObject();
    persistedUser.put("_id", userId);
    persistedUser.put("name", "User");
    persistedUser.put("email", "user@mail.com");
    persistedUser.put("password", "123456s");
    persistedUser.put("phones", new JsonArray().add(new JsonObject().put("number", "123456789").put("ddd", "81")));

    mongoClient.save(usersCollectionName, persistedUser, asyncResult -> {
      if (asyncResult.succeeded()) {

        webClient.get(8080, "localhost", "/api/users").as(BodyCodec.jsonObject())
          .putHeader("Content-Type", "application/json")
          .putHeader("Authorization", token)
          .send(vertxTestContext.succeeding(getUserAsyncResult -> vertxTestContext.verify(() -> {
            JsonObject resultUser = getUserAsyncResult.body();
            Assertions.assertEquals(HttpResponseStatus.OK.code(), getUserAsyncResult.statusCode());
            Assertions.assertNotNull(resultUser.getString("name"));
            Assertions.assertNotNull(resultUser.getString("email"));
            Assertions.assertNotNull(resultUser.getJsonArray("phones"));
            vertxTestContext.completeNow();
          })));

      }
    });


  }

}
