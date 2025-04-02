// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package org.logstashplugins;

import ai.vespa.feed.client.*;
import ai.vespa.feed.client.impl.GracePeriodCircuitBreaker;
import co.elastic.logstash.api.Configuration;
import co.elastic.logstash.api.Context;
import co.elastic.logstash.api.Event;
import co.elastic.logstash.api.LogstashPlugin;
import co.elastic.logstash.api.Output;
import co.elastic.logstash.api.PluginConfigSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.logstash.ObjectMappers;
import org.logstash.common.io.DeadLetterQueueWriter;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

// class name must match plugin name
@LogstashPlugin(name = "vespa_feed")
public class VespaFeed implements Output {
    private static final Logger logger = LogManager.getLogger(VespaFeed.class);

    /***********************
     * Quick start mode settings
     ***********************/
    // quick start mode. This will not send documents to Vespa, but will generate an application package
    public static final PluginConfigSpec<Boolean> QUICK_START =
            PluginConfigSpec.booleanSetting("quick_start", false);

    // Other options in this section are ignored/irrelevant if quick_start is false

    // save the generated application package in this directory
    public static final PluginConfigSpec<String> APPLICATION_PACKAGE_DIR =
            PluginConfigSpec.stringSetting("application_package_dir", System.getProperty("java.io.tmpdir") + File.separator + "vespa_app");
    // should we deploy the application after generating it?
    public static final PluginConfigSpec<Boolean> DEPLOY_PACKAGE =
            PluginConfigSpec.booleanSetting("deploy_package", true);
    // when? What do we consider idle time in terms of empty pipeline batches?
    public static final PluginConfigSpec<Long> IDLE_BATCHES =
            PluginConfigSpec.numSetting("idle_batches", 10);
    // where to deploy on local installs. Defaults to the same host as the vespa_url, with port 19071
    public static final PluginConfigSpec<URI> CONFIG_SERVER =
            PluginConfigSpec.uriSetting("config_server", null);
    // whether to generate mTLS certificates
    // if we're using Vespa Cloud, the default is "true", otherwise "false"
    public static final PluginConfigSpec<Boolean> GENERATE_MTLS_CERTIFICATES =
            PluginConfigSpec.booleanSetting("generate_mtls_certificates", false);
    // custom type mappings file
    public static final PluginConfigSpec<String> TYPE_MAPPINGS_FILE =
    PluginConfigSpec.stringSetting("type_mappings_file", null);
    // custom type conflict resolution file
    public static final PluginConfigSpec<String> TYPE_CONFLICT_RESOLUTION_FILE =
            PluginConfigSpec.stringSetting("type_conflict_resolution_file", null);
    
    // Vespa Cloud deployment options
    public static final PluginConfigSpec<String> VESPA_CLOUD_TENANT = 
            PluginConfigSpec.stringSetting("vespa_cloud_tenant", null);
    public static final PluginConfigSpec<String> VESPA_CLOUD_APPLICATION = 
            PluginConfigSpec.stringSetting("vespa_cloud_application", null);
    public static final PluginConfigSpec<String> VESPA_CLOUD_INSTANCE = 
            PluginConfigSpec.stringSetting("vespa_cloud_instance", "default");
    
    /***********************
     * Vespa API settings
     ***********************/
    public static final PluginConfigSpec<URI> VESPA_URL =
            PluginConfigSpec.uriSetting("vespa_url", "http://localhost:8080");
    // if namespace is set to %{field_name} or %{[field_name]}, it's dynamic
    // irrelevant/ignored for if quick_start is true
    public static final PluginConfigSpec<String> NAMESPACE =
            PluginConfigSpec.stringSetting("namespace", null);
    // if remove_namespace is true, the namespace is removed from the document
    // ignored/irrelevant for if quick_start is true
    public static final PluginConfigSpec<Boolean> REMOVE_NAMESPACE =
            PluginConfigSpec.booleanSetting("remove_namespace", false);
    public static final PluginConfigSpec<String> DOCUMENT_TYPE =
            PluginConfigSpec.stringSetting("document_type", "doctype");
    // if remove_document_type is true, the document type is removed from the document (assuming it's dynamic)
    // ignored/irrelevant for if quick_start is true
    public static final PluginConfigSpec<Boolean> REMOVE_DOCUMENT_TYPE =
            PluginConfigSpec.booleanSetting("remove_document_type", false);
    // field from the event to use as doc ID
    // ignored/irrelevant for if quick_start is true
    public static final PluginConfigSpec<String> ID_FIELD =
            PluginConfigSpec.stringSetting("id_field", "id");
    // if remove_id is true, the id field is removed from the document
    // ignored/irrelevant for if quick_start is true
    public static final PluginConfigSpec<Boolean> REMOVE_ID =
            PluginConfigSpec.booleanSetting("remove_id", false);

    // client certificate and key
    // defaults to the application package dir under
    // security/clients.pem and data-plane-private-key.pem
    public static final PluginConfigSpec<String> CLIENT_CERT =
            PluginConfigSpec.stringSetting("client_cert", null);
    public static final PluginConfigSpec<String> CLIENT_KEY =
            PluginConfigSpec.stringSetting("client_key", null);
    
    // authentication token (for Vespa Cloud)
    // ignored/irrelevant for if quick_start is true
    public static final PluginConfigSpec<String> AUTH_TOKEN =
            PluginConfigSpec.stringSetting("auth_token", null);

    // put, update or remove
    // ignored/irrelevant for if quick_start is true
    public static final PluginConfigSpec<String> OPERATION =
            PluginConfigSpec.stringSetting("operation", "put");
    // if remove_operation is true, the operation field is removed from the document (assuming it's dynamic)
    // ignored/irrelevant for if quick_start is true
    public static final PluginConfigSpec<Boolean> REMOVE_OPERATION =
            PluginConfigSpec.booleanSetting("remove_operation", false);
    // whether to add create=true to the put/update request
    // ignored/irrelevant for if quick_start is true
    public static final PluginConfigSpec<Boolean> CREATE =
            PluginConfigSpec.booleanSetting("create", false);

    /***********************
     * Feed client settings
     (ignored/irrelevant for if quick_start is true)
     ***********************/
    // max HTTP/2 connections per endpoint. We only have 1
    public static final PluginConfigSpec<Long> MAX_CONNECTIONS =
            PluginConfigSpec.numSetting("max_connections", 1);
    // max streams for the async client. General wisdom is to prefer more streams than connections
    public static final PluginConfigSpec<Long> MAX_STREAMS =
            PluginConfigSpec.numSetting("max_streams", 128);
    // request timeout (seconds) for each write operation
    public static final PluginConfigSpec<Long> OPERATION_TIMEOUT =
            PluginConfigSpec.numSetting("operation_timeout", 180);
    // max retries for transient failures
    public static final PluginConfigSpec<Long> MAX_RETRIES =
            PluginConfigSpec.numSetting("max_retries", 10);
    // after this time (seconds), the circuit breaker will be half-open:
    // it will ping the endpoint to see if it's back,
    // then resume sending requests when it's back
    public static final PluginConfigSpec<Long> GRACE_PERIOD =
            PluginConfigSpec.numSetting("grace_period", 10);
    // this should close the client, but it looks like it doesn't shut down either Logstash or the plugin
    // when the connection can work again, Logstash seems to resume sending requests
    public static final PluginConfigSpec<Long> DOOM_PERIOD =
            PluginConfigSpec.numSetting("doom_period", 60);
    
    /***********
     * Dead Letter Queue settings
     (ignored/irrelevant for if quick_start is true)
     ***********/
    // enable dead letter queue. This overrides the global setting in logstash.yml
    public static final PluginConfigSpec<Boolean> ENABLE_DLQ =
            PluginConfigSpec.booleanSetting("enable_dlq", false);
    // path to the dead letter queue. Similarly, this overrides the global setting in logstash.yml
    public static final PluginConfigSpec<String> DLQ_PATH =
            PluginConfigSpec.stringSetting("dlq_path", "data" + File.separator + "dead_letter_queue");
    // max queue size
    public static final PluginConfigSpec<Long> MAX_QUEUE_SIZE =
            PluginConfigSpec.numSetting("max_queue_size", 1024 * 1024 * 1024);
    // max size of a single segment (file)
    public static final PluginConfigSpec<Long> MAX_SEGMENT_SIZE =
            PluginConfigSpec.numSetting("max_segment_size", 10 * 1024 * 1024);
    // flush interval in millis (i.e. when to write to disk if there are no more events to write for this while)
    public static final PluginConfigSpec<Long> FLUSH_INTERVAL =
            PluginConfigSpec.numSetting("flush_interval", 5000);
    
    private FeedClient client;
    private final String id;
    // this signals that the plugin is stopping
    private volatile boolean stopped = false;
    
    ObjectMapper objectMapper;
    private DeadLetterQueueWriter dlqWriter;
    private VespaQuickStarter quickStarter;
    private final QuickStartConfig quickStartConfig;
    private final FeedConfig feedConfig;

    public VespaFeed(final String id, final Configuration config, final Context context) {
        this.id = id;

        feedConfig = new FeedConfig(
            config.get(NAMESPACE),
            config.get(REMOVE_NAMESPACE),
            config.get(DOCUMENT_TYPE),
            config.get(REMOVE_DOCUMENT_TYPE),
            config.get(OPERATION),
            config.get(CREATE),
            config.get(ID_FIELD),
            config.get(REMOVE_ID),
            config.get(OPERATION_TIMEOUT),
            config.get(REMOVE_OPERATION),
            config.get(CLIENT_CERT),
            config.get(CLIENT_KEY),
            config.get(APPLICATION_PACKAGE_DIR)
        );

        // for JSON serialization
        objectMapper = ObjectMappers.JSON_MAPPER;
        
        if (config.get(QUICK_START)) {
            quickStartConfig = new QuickStartConfig(
                config.get(DEPLOY_PACKAGE),
                config.get(GENERATE_MTLS_CERTIFICATES),
                feedConfig.getClientCert(),
                feedConfig.getClientKey(),
                config.get(CONFIG_SERVER),
                config.get(VESPA_URL),
                feedConfig.getDocumentType(),
                feedConfig.isDynamicDocumentType(),
                config.get(IDLE_BATCHES).longValue(),
                config.get(APPLICATION_PACKAGE_DIR),
                config.get(TYPE_MAPPINGS_FILE),
                config.get(TYPE_CONFLICT_RESOLUTION_FILE),
                config.get(MAX_RETRIES),
                config.get(GRACE_PERIOD),
                config.get(VESPA_CLOUD_TENANT),
                config.get(VESPA_CLOUD_APPLICATION),
                config.get(VESPA_CLOUD_INSTANCE)
            );
            quickStarter = new VespaQuickStarter(quickStartConfig);
        } else {
            quickStartConfig = null;
            if (config.get(ENABLE_DLQ)) {
                try {
                    Path dlqPath = Paths.get(config.get(DLQ_PATH));
                    dlqWriter = DeadLetterQueueWriter.newBuilder(dlqPath,
                                    config.get(MAX_QUEUE_SIZE).longValue(),
                                    config.get(MAX_SEGMENT_SIZE).longValue(),
                                    Duration.ofMillis(config.get(FLUSH_INTERVAL).longValue()))
                                .build();
                } catch (IOException e) {
                    logger.error("Failed to create DLQ writer: ", e);
                    dlqWriter = null;
                }
            } else {
                dlqWriter = null;
            }

            try {
                FeedClientBuilder builder = FeedClientBuilder.create(config.get(VESPA_URL))
                        .setConnectionsPerEndpoint(config.get(MAX_CONNECTIONS).intValue())
                        .setMaxStreamPerConnection(config.get(MAX_STREAMS).intValue())
                        .setRetryStrategy(
                                new FeedClient.RetryStrategy() {
                                    @Override
                                    public boolean retry(FeedClient.OperationType type) {
                                        // retry all operations
                                        return true;
                                    }

                                    @Override
                                    public int retries() {
                                        return config.get(MAX_RETRIES).intValue();
                                    }
                                }
                        )
                        .setCircuitBreaker(
                                new GracePeriodCircuitBreaker(
                                        Duration.ofSeconds(config.get(GRACE_PERIOD)),
                                        Duration.ofSeconds(config.get(DOOM_PERIOD))
                                )
                        );

                // set client certificate and key (or auth token) if they are provided
                builder = addAuthOptionsToBuilder(config, builder, 
                            feedConfig.getClientCert(), feedConfig.getClientKey());

                // now we should have the client
                client = builder.build();
            } catch (Exception e) {
                String errorMessage = "Failed to create Vespa feed client: " + e.getMessage();
                logger.error(errorMessage, e);
                throw new IllegalArgumentException(errorMessage, e);
            }
        }
    }

    // Constructor for testing
    protected VespaFeed(String id, Configuration config, Context context, FeedClient testClient) {
        this(id, config, context);
        if (testClient != null) {
            this.client = testClient;
        }
    }

    protected static FeedClientBuilder addAuthOptionsToBuilder(Configuration config, FeedClientBuilder builder,
                                                String clientCert, String clientKey) {
        Path clientCertPath = null;
        Path clientKeyPath = null;
        
        if (clientCert != null && clientKey != null) {
            // Check if certificate files exist
            Path certPath = Paths.get(clientCert);
            Path keyPath = Paths.get(clientKey);
            
            if (Files.exists(certPath) && Files.exists(keyPath)) {
                clientCertPath = certPath;
                clientKeyPath = keyPath;
                logger.info("Using mTLS certificates for authentication - cert: {}, key: {}", clientCert, clientKey);
            } else {
                logger.warn("Certificate files not found, not using mTLS: cert: {} (exists: {}), key: {} (exists: {})", 
                    clientCert, Files.exists(certPath), clientKey, Files.exists(keyPath));
            }
        }

        if (clientCertPath != null && clientKeyPath != null) {
            builder.setCertificate(clientCertPath, clientKeyPath);
            return builder;
        }

        String authToken = config.get(AUTH_TOKEN);
        if (authToken != null) {
            logger.info("Using auth token for authentication");
            builder.addRequestHeader("Authorization", "Bearer " + authToken);
            return builder;
        }
        
        logger.warn("Client certificate + key combination not found. Auth token not provided, either. Using insecure connection.");
        return builder;
    }

    @Override
    public void output(final Collection<Event> events) {
        if (quickStarter != null) {
            quickStarter.run(events);
            return;
        }

        // track async requests (promises) and their corresponding events here
        Map<CompletableFuture<Result>, Event> promiseToEventMap = new HashMap<>();

        Iterator<Event> eventIterator = events.iterator();
        while (eventIterator.hasNext() && !stopped) {
            Event event = eventIterator.next();
            try {
                CompletableFuture<Result> promise = asyncFeed(event);
                if (promise != null) {
                    promiseToEventMap.put(promise, event);
                }
            } catch (JsonProcessingException | RuntimeException e) {
                // RuntimeException shouldn't really happen here, we use it in tests
                String errorMessage = String.format("Error processing event to generate async feed request: %s", e.getMessage());
                logger.error(errorMessage);
                writeToDlq(event, errorMessage);
            }
        }

        // check if we have any promises (we might have dropped some invalid events)
        if (promiseToEventMap.isEmpty()) {
            return;
        }

        // wait for all futures to complete
        try {
            FeedClient.await(new ArrayList<>(promiseToEventMap.keySet()));
        } catch (MultiFeedException e) {
            // now we need to figure out which events failed
            promiseToEventMap.forEach((promise, event) -> {
                if (promise.isCompletedExceptionally()) {
                    try {
                        promise.get(); // This will throw the exception
                    } catch (Exception ex) {
                        String errorMessage = String.format("Error while waiting for async operation to complete: %s", ex.getMessage());
                        logger.error(errorMessage);
                        writeToDlq(event, errorMessage);
                    }
                }
            });
        }
    }

    protected CompletableFuture<Result> asyncFeed(Event event) throws JsonProcessingException {
        // try to get the doc ID from the event, otherwise generate a UUID
        String docIdStr;
        Object docIdObj = event.getField(feedConfig.getIdField());
        if (docIdObj == null) {
            docIdStr = UUID.randomUUID().toString();
        } else {
            docIdStr = docIdObj.toString();
            // Remove the ID field if configured to do so
            if (feedConfig.isRemoveId()) {
                event.remove(feedConfig.getIdField());
            }
        }

        // if the namespace is dynamic, we need to resolve it
        String namespace = feedConfig.getNamespace();
        if (feedConfig.isDynamicNamespace()) {
            namespace = getDynamicField(event, feedConfig.getNamespace());
            if (feedConfig.isRemoveNamespace()) {
                event.remove(feedConfig.getNamespace());
            }
        }

        // similar logic for the document type
        String documentType = feedConfig.getDocumentType();
        if (feedConfig.isDynamicDocumentType()) {
            documentType = getDynamicField(event, feedConfig.getDocumentType());
            if (feedConfig.isRemoveDocumentType()) {
                event.remove(feedConfig.getDocumentType());
            }
        }

        // and the operation
        String operation = feedConfig.getOperation();
        if (feedConfig.isDynamicOperation()) {
            operation = getDynamicField(event, feedConfig.getOperation());
            if (!operation.equals("put") && !operation.equals("update") && !operation.equals("remove")) {
                String errorMessage = String.format("Invalid operation (must be put, update or remove): {}", operation);
                logger.error(errorMessage);
                writeToDlq(event, errorMessage);
                return null;
            }
            if (feedConfig.isRemoveOperation()) {
                event.remove(feedConfig.getOperation());
            }
        }
        // add create=true, if applicable
        OperationParameters operationParameters = addCreateIfApplicable(operation, docIdStr);

        logger.trace("Feeding document with ID: {} to namespace: {} and document type: {}",
                docIdStr, namespace, documentType);
        DocumentId docId = DocumentId.of(namespace,
                documentType, docIdStr);

        // create a document from the event data
        Map<String,Object> doc = new HashMap<>();
        doc.put("fields", event.getData());

        // create the request to feed the document
        if (operation.equals("put")) {
            return client.put(docId, toJson(doc), operationParameters);
        } else if (operation.equals("update")) {
            return client.update(docId, toJson(doc), operationParameters);
        } else {
            return client.remove(docId, operationParameters);
        }
    }

    /**
     * Add create=true to the operation parameters if the operation is put or update
     * @param operation
     * @param docId
     * @return The operation parameters with create=true if applicable
     */
    public OperationParameters addCreateIfApplicable(String operation, String docId) {
        OperationParameters operationParameters = OperationParameters.empty()
                .timeout(Duration.ofSeconds(feedConfig.getOperationTimeout()));

        if (feedConfig.isCreate()) {
            if (operation.equals("put") || operation.equals("update")) {
                return operationParameters.createIfNonExistent(true);
            } else {
                logger.warn("Operation remove cannot have create=true." +
                        " Ignoring create=true for docID: {}", docId);
            }
        }
        return operationParameters;
    }

    /**
     * We need to use the original event object to get the fieldName value.
     * For some reason, getting it from the eventData map of asyncFeed doesn't work
     *
     * @param event The original event object
     * @param fieldName The field name to get
     * @return The value of the field or the field name if it doesn't exist
     */
    protected String getDynamicField(Event event, String fieldName) {
        Object namespaceFieldValue = event.getField(fieldName);
        if (namespaceFieldValue != null) {
            return namespaceFieldValue.toString();
        } else {
            return fieldName;
        }
    }

    protected String toJson(Map<String, Object> eventData) throws JsonProcessingException {
        return objectMapper.writeValueAsString(eventData);
    }

    @Override
    public void stop() {
        // this will tell the main loop to stop... looping :)
        stopped = true;
       
        // close the client, if we're in standard mode
        if (client != null) {
            client.close();
        }

        if (quickStarter != null) {
            logger.info("Stopping VespaFeed plugin");
            
            // In quick start mode, deploy the application package if configured to do so
            if (quickStartConfig != null && quickStartConfig.isDeployPackage()) {
                quickStarter.deployer.deployApplicationPackage();
            }
        }
    }

    @Override
    public void awaitStop() throws InterruptedException {
        // do nothing
    }

    @Override
    public Collection<PluginConfigSpec<?>> configSchema() {
        return List.of(
            QUICK_START, DEPLOY_PACKAGE, CONFIG_SERVER, APPLICATION_PACKAGE_DIR, IDLE_BATCHES, TYPE_MAPPINGS_FILE,
            TYPE_CONFLICT_RESOLUTION_FILE, GENERATE_MTLS_CERTIFICATES, CLIENT_CERT, CLIENT_KEY, VESPA_URL,
            NAMESPACE, REMOVE_NAMESPACE, DOCUMENT_TYPE, REMOVE_DOCUMENT_TYPE, ID_FIELD, REMOVE_ID,
            AUTH_TOKEN, OPERATION, REMOVE_OPERATION, CREATE, MAX_CONNECTIONS, MAX_STREAMS,
            OPERATION_TIMEOUT, MAX_RETRIES, GRACE_PERIOD, DOOM_PERIOD, ENABLE_DLQ, DLQ_PATH,
            MAX_QUEUE_SIZE, MAX_SEGMENT_SIZE, FLUSH_INTERVAL, VESPA_CLOUD_TENANT, VESPA_CLOUD_APPLICATION,
            VESPA_CLOUD_INSTANCE
        );
    }

    @Override
    public String getId() {
        return id;
    }

    protected void writeToDlq(Event event, String errorMessage) {
        if (dlqWriter != null) {
            try {
                // because our event is co.elastic.logstash.api.Event,
                // we need to cast it to org.logstash.Event
                // to be able to use writeEntry
                org.logstash.Event castedEvent = (org.logstash.Event) event;
                dlqWriter.writeEntry(castedEvent, this.getName(), this.id, errorMessage);
            } catch (IOException e) {
                logger.error("Error writing to dead letter queue: ", e);
            }
        }
    }

    // for testing DLQ functionality
    protected void setDlqWriter(DeadLetterQueueWriter writer) {
        this.dlqWriter = writer;
    }
}
