package iudx.resource.server.metering;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.serviceproxy.ServiceBinder;
import io.vertx.sqlclient.PoolOptions;
import iudx.resource.server.database.postgres.PostgresService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static iudx.resource.server.common.Constants.PG_SERVICE_ADDRESS;

public class MeteringVerticle extends AbstractVerticle {

  private static final String METERING_SERVICE_ADDRESS = "iudx.rs.metering.service";
  private static final Logger LOGGER = LogManager.getLogger(MeteringVerticle.class);
  PgConnectOptions connectOptions;
  PoolOptions poolOptions;
  PgPool pool;
  private String databaseIP;
  private int databasePort;
  private String databaseName;
  private String databaseTableName;
  private String databaseUserName;
  private String databasePassword;
  private int poolSize;
  private PgConnectOptions config;
  private ServiceBinder binder;
  private MessageConsumer<JsonObject> consumer;
  private MeteringService metering;
  private PostgresService postgresService;

  @Override
  public void start() throws Exception {
    binder = new ServiceBinder(vertx);
    postgresService = PostgresService.createProxy(vertx, PG_SERVICE_ADDRESS);

    metering = new MeteringServiceImpl(vertx, postgresService);
    consumer =
        binder.setAddress(METERING_SERVICE_ADDRESS).register(MeteringService.class, metering);
    LOGGER.info("Metering Verticle Started");
  }

  @Override
  public void stop() {
    binder.unregister(consumer);
  }
}
