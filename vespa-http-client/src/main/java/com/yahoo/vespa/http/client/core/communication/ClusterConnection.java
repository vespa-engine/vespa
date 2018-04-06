// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.http.client.config.Cluster;
import com.yahoo.vespa.http.client.config.ConnectionParams;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.config.FeedParams;
import com.yahoo.vespa.http.client.config.SessionParams;
import com.yahoo.vespa.http.client.core.Document;
import com.yahoo.vespa.http.client.core.Exceptions;
import com.yahoo.vespa.http.client.core.operationProcessor.OperationProcessor;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Einar M R Rosenvinge
 */
public class ClusterConnection implements AutoCloseable {

    private final OperationProcessor operationProcessor;
    private final List<IOThread> ioThreads = new ArrayList<>();
    private final int clusterId;
    private final SessionParams.ErrorReporter errorReporter;
    private static JsonFactory jsonFactory = new JsonFactory();
    private static ObjectMapper objectMapper = new ObjectMapper();

    public ClusterConnection(
            OperationProcessor operationProcessor,
            FeedParams feedParams,
            ConnectionParams connectionParams,
            SessionParams.ErrorReporter errorReporter,
            Cluster cluster,
            int clusterId,
            int clientQueueSizePerCluster,
            ScheduledThreadPoolExecutor timeoutExecutor) {
        this.errorReporter = errorReporter;
        if (cluster.getEndpoints().isEmpty()) {
            throw new IllegalArgumentException("Cannot feed to empty cluster.");
        }
        this.operationProcessor = operationProcessor;
        this.clusterId = clusterId;
        final int totalNumberOfEndpointsInThisCluster = cluster.getEndpoints().size()
                * connectionParams.getNumPersistentConnectionsPerEndpoint();
        if (totalNumberOfEndpointsInThisCluster == 0) {
            return;
        }
        // Lower than 1 does not make any sense.
        final int maxInFlightPerSession = Math.max(
                1, feedParams.getMaxInFlightRequests() / totalNumberOfEndpointsInThisCluster);
        DocumentQueue documentQueue = null;
        for (Endpoint endpoint : cluster.getEndpoints()) {
            final EndpointResultQueue endpointResultQueue = new EndpointResultQueue(
                    operationProcessor,
                    endpoint,
                    clusterId,
                    timeoutExecutor,
                    feedParams.getServerTimeout(TimeUnit.MILLISECONDS)
                            + feedParams.getClientTimeout(TimeUnit.MILLISECONDS));
            for (int i = 0; i < connectionParams.getNumPersistentConnectionsPerEndpoint(); i++) {
                GatewayConnection gatewayConnection;
                if (connectionParams.isDryRun()) {
                    gatewayConnection = new DryRunGatewayConnection(endpoint);
                } else {
                    gatewayConnection = new ApacheGatewayConnection(
                            endpoint,
                            feedParams,
                            cluster.getRoute(),
                            connectionParams,
                            new ApacheGatewayConnection.HttpClientFactory(
                                    connectionParams, endpoint.isUseSsl()),
                            operationProcessor.getClientId()
                    );
                }
                if (connectionParams.isEnableV3Protocol()) {
                    if (documentQueue == null) {
                        documentQueue = new DocumentQueue(clientQueueSizePerCluster);
                    }
                } else {
                    documentQueue = new DocumentQueue(clientQueueSizePerCluster / cluster.getEndpoints().size());
                }
                final IOThread ioThread = new IOThread(
                        endpointResultQueue,
                        gatewayConnection,
                        clusterId,
                        feedParams.getMaxChunkSizeBytes(),
                        maxInFlightPerSession,
                        feedParams.getLocalQueueTimeOut(),
                        documentQueue,
                        connectionParams.isEnableV3Protocol() ? feedParams.getMaxSleepTimeMs() : 0);
                ioThreads.add(ioThread);
            }
        }
    }

    public int getClusterId() {
        return clusterId;
    }

    public void post(Document document) throws EndpointIOException {
        String documentIdStr = document.getDocumentId();
        //the same document ID must always go to the same destination
        // In noHandshakeMode this has no effect as the documentQueue is shared between the IOThreads.
        int hash = documentIdStr.hashCode() & 0x7FFFFFFF;  //strip sign bit
        IOThread ioThread = ioThreads.get(hash % ioThreads.size());
        try {
            ioThread.post(document);
        } catch (Throwable t) {
            throw new EndpointIOException(ioThread.getEndpoint(), "While sending", t);
        }
    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    @Override
    public void close() {
        List<Exception> exceptions = new ArrayList<>();
        for (IOThread ioThread : ioThreads) {
            try {
                ioThread.close();
            } catch (Exception e) {
                exceptions.add(e);
            }
        }
        if (exceptions.isEmpty()) {
            return;
        }
        if (exceptions.size() == 1) {
            if (exceptions.get(0) instanceof RuntimeException) {
                throw (RuntimeException) exceptions.get(0);
            } else {
                throw new RuntimeException(exceptions.get(0));
            }
        }
        StringBuilder b = new StringBuilder();
        b.append("Exception thrown while closing one or more endpoints: ");
        for (int i = 0; i < exceptions.size(); i++) {
            Exception e = exceptions.get(i);
            b.append(Exceptions.toMessageString(e));
            if (i != (exceptions.size() - 1)) {
                b.append(", ");
            }
        }
        throw new RuntimeException(b.toString(), exceptions.get(0));
    }

    public String getStatsAsJSon() throws IOException {
        final StringWriter stringWriter = new StringWriter();
        JsonGenerator jsonGenerator = jsonFactory.createGenerator(stringWriter);
        jsonGenerator.writeStartObject();
        jsonGenerator.writeArrayFieldStart("session");
        for (IOThread ioThread : ioThreads) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeObjectFieldStart("endpoint");
            jsonGenerator.writeStringField("host", ioThread.getEndpoint().getHostname());
            jsonGenerator.writeNumberField("port", ioThread.getEndpoint().getPort());
            jsonGenerator.writeEndObject();
            jsonGenerator.writeFieldName("stats");
            IOThread.ConnectionStats connectionStats = ioThread.getConnectionStats();
            objectMapper.writeValue(jsonGenerator, connectionStats);
            jsonGenerator.writeEndObject();
        }
        jsonGenerator.writeEndArray();
        jsonGenerator.writeEndObject();
        jsonGenerator.close();
        return stringWriter.toString();
    }

    @Override
    public boolean equals(Object o) {
        return (this == o) || (o instanceof ClusterConnection && clusterId == ((ClusterConnection) o).clusterId);
    }

    @Override
    public int hashCode() {
        return clusterId;
    }
}
