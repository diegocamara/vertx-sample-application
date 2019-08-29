package com.example.users.verticle;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class MainVerticle extends AbstractVerticle {

  private Logger logger = LoggerFactory.getLogger(this.getClass());

  @Override
  public void start(Promise<Void> startPromise) throws Exception {

    getConfig().setHandler(config -> {

      DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(config.result());

      CompositeFuture.all(
        startDatabase().future(),
        deployVerticle(HttpVerticle.class, deploymentOptions).future(),
        deployVerticle(UserPersistenceVerticle.class, deploymentOptions).future())
        .setHandler(asyncResult -> {
          if (asyncResult.succeeded()) {
            logger.info("All deployments succeeded");
            startPromise.complete();
          } else {
            logger.error("Deployment failure: " + asyncResult.cause().getMessage());
            startPromise.fail(asyncResult.cause());
          }
        });

    });


  }

  private Promise<Void> deployVerticle(Class<? extends Verticle> clazz, DeploymentOptions deploymentOptions) {
    Promise<Void> deployPromise = Promise.promise();
    vertx.deployVerticle(clazz, deploymentOptions, asyncResult -> {
      if (asyncResult.succeeded()) {
        deployPromise.complete();
      } else {
        deployPromise.fail(asyncResult.cause());
      }
    });
    return deployPromise;
  }

  private Promise<Void> startDatabase() {
    return Promise.succeededPromise();
  }

  private Future<JsonObject> getConfig() {
    ConfigStoreOptions fileConfig = new ConfigStoreOptions()
      .setType("file")
      .setFormat("json")
      .setConfig(new JsonObject().put("path", "config/environment.json"));

    ConfigRetrieverOptions configRetrieverOptions = new ConfigRetrieverOptions().addStore(fileConfig);
    ConfigRetriever configRetriever = ConfigRetriever.create(vertx, configRetrieverOptions);

    return ConfigRetriever.getConfigAsFuture(configRetriever);
  }


}
