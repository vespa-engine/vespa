// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yahoo.vespa.http.client.config.Cluster;
import com.yahoo.vespa.http.client.config.ConnectionParams;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.config.FeedParams;
import com.yahoo.vespa.http.client.core.Document;
import com.yahoo.vespa.http.client.core.Exceptions;
import com.yahoo.vespa.http.client.core.operationProcessor.OperationProcessor;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Einar M R Rosenvinge
 */
public class ClusterConnection implements AutoCloseable {

    private static ObjectMapper jsonMapper = createMapper();

    private final List<IOThread> ioThreads = new ArrayList<>();
    private final int clusterId;
    private final ThreadGroup ioThreadGroup;

    /** The shared queue of document operations the io threads will take from */
    private final DocumentQueue documentQueue;

    /** The single endpoint this sends to, or null if it will send to multiple endpoints */
    private final Endpoint singleEndpoint;

    public ClusterConnection(OperationProcessor operationProcessor,
                             FeedParams feedParams,
                             ConnectionParams connectionParams,
                             Cluster cluster,
                             int clusterId,
                             int clientQueueSizePerCluster,
                             ScheduledThreadPoolExecutor timeoutExecutor,
                             Clock clock) {
        if (cluster.getEndpoints().isEmpty())
            throw new IllegalArgumentException("At least a single endpoint is required in " + cluster);

        this.clusterId = clusterId;
        int totalNumberOfEndpointsInThisCluster = cluster.getEndpoints().size() * connectionParams.getNumPersistentConnectionsPerEndpoint();
        if (totalNumberOfEndpointsInThisCluster == 0)
            throw new IllegalArgumentException("At least 1 persistent connection per endpoint is required in " + cluster);
        int maxInFlightPerSession = Math.max(1, feedParams.getMaxInFlightRequests() / totalNumberOfEndpointsInThisCluster);

        documentQueue = new DocumentQueue(clientQueueSizePerCluster, clock);
        ioThreadGroup = operationProcessor.getIoThreadGroup();
        singleEndpoint = cluster.getEndpoints().size() == 1 ? cluster.getEndpoints().get(0) : null;
        Double idlePollFrequency = feedParams.getIdlePollFrequency();
        if (idlePollFrequency == null)
            idlePollFrequency = 10.0;
        for (Endpoint endpoint : cluster.getEndpoints()) {
            EndpointResultQueue endpointResultQueue = new EndpointResultQueue(operationProcessor,
                                                                              endpoint,
                                                                              clusterId,
                                                                              timeoutExecutor,
                                                                              feedParams.getServerTimeout(TimeUnit.MILLISECONDS) + feedParams.getClientTimeout(TimeUnit.MILLISECONDS));
            for (int i = 0; i < connectionParams.getNumPersistentConnectionsPerEndpoint(); i++) {
                GatewayConnectionFactory connectionFactory;
                if (connectionParams.isDryRun()) {
                    connectionFactory = new DryRunGatewayConnectionFactory(endpoint, clock);
                } else {
                    connectionFactory = new ApacheGatewayConnectionFactory(endpoint,
                                                                           feedParams,
                                                                           cluster.getRoute(),
                                                                           connectionParams,
                                                                           new ApacheGatewayConnection.HttpClientFactory(connectionParams, endpoint.isUseSsl()),
                                                                           operationProcessor.getClientId(),
                                                                           clock
                    );
                }
                IOThread ioThread = new IOThread(operationProcessor.getIoThreadGroup(),
                                                 endpoint,
                                                 endpointResultQueue,
                                                 connectionFactory,
                                                 clusterId,
                                                 feedParams.getMaxChunkSizeBytes(),
                                                 maxInFlightPerSession,
                                                 Duration.ofMillis(feedParams.getLocalQueueTimeOut()),
                                                 documentQueue,
                                                 feedParams.getMaxSleepTimeMs(),
                                                 connectionParams.getConnectionTimeToLive(),
                                                 connectionParams.runThreads(),
                                                 idlePollFrequency,
                                                 clock);
                ioThreads.add(ioThread);
            }
        }
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    public int getClusterId() {
        return clusterId;
    }

    public void post(Document document) throws EndpointIOException {
        try {
            documentQueue.put(document, Thread.currentThread().getThreadGroup() == ioThreadGroup);
        } catch (Throwable t) { // InterruptedException if shutting down, IllegalStateException if already shut down
            throw new EndpointIOException(singleEndpoint, "While sending", t);
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
        StringWriter stringWriter = new StringWriter();
        JsonGenerator jsonGenerator = jsonMapper.createGenerator(stringWriter);
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
            jsonMapper.writeValue(jsonGenerator, connectionStats);
            jsonGenerator.writeEndObject();
        }
        jsonGenerator.writeEndArray();
        jsonGenerator.writeEndObject();
        jsonGenerator.close();
        return stringWriter.toString();
    }

    public List<IOThread> ioThreads() {
        return Collections.unmodifiableList(ioThreads);
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
