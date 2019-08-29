package com.example.users;

import com.example.users.infrastructure.EmbeddedMongoDB;
import com.example.users.verticle.MainVerticle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxTestContext;

public class DefaultTest {

  private Logger logger = LoggerFactory.getLogger(getClass());

  private static String bindIp = "localhost";
  private static int port = 27018;
  private static String dbName = "usersdb";
  private static EmbeddedMongoDB embeddedMongoDB = getEmbeddedMongoDB();
  WebClient webClient;
  MongoClient mongoClient;


  DefaultTest() {
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        logger.info("Running shutdown hook.");
        getEmbeddedMongoDB().stop();
      }
    });
  }

  @BeforeEach
  void beforeEach(Vertx vertx, VertxTestContext testContext) {
    webClient = WebClient.create(vertx);
    vertx.deployVerticle(new MainVerticle(), testContext.succeeding(id -> testContext.completeNow()));
    configClient(bindIp, port, dbName, vertx);
  }

  @AfterEach
  void afterEach(Vertx vertx, VertxTestContext testContext) {
    clearDB().setHandler(clearDBAsyncResult -> {
      if (clearDBAsyncResult.succeeded()) {
        testContext.completeNow();
      }
    });
  }

  private Future<Void> clearDB() {

    Promise<Void> clearDBPromise = Promise.promise();

    mongoClient.getCollections(asyncResult -> {

      if (asyncResult.succeeded()) {

        List<Future> futures = new LinkedList<>();
        asyncResult.result().forEach(collectionName -> futures.add(dropCollection(collectionName)));
        CompositeFuture.all(futures).setHandler(compositeFutureAsyncResult -> {
          if (compositeFutureAsyncResult.succeeded()) {
            clearDBPromise.complete();
          } else {
            clearDBPromise.fail(compositeFutureAsyncResult.cause());
          }
        });

      }

    });

    return clearDBPromise.future();

  }

  private Future<String> dropCollection(String collectionName) {

    Promise<String> dropCollectionPromise = Promise.promise();

    mongoClient.dropCollection(collectionName, dropCollectionAsyncResult -> {
      if (dropCollectionAsyncResult.succeeded()) {
        dropCollectionPromise.complete(collectionName);
      } else {
        dropCollectionPromise.fail(collectionName);
      }
    });

    return dropCollectionPromise.future();

  }

  public static EmbeddedMongoDB getEmbeddedMongoDB() {
    return embeddedMongoDB != null ? embeddedMongoDB : new EmbeddedMongoDB(bindIp, port);
  }

  private void configClient(String bindIp, int port, String dbName, Vertx vertx) {
    JsonObject config = new JsonObject();
    config.put("connection_string", "mongodb://" + bindIp + ":" + port);
    config.put("db_name", dbName);
    mongoClient = MongoClient.createNonShared(vertx, config);
  }

}
