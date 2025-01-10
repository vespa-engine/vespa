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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

// class name must match plugin name
@LogstashPlugin(name = "vespa_feed")
public class VespaFeed implements Output {
    private static final Logger logger = LogManager.getLogger(VespaFeed.class);

    public static final PluginConfigSpec<URI> VESPA_URL =
            PluginConfigSpec.uriSetting("vespa_url", "http://localhost:8080");
    public static final PluginConfigSpec<String> NAMESPACE =
            PluginConfigSpec.requiredStringSetting("namespace");
    // if namespace is set to %{field_name} or %{[field_name]}, it's dynamic
    // if remove_namespace is true, the namespace is removed from the document
    public static final PluginConfigSpec<Boolean> REMOVE_NAMESPACE =
            PluginConfigSpec.booleanSetting("remove_namespace", false);
    public static final PluginConfigSpec<String> DOCUMENT_TYPE =
            PluginConfigSpec.requiredStringSetting("document_type");
    // if remove_document_type is true, the document type is removed from the document (assuming it's dynamic)
    public static final PluginConfigSpec<Boolean> REMOVE_DOCUMENT_TYPE =
            PluginConfigSpec.booleanSetting("remove_document_type", false);
    // field from the event to use as doc ID
    public static final PluginConfigSpec<String> ID_FIELD =
            PluginConfigSpec.stringSetting("id_field", "id");
    // if remove_id is true, the id field is removed from the document
    public static final PluginConfigSpec<Boolean> REMOVE_ID =
            PluginConfigSpec.booleanSetting("remove_id", false);

    // client certificate and key
    public static final PluginConfigSpec<String> CLIENT_CERT =
            PluginConfigSpec.stringSetting("client_cert", null);
    public static final PluginConfigSpec<String> CLIENT_KEY =
            PluginConfigSpec.stringSetting("client_key", null);

    // put, update or remove
    public static final PluginConfigSpec<String> OPERATION =
            PluginConfigSpec.stringSetting("operation", "put");
    // if remove_operation is true, the operation field is removed from the document (assuming it's dynamic)
    public static final PluginConfigSpec<Boolean> REMOVE_OPERATION =
            PluginConfigSpec.booleanSetting("remove_operation", false);
    // whether to add create=true to the put/update request
    public static final PluginConfigSpec<Boolean> CREATE =
            PluginConfigSpec.booleanSetting("create", false);

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
    private final String namespace;
    private final boolean dynamicNamespace;
    private final boolean removeNamespace;
    private final String documentType;
    private final boolean dynamicDocumentType;
    private final boolean removeDocumentType;
    private final String operation;
    private final boolean dynamicOperation;
    private final boolean create;
    private final String idField;
    private final boolean removeId; 
    private final long operationTimeout;
    private volatile boolean stopped = false;
    ObjectMapper objectMapper;
    private final boolean removeOperation;
    private DeadLetterQueueWriter dlqWriter;


    public VespaFeed(final String id, final Configuration config, final Context context) {
        this.id = id;
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

        // if the namespace matches %{field_name} or %{[field_name]}, it's dynamic
        DynamicOption configOption = new DynamicOption(config.get(NAMESPACE));
        dynamicNamespace = configOption.isDynamic();
        namespace = configOption.getParsedConfigValue();
        removeNamespace = config.get(REMOVE_NAMESPACE);
        // same with document type
        configOption = new DynamicOption(config.get(DOCUMENT_TYPE));
        dynamicDocumentType = configOption.isDynamic();
        documentType = configOption.getParsedConfigValue();
        removeDocumentType = config.get(REMOVE_DOCUMENT_TYPE);
        // and operation
        configOption = new DynamicOption(config.get(OPERATION));
        dynamicOperation = configOption.isDynamic();
        operation = configOption.getParsedConfigValue();
        create = config.get(CREATE);
        validateOperationAndCreate();

        operationTimeout = config.get(OPERATION_TIMEOUT);

        idField = config.get(ID_FIELD);
        removeId = config.get(REMOVE_ID);
        this.removeOperation = config.get(REMOVE_OPERATION);
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

        // set client certificate and key if they are provided
        builder = addCertAndKeyToBuilder(config, builder);

        // now we should have the client
        client = builder.build();

        // for JSON serialization
        objectMapper = ObjectMappers.JSON_MAPPER;
    }

    // Constructor for testing
    protected VespaFeed(String id, Configuration config, Context context, FeedClient testClient) {
        this(id, config, context);
        if (testClient != null) {
            this.client = testClient;
        }
    }

    public void validateOperationAndCreate() {
        if (!dynamicOperation) {
            if (!operation.equals("put") && !operation.equals("update") && !operation.equals("remove")) {
                throw new IllegalArgumentException("Operation must be put, update or remove");
            }
            if (operation.equals("remove") && create) {
                throw new IllegalArgumentException("Operation remove cannot have create=true");
            }
        }
    }

    protected static FeedClientBuilder addCertAndKeyToBuilder(Configuration config, FeedClientBuilder builder) {
        String clientCert = config.get(CLIENT_CERT);
        Path clientCertPath = null;
        if (clientCert != null) {
            clientCertPath = Paths.get(clientCert);
        }

        String clientKey = config.get(CLIENT_KEY);
        Path clientKeyPath = null;
        if (clientKey != null) {
            clientKeyPath = Paths.get(clientKey);
        }

        if (clientCertPath != null && clientKeyPath != null) {
            builder.setCertificate(clientCertPath, clientKeyPath);
        } else {
            logger.warn("Client certificate and key not provided. Using insecure connection.");
        }

        return builder;
    }

    @Override
    public void output(final Collection<Event> events) {
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
        Object docIdObj = event.getField(idField);
        if (docIdObj == null) {
            docIdStr = UUID.randomUUID().toString();
        } else {
            docIdStr = docIdObj.toString();
            // Remove the ID field if configured to do so
            if (removeId) {
                event.remove(idField);
            }
        }

        // if the namespace is dynamic, we need to resolve it
        String namespace = this.namespace;
        if (dynamicNamespace) {
            namespace = getDynamicField(event, this.namespace);
            if (removeNamespace) {
                event.remove(namespace);
            }
        }

        // similar logic for the document type
        String documentType = this.documentType;
        if (dynamicDocumentType) {
            documentType = getDynamicField(event, this.documentType);
            if (removeDocumentType) {
                event.remove(documentType);
            }
        }

        // and the operation
        String operation = this.operation;
        if (dynamicOperation) {
            operation = getDynamicField(event, this.operation);
            if (!operation.equals("put") && !operation.equals("update") && !operation.equals("remove")) {
                String errorMessage = String.format("Invalid operation (must be put, update or remove): {}", operation);
                logger.error(errorMessage);
                writeToDlq(event, errorMessage);
                return null;
            }
            if (removeOperation) {
                event.remove(this.operation);
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
        doc.put("fields", event.getData());  // Use the modified eventData here

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
                .timeout(Duration.ofSeconds(operationTimeout));

        if (create) {
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
        stopped = true;
        client.close();
    }

    @Override
    public void awaitStop() throws InterruptedException {
        // nothing to do here
    }

    @Override
    public Collection<PluginConfigSpec<?>> configSchema() {
        return List.of(VESPA_URL, CLIENT_CERT, CLIENT_KEY, OPERATION, CREATE,
                NAMESPACE, REMOVE_NAMESPACE, DOCUMENT_TYPE, REMOVE_DOCUMENT_TYPE, ID_FIELD, REMOVE_ID, REMOVE_OPERATION,
                MAX_CONNECTIONS, MAX_STREAMS, MAX_RETRIES, OPERATION_TIMEOUT, GRACE_PERIOD, DOOM_PERIOD,
                ENABLE_DLQ, DLQ_PATH, MAX_QUEUE_SIZE, MAX_SEGMENT_SIZE, FLUSH_INTERVAL);
    }

    @Override
    public String getId() {
        return id;
    }

    public boolean isDynamicNamespace() {
        return dynamicNamespace;
    }

    public String getNamespace() {
        return namespace;
    }

    public boolean isDynamicDocumentType() {
        return dynamicDocumentType;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getOperation() {
        return operation;
    }

    public boolean isCreate() {
        return create;
    }

    public boolean isDynamicOperation() {
        return dynamicOperation;
    }

    public long getOperationTimeout() {
        return operationTimeout;
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
