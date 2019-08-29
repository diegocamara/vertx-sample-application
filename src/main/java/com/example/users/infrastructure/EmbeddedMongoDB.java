package com.example.users.infrastructure;

import java.io.IOException;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class EmbeddedMongoDB {

  private Logger logger = LoggerFactory.getLogger(getClass());

  private MongodExecutable mongodExecutable;
  private MongodProcess mongodProcess;

  public EmbeddedMongoDB(String bindIp, int port) {
    try {
      MongodStarter mongodStarter = MongodStarter.getDefaultInstance();
      IMongodConfig mongodConfig = new MongodConfigBuilder()
        .version(Version.Main.PRODUCTION)
        .net(new Net(bindIp, port, Network.localhostIsIPv6()))
        .build();
      mongodExecutable = mongodStarter.prepare(mongodConfig);
      mongodProcess = mongodExecutable.start();
    } catch (IOException ex) {
      logger.error("Database initialization failed: {}", ex.getCause());
    }
  }

  public void stop() {
    mongodProcess.stop();
    mongodExecutable.stop();
  }

}
