// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core;

import com.yahoo.vespa.http.client.FeedClient;
import com.yahoo.vespa.http.client.FeedEndpointException;
import com.yahoo.vespa.http.client.ManualClock;
import com.yahoo.vespa.http.client.Result;
import com.yahoo.vespa.http.client.config.Cluster;
import com.yahoo.vespa.http.client.config.ConnectionParams;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.config.SessionParams;
import com.yahoo.vespa.http.client.core.communication.ClusterConnection;
import com.yahoo.vespa.http.client.core.communication.IOThread;
import com.yahoo.vespa.http.client.core.communication.IOThreadTest;
import com.yahoo.vespa.http.client.core.operationProcessor.IncompleteResultsThrottler;
import com.yahoo.vespa.http.client.core.operationProcessor.OperationProcessor;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.Assert.assertEquals;

/**
 * Helper for testing with an operation processor
 *
 * @author bratseth
 */
public class OperationProcessorTester {

    private final Endpoint endpoint;
    private final int clusterId = 0;
    private final ManualClock clock;
    private final TestResultCallback resultCallback;
    private final OperationProcessor operationProcessor;

    public OperationProcessorTester() {
        endpoint = Endpoint.create("test-endpoint");
        SessionParams.Builder params = new SessionParams.Builder();
        Cluster.Builder clusterParams = new Cluster.Builder();
        clusterParams.addEndpoint(endpoint);
        params.addCluster(clusterParams.build());
        ConnectionParams.Builder connectionParams = new ConnectionParams.Builder();
        connectionParams.setDryRun(true);
        connectionParams.setRunThreads(false);
        params.setConnectionParams(connectionParams.build());

        clock = new ManualClock(Instant.ofEpochMilli(0));
        resultCallback = new TestResultCallback();
        operationProcessor = new OperationProcessor(new IncompleteResultsThrottler(1, 100, clock, new ThrottlePolicy()),
                                                    resultCallback,
                                                    params.build(),
                                                    new ScheduledThreadPoolExecutor(1),
                                                    clock);
    }

    public ManualClock clock() { return clock; }

    /** Asserts that this has but a single IOThread and returns it */
    public IOThread getSingleIOThread() {
        assertEquals(1, clusterConnections().size());
        assertEquals(1, clusterConnections().get(0).ioThreads().size());
        return clusterConnections().get(0).ioThreads().get(0);
    }

    /** Do n iteration of work in all io threads of this */
    public void tick(int n) {
        for (int i = 0; i < n; i++)
            for (ClusterConnection cluster : operationProcessor.clusters())
                for (IOThread thread : cluster.ioThreads())
                    thread.tick();
    }

    public void send(String documentId) {
        operationProcessor.sendDocument(new Document(documentId, documentId, "data of " + documentId, null, clock.instant()));
    }

    public int incomplete() {
        return operationProcessor.getIncompleteResultQueueSize();
    }

    public int success() {
        return resultCallback.successes;
    }

    public List<ClusterConnection> clusterConnections() {
        return operationProcessor.clusters();
    }

    public int failures() {
        return resultCallback.failures;
    }

    public int endpointExceptions() {
        return resultCallback.endpointExceptions;
    }

    public Result lastResult() {
        return resultCallback.lastResult;
    }

    private static class TestResultCallback implements FeedClient.ResultCallback {

        private int successes = 0;
        private int failures = 0;
        private int endpointExceptions = 0;
        private Result lastResult;

        @Override
        public void onCompletion(String docId, Result documentResult) {
            this.lastResult = documentResult;
            if (documentResult.isSuccess())
                successes++;
            else
                failures++;
        }

        @Override
        public void onEndpointException(FeedEndpointException exception) {
            endpointExceptions++;
        }

    }

}
