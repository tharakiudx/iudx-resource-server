package iudx.resource.server.callback;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.types.EventBusService;
import io.vertx.serviceproxy.ServiceBinder;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public class CallbackVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(CallbackVerticle.class);
  private Vertx vertx;
  private ServiceDiscovery discovery;
  private Record record;
  private ClusterManager mgr;
  private VertxOptions options;
  private RabbitMQOptions config;
  private RabbitMQClient client;
  private Properties properties;
  private InputStream inputstream;
  private String dataBrokerIP;
  private int dataBrokerPort;
  private int dataBrokerManagementPort;
  private String dataBrokerVhost;
  private String dataBrokerUserName;
  private String dataBrokerPassword;
  private int connectionTimeout;
  private int requestedHeartbeat;
  private int handshakeTimeout;
  private int requestedChannelMax;
  private int networkRecoveryInterval;
  private CallbackService callback;
  private WebClient webClient;
  private WebClientOptions webConfig;
  /* Database Properties */
  private String databaseIP;
  private int databasePort;
  private String databaseName;
  private String databaseUserName;
  private String databasePassword;
  private int poolSize;

  /**
   * This method is used to start the Verticle. It deploys a verticle in a cluster.
   */
  @Override
  public void start() throws Exception {

    /* Create a reference to HazelcastClusterManager. */

    mgr = new HazelcastClusterManager();
    options = new VertxOptions().setClusterManager(mgr);

    /* Create or Join a Vert.x Cluster. */

    Vertx.clusteredVertx(options, res -> {
      if (res.succeeded()) {
        vertx = res.result();

        /* Read the configuration and set the rabbitMQ server properties. */
        properties = new Properties();
        inputstream = null;

        try  {

          inputstream = new FileInputStream("config.properties");
          properties.load(inputstream);

          dataBrokerIP = properties.getProperty("dataBrokerIP");
          dataBrokerPort = Integer.parseInt(properties.getProperty("dataBrokerPort"));
          dataBrokerManagementPort =
                  Integer.parseInt(properties.getProperty("dataBrokerManagementPort"));
          dataBrokerVhost = properties.getProperty("dataBrokerVhost");
          dataBrokerUserName = properties.getProperty("dataBrokerUserName");
          dataBrokerPassword = properties.getProperty("dataBrokerPassword");
          connectionTimeout = Integer.parseInt(properties.getProperty("connectionTimeout"));
          requestedHeartbeat = Integer.parseInt(properties.getProperty("requestedHeartbeat"));
          handshakeTimeout = Integer.parseInt(properties.getProperty("handshakeTimeout"));
          requestedChannelMax = Integer.parseInt(properties.getProperty("requestedChannelMax"));
          networkRecoveryInterval = Integer.parseInt(properties.getProperty("networkRecoveryInterval"));
          
		  databaseIP = properties.getProperty("databaseIP");
		  databasePort = Integer.parseInt(properties.getProperty("databasePort"));
		  databaseName = properties.getProperty("databaseName");
		  databaseUserName = properties.getProperty("databaseUserName");
		  databasePassword = properties.getProperty("databasePassword");
		  poolSize = Integer.parseInt(properties.getProperty("poolSize"));

        } catch (Exception ex) {

          logger.info(ex.toString());

        }

        /* Configure the RabbitMQ Data Broker client with input from config files. */

        config = new RabbitMQOptions();
        config.setUser(dataBrokerUserName);
        config.setPassword(dataBrokerPassword);
        config.setHost(dataBrokerIP);
        config.setPort(dataBrokerPort);
        config.setVirtualHost(dataBrokerVhost);
        config.setConnectionTimeout(connectionTimeout);
        config.setRequestedHeartbeat(requestedHeartbeat);
        config.setHandshakeTimeout(handshakeTimeout);
        config.setRequestedChannelMax(requestedChannelMax);
        config.setNetworkRecoveryInterval(networkRecoveryInterval);
        config.setAutomaticRecoveryEnabled(true);
        
        webConfig = new WebClientOptions();
        webConfig.setKeepAlive(true);
        webConfig.setConnectTimeout(86400000);
        webConfig.setDefaultHost(dataBrokerIP);
        webConfig.setDefaultPort(dataBrokerManagementPort);
        webConfig.setKeepAliveTimeout(86400000);

        /* Create a RabbitMQ Client with the configuration and vertx cluster instance. */

        client = RabbitMQClient.create(vertx, config);
        
        /* Create a Vertx Web Client with the configuration and vertx cluster instance. */

        webClient = WebClient.create(vertx, webConfig);

        /* Create a Json Object for properties */

        JsonObject propObj = new JsonObject();

        propObj.put("userName", dataBrokerUserName);
        propObj.put("password", dataBrokerPassword);
        propObj.put("vHost", dataBrokerVhost);
        propObj.put("dataBrokerIP", dataBrokerIP);
        propObj.put("dataBrokerPort", dataBrokerPort);
        propObj.put("dataBrokerPort", dataBrokerPort);
        propObj.put("databaseIP", databaseIP);
        propObj.put("databasePort", databasePort);
        propObj.put("databaseName", databaseName);
        propObj.put("databaseUserName", databaseUserName);
        propObj.put("databasePassword", databasePassword);
        propObj.put("databasePoolSize", poolSize);
  
        /* Call the callback constructor with the RabbitMQ client. */
        callback = new CallbackServiceImpl(client, webClient, propObj, vertx);
        
        /* Publish the Callback service with the Event Bus against an address. */

        new ServiceBinder(vertx).setAddress("iudx.rs.callback.service")
            .register(CallbackService.class, callback);
        
        /* Get a handler for the Service Discovery interface and publish a service record. */

        discovery = ServiceDiscovery.create(vertx);
        record = EventBusService.createRecord("iudx.rs.callback.service", // The service name
            "iudx.rs.callback.service", // the service address,
            CallbackService.class // the service interface
        );
        
        discovery.publish(record, publishRecordHandler -> {
            if (publishRecordHandler.succeeded()) {
              Record publishedRecord = publishRecordHandler.result();
              logger.info("Publication succeeded " + publishedRecord.toJson());
            } else {
              logger.info("Publication failed " + publishRecordHandler.result());
            }
          });
      }
    });
    logger.info("Callback Verticle started");
  }
}
