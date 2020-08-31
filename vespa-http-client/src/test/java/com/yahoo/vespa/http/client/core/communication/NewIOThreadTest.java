// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import com.yahoo.vespa.http.client.FeedClient;
import com.yahoo.vespa.http.client.FeedEndpointException;
import com.yahoo.vespa.http.client.ManualClock;
import com.yahoo.vespa.http.client.Result;
import com.yahoo.vespa.http.client.config.Cluster;
import com.yahoo.vespa.http.client.config.ConnectionParams;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.config.SessionParams;
import com.yahoo.vespa.http.client.core.Document;
import com.yahoo.vespa.http.client.core.EndpointResult;
import com.yahoo.vespa.http.client.core.ThrottlePolicy;
import com.yahoo.vespa.http.client.core.operationProcessor.IncompleteResultsThrottler;
import com.yahoo.vespa.http.client.core.operationProcessor.OperationProcessor;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

/**
 * TODO: Migrate IOThreadTests here.
 *
 * @author bratseth
 */
public class NewIOThreadTest {

    @Test
    public void testBasics() {
        OperationProcessorTester tester = new OperationProcessorTester();
        assertEquals(0, tester.inflight());
        assertEquals(0, tester.success());
        assertEquals(0, tester.failures());
        tester.send("doc1");
        tester.send("doc2");
        tester.send("doc3");
        assertEquals(3, tester.inflight());
        assertEquals(0, tester.success());
        assertEquals(0, tester.failures());
        tester.success("doc1");
        tester.success("doc2");
        tester.success("doc3");
        assertEquals(0, tester.inflight());
        assertEquals(3, tester.success());
        assertEquals(0, tester.failures());
    }

    @Test
    public void testPollingOldConnections() {
        OperationProcessorTester tester = new OperationProcessorTester();
        tester.tick(3);

        assertEquals(1, tester.clusterConnections().size());
        assertEquals(1, tester.clusterConnections().get(0).ioThreads().size());
        IOThread ioThread = tester.clusterConnections().get(0).ioThreads().get(0);
        DryRunGatewayConnection firstConnection = (DryRunGatewayConnection)ioThread.currentConnection();
        assertEquals(0, ioThread.oldConnections().size());

        firstConnection.hold(true);
        tester.send("doc1");
        tester.tick(1);

        tester.clock().advance(Duration.ofSeconds(16)); // Default connection ttl is 15
        tester.tick(3);

        assertEquals(1, ioThread.oldConnections().size());
        assertEquals(firstConnection, ioThread.oldConnections().get(0));
        assertNotSame(firstConnection, ioThread.currentConnection());
        assertEquals(16, firstConnection.lastPollTime().toEpochMilli() / 1000);

        // Check old connection poll pattern (exponential backoff)
        assertLastPollTimeWhenAdvancing(16, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(18, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(18, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(18, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(18, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(22, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(22, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(22, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(22, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(22, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(22, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(22, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(22, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(30, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(30, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(30, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(30, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(30, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(30, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(30, 1, firstConnection, tester);
        assertLastPollTimeWhenAdvancing(30, 1, firstConnection, tester);

        tester.clock().advance(Duration.ofSeconds(200));
        tester.tick(1);
        assertEquals("Old connection is eventually removed", 0, ioThread.oldConnections().size());
    }

    private void assertLastPollTimeWhenAdvancing(int lastPollTimeSeconds,
                                                 int advanceSeconds,
                                                 DryRunGatewayConnection connection,
                                                 OperationProcessorTester tester) {
        tester.clock().advance(Duration.ofSeconds(advanceSeconds));
        tester.tick(1);
        assertEquals(lastPollTimeSeconds, connection.lastPollTime().toEpochMilli() / 1000);
    }

    private static class OperationProcessorTester {

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

        public void success(String documentId) {
            operationProcessor.resultReceived(new EndpointResult(documentId, new Result.Detail(endpoint)), clusterId);
        }

        public int inflight() {
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

    }

    private static class TestResultCallback implements FeedClient.ResultCallback {

        private int successes = 0;
        private int failures = 0;

        @Override
        public void onCompletion(String docId, Result documentResult) {
            successes++;
        }

        @Override
        public void onEndpointException(FeedEndpointException exception) {
            failures++;
        }


    }

}
