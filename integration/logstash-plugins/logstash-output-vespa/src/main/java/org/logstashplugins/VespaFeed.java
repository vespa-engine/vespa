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
    public static final PluginConfigSpec<String> DOCUMENT_TYPE =
            PluginConfigSpec.requiredStringSetting("document_type");
    public static final PluginConfigSpec<String> ID_FIELD =
            PluginConfigSpec.stringSetting("id_field", "id");

    // client certificate and key
    public static final PluginConfigSpec<String> CLIENT_CERT =
            PluginConfigSpec.stringSetting("client_cert", null);
    public static final PluginConfigSpec<String> CLIENT_KEY =
            PluginConfigSpec.stringSetting("client_key", null);

    // put, update or remove
    public static final PluginConfigSpec<String> OPERATION =
            PluginConfigSpec.stringSetting("operation", "put");
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

    private final FeedClient client;
    private final String id;
    private final String namespace;
    private final boolean dynamicNamespace;
    private final String documentType;
    private final boolean dynamicDocumentType;
    private final String operation;
    private final boolean dynamicOperation;
    private final boolean create;
    private final String idField;
    private final long operationTimeout;
    private volatile boolean stopped = false;
    ObjectMapper objectMapper;


    public VespaFeed(final String id, final Configuration config, final Context context) {
        this.id = id;

        // if the namespace matches %{field_name} or %{[field_name]}, it's dynamic
        DynamicOption configOption = new DynamicOption(config.get(NAMESPACE));
        dynamicNamespace = configOption.isDynamic();
        namespace = configOption.getParsedConfigValue();

        // same with document type
        configOption = new DynamicOption(config.get(DOCUMENT_TYPE));
        dynamicDocumentType = configOption.isDynamic();
        documentType = configOption.getParsedConfigValue();

        // and operation
        configOption = new DynamicOption(config.get(OPERATION));
        dynamicOperation = configOption.isDynamic();
        operation = configOption.getParsedConfigValue();
        create = config.get(CREATE);
        validateOperationAndCreate();

        operationTimeout = config.get(OPERATION_TIMEOUT);

        idField = config.get(ID_FIELD);

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

    private void validateOperationAndCreate() {
        if (!dynamicOperation) {
            if (!operation.equals("put") && !operation.equals("update") && !operation.equals("remove")) {
                throw new IllegalArgumentException("Operation must be put, update or remove");
            }
            if (operation.equals("remove") && create) {
                throw new IllegalArgumentException("Operation remove cannot have create=true");
            }
        }
    }

    private FeedClientBuilder addCertAndKeyToBuilder(Configuration config, FeedClientBuilder builder) {
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
        // we put (async) indexing requests here
        List<CompletableFuture<Result>> promises = new ArrayList<>();

        Iterator<Event> eventIterator = events.iterator();
        while (eventIterator.hasNext() && !stopped) {
            try {
                CompletableFuture<Result> promise = asyncFeed(eventIterator.next());
                if (promise != null) {
                    promises.add(promise);
                }
            } catch (JsonProcessingException e) {
                logger.error("Error serializing event data into JSON: ", e);
            }
        }

        // check if we have any promises (we might have dropped some invalid events)
        if (promises.isEmpty()) {
            return;
        }

        // wait for all futures to complete
        try {
            FeedClient.await(promises);
        } catch (MultiFeedException e) {
            e.feedExceptions().forEach(
                    exception -> logger.error("Error while waiting for async operation to complete: ",
                            exception)
            );
        }

    }

    private CompletableFuture<Result> asyncFeed(Event event) throws JsonProcessingException {
        Map<String, Object> eventData = event.getData();

        // we put the doc ID here
        String docIdStr;

        // see if the event has an ID field (as configured)
        // if it does, use it as docIdStr. Otherwise, generate a UUID
        if (eventData.containsKey(idField)) {
            docIdStr = eventData.get(idField).toString();
        } else {
            docIdStr = UUID.randomUUID().toString();
        }

        // if the namespace is dynamic, we need to resolve it
        // the default (if we don't have such a field) is simply the name of the field
        String namespace = this.namespace;
        if (dynamicNamespace) {
            namespace = getDynamicField(event, this.namespace);
        }

        // similar logic for the document type
        String documentType = this.documentType;
        if (dynamicDocumentType) {
            documentType = getDynamicField(event, this.documentType);
        }

        // and the operation
        String operation = this.operation;
        if (dynamicOperation) {
            operation = getDynamicField(event, this.operation);
            if (!operation.equals("put") && !operation.equals("update") && !operation.equals("remove")) {
                logger.error("Operation must be put, update or remove. Ignoring operation: {}", operation);
                // TODO we should put this in the dead letter queue
                return null;
            }
        }
        // add create=true, if applicable
        OperationParameters operationParameters = addCreateIfApplicable(operation, docIdStr);

        logger.trace("Feeding document with ID: {} to namespace: {} and document type: {}",
                docIdStr, namespace, documentType);
        DocumentId docId = DocumentId.of(namespace,
                documentType, docIdStr);

        // create a document from the event data. We need an enclosing "fields" object
        // to match the Vespa put format
        Map<String,Object> doc = new HashMap<>();
        doc.put("fields", eventData);

        // create the request to feed the document
        //return client.put(docId, toJson(doc), operationParameters);
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
    private OperationParameters addCreateIfApplicable(String operation, String docId) {
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
    private String getDynamicField(Event event, String fieldName) {
        Object namespaceFieldValue = event.getField(fieldName);
        if (namespaceFieldValue != null) {
            return namespaceFieldValue.toString();
        } else {
            return fieldName;
        }
    }

    private String toJson(Map<String, Object> eventData) throws JsonProcessingException {
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
        return List.of(VESPA_URL, CLIENT_CERT, CLIENT_KEY, OPERATION, CREATE, NAMESPACE, DOCUMENT_TYPE, ID_FIELD,
                MAX_CONNECTIONS, MAX_STREAMS, MAX_RETRIES, OPERATION_TIMEOUT);
    }

    @Override
    public String getId() {
        return id;
    }
}
