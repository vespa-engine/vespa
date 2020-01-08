// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.operationProcessor;

import com.yahoo.vespa.http.client.Result;
import com.yahoo.vespa.http.client.config.Cluster;
import com.yahoo.vespa.http.client.config.ConnectionParams;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.config.SessionParams;
import com.yahoo.vespa.http.client.core.Document;
import com.yahoo.vespa.http.client.core.EndpointResult;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Einar M R Rosenvinge
 */
public class OperationProcessorTest {

    final Queue<Result> queue = new ArrayDeque<>();
    final Document doc1 = new Document("id:a:type::b", null, "data doc 1", null);
    final Document doc1b = new Document("id:a:type::b", null, "data doc 1b", null);
    final Document doc2 = new Document("id:a:type::b2", null, "data doc 2", null);
    final Document doc3 = new Document("id:a:type::b3", null, "data doc 3", null);

    @Test
    public void testBasic() {
        SessionParams sessionParams = new SessionParams.Builder()
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("localhost")).build())
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("localhost")).build())
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("localhost")).build())
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("localhost")).build())
                .build();

        OperationProcessor q = new OperationProcessor(
                new IncompleteResultsThrottler(1000, 1000, null, null),
                (docId, documentResult) -> queue.add(documentResult),
                sessionParams, null);


        q.resultReceived(new EndpointResult("foo", new Result.Detail(null)), 0);
        assertEquals(0, queue.size());


        q.sendDocument(doc1);
        assertEquals(0, queue.size());

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("a"))), 0);
        assertEquals(0, queue.size());

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("b"))), 1);
        assertEquals(0, queue.size());

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("c"))), 2);
        assertEquals(0, queue.size());

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("d"))), 3);
        assertEquals(1, queue.size());

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("e"))), 0);
        assertEquals(1, queue.size());

        //check a, b, c, d
        Result aggregated = queue.poll();
        assertEquals("id:a:type::b", aggregated.getDocumentId());
        assertEquals(4, aggregated.getDetails().size());
        assertEquals("a", aggregated.getDetails().get(0).getEndpoint().getHostname());
        assertEquals("b", aggregated.getDetails().get(1).getEndpoint().getHostname());
        assertEquals("c", aggregated.getDetails().get(2).getEndpoint().getHostname());
        assertEquals("d", aggregated.getDetails().get(3).getEndpoint().getHostname());
        assertEquals("data doc 1", aggregated.getDocumentDataAsCharSequence().toString());

        assertEquals(0, queue.size());

        q.sendDocument(doc2);
        assertEquals(0, queue.size());

        q.resultReceived(new EndpointResult(doc2.getOperationId(), new Result.Detail(Endpoint.create("a"))), 0);
        assertEquals(0, queue.size());

        q.resultReceived(new EndpointResult(doc2.getOperationId(), new Result.Detail(Endpoint.create("b"))), 1);
        assertEquals(0, queue.size());

        q.resultReceived(new EndpointResult(doc2.getOperationId(), new Result.Detail(Endpoint.create("c"))), 2);
        assertEquals(0, queue.size());

        q.resultReceived(new EndpointResult(doc2.getOperationId(), new Result.Detail(Endpoint.create("d"))), 3);
        assertEquals(1, queue.size());

        q.resultReceived(new EndpointResult(doc2.getOperationId(), new Result.Detail(Endpoint.create("e"))), 0);
        assertEquals(1, queue.size());

        // check a, b, c, d
        aggregated = queue.poll();
        assertEquals("id:a:type::b2", aggregated.getDocumentId());
        assertEquals(4, aggregated.getDetails().size());
        assertEquals("a", aggregated.getDetails().get(0).getEndpoint().getHostname());
        assertEquals("b", aggregated.getDetails().get(1).getEndpoint().getHostname());
        assertEquals("c", aggregated.getDetails().get(2).getEndpoint().getHostname());
        assertEquals("d", aggregated.getDetails().get(3).getEndpoint().getHostname());
        assertEquals("data doc 2", aggregated.getDocumentDataAsCharSequence().toString());

        assertEquals(0, queue.size());
    }

    @Test
    public void testBlockingOfOperationsTwoEndpoints() {
        SessionParams sessionParams = new SessionParams.Builder()
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("localhost")).build())
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("localhost")).build())
                .setConnectionParams(new ConnectionParams.Builder().build())
                .build();
        OperationProcessor operationProcessor = new OperationProcessor(
                new IncompleteResultsThrottler(1000, 1000, null, null),
                (docId, documentResult) -> queue.add(documentResult),
                sessionParams, null);

        operationProcessor.sendDocument(doc1);
        operationProcessor.sendDocument(doc1b);

        assertEquals(0, queue.size());
        // Only one operations should be in flight.
        assertEquals(1, operationProcessor.getIncompleteResultQueueSize());
        operationProcessor.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("host"))), 0);
        assertEquals(0, queue.size());
        assertEquals(1, operationProcessor.getIncompleteResultQueueSize());
        operationProcessor.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("host"))), 1);
        assertEquals(1, queue.size());
        assertEquals(1, operationProcessor.getIncompleteResultQueueSize());
        operationProcessor.resultReceived(new EndpointResult(doc1b.getOperationId(), new Result.Detail(Endpoint.create("host"))), 0);
        assertEquals(1, queue.size());
        assertEquals(1, operationProcessor.getIncompleteResultQueueSize());
        operationProcessor.resultReceived(new EndpointResult(doc1b.getOperationId(), new Result.Detail(Endpoint.create("host"))), 1);
        assertEquals(2, queue.size());
        assertEquals(0, operationProcessor.getIncompleteResultQueueSize());
        // This should have no effect.
        operationProcessor.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("host"))), 0);
        operationProcessor.resultReceived(new EndpointResult(doc1b.getOperationId(), new Result.Detail(Endpoint.create("host"))), 0);
        operationProcessor.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("host"))), 1);
        operationProcessor.resultReceived(new EndpointResult(doc1b.getOperationId(), new Result.Detail(Endpoint.create("host"))), 1);
        assertEquals(2, queue.size());
    }

    @Test
    public void testBlockingOfOperationsToSameDocIdWithTwoOperations() {
        SessionParams sessionParams = new SessionParams.Builder()
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("localhost")).build())
                .setConnectionParams(new ConnectionParams.Builder().build())
                .build();

        OperationProcessor operationProcessor = new OperationProcessor(
                new IncompleteResultsThrottler(1000, 1000, null, null),
                (docId, documentResult) -> queue.add(documentResult),
                sessionParams, null);

        operationProcessor.sendDocument(doc1);
        operationProcessor.sendDocument(doc1b);

        assertEquals(0, queue.size());
        // Only one operations should be in flight.
        assertEquals(1, operationProcessor.getIncompleteResultQueueSize());
        assertEquals(doc1.getOperationId(), operationProcessor.oldestIncompleteResultId().get());
        operationProcessor.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("host"))), 0);
        assertEquals(1, queue.size());
        assertEquals(1, operationProcessor.getIncompleteResultQueueSize());
        assertEquals(doc1b.getOperationId(), operationProcessor.oldestIncompleteResultId().get());
        operationProcessor.resultReceived(new EndpointResult(doc1b.getOperationId(), new Result.Detail(Endpoint.create("host"))), 0);
        assertEquals(2, queue.size());
        assertEquals(0, operationProcessor.getIncompleteResultQueueSize());
        assertFalse(operationProcessor.oldestIncompleteResultId().isPresent());
        // This should have no effect.
        operationProcessor.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("host"))), 0);
        operationProcessor.resultReceived(new EndpointResult(doc1b.getOperationId(), new Result.Detail(Endpoint.create("host"))), 0);
        assertEquals(2, queue.size());
    }

    @Test
    public void testBlockingOfOperationsToSameDocIdMany() {
        SessionParams sessionParams = new SessionParams.Builder()
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("localhost")).build())
                .setConnectionParams(new ConnectionParams.Builder().build())
                .build();

        OperationProcessor operationProcessor = new OperationProcessor(
                new IncompleteResultsThrottler(1000, 1000, null, null),
                (docId, documentResult) -> queue.add(documentResult),
                sessionParams, null);

        Queue<Document> documentQueue = new ArrayDeque<>();
        for (int x = 0; x < 100; x++) {
            Document document = new Document("id:a:type::b", null, String.valueOf(x), null);
            operationProcessor.sendDocument(document);
            documentQueue.add(document);
        }

        for (int x = 0; x < 100; x++) {
            assertEquals(x, queue.size());
            // Only one operations should be in flight.
            assertEquals(1, operationProcessor.getIncompleteResultQueueSize());
            Document document = documentQueue.poll();
            operationProcessor.resultReceived(new EndpointResult(document.getOperationId(), new Result.Detail(Endpoint.create("host"))), 0);
            assertEquals(x+1, queue.size());
            if (x < 99) {
                assertEquals(1, operationProcessor.getIncompleteResultQueueSize());
            } else {
                assertEquals(0, operationProcessor.getIncompleteResultQueueSize());
            }
        }
    }

    @Test
    public void testMixOfBlockingAndNonBlocking() {
        Endpoint endpoint = Endpoint.create("localhost");
        SessionParams sessionParams = new SessionParams.Builder()
                .addCluster(new Cluster.Builder().addEndpoint(endpoint).build())
                .setConnectionParams(new ConnectionParams.Builder().build())
                .build();

        OperationProcessor operationProcessor = new OperationProcessor(
                new IncompleteResultsThrottler(1000, 1000, null, null),
                (docId, documentResult) -> queue.add(documentResult),
                sessionParams, null);

        operationProcessor.sendDocument(doc1);
        operationProcessor.sendDocument(doc1b); // Blocked
        operationProcessor.sendDocument(doc2);
        operationProcessor.sendDocument(doc3);

        assertEquals(0, queue.size());
        assertEquals(3, operationProcessor.getIncompleteResultQueueSize());
        assertEquals(doc1.getOperationId(), operationProcessor.oldestIncompleteResultId().get());
        // This should have no effect since it should not be sent.
        operationProcessor.resultReceived(new EndpointResult(doc1b.getOperationId(), new Result.Detail(endpoint)), 0);
        assertEquals(3, operationProcessor.getIncompleteResultQueueSize());
        assertEquals(doc1.getOperationId(), operationProcessor.oldestIncompleteResultId().get());

        operationProcessor.resultReceived(new EndpointResult(doc3.getOperationId(), new Result.Detail(endpoint)), 0);
        assertEquals(2, operationProcessor.getIncompleteResultQueueSize());
        assertEquals(doc1.getOperationId(), operationProcessor.oldestIncompleteResultId().get());
        operationProcessor.resultReceived(new EndpointResult(doc2.getOperationId(), new Result.Detail(endpoint)), 0);
        assertEquals(1, operationProcessor.getIncompleteResultQueueSize());
        assertEquals(doc1.getOperationId(), operationProcessor.oldestIncompleteResultId().get());
        operationProcessor.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(endpoint)), 0);
        assertEquals(1, operationProcessor.getIncompleteResultQueueSize());
        assertEquals(doc1b.getOperationId(), operationProcessor.oldestIncompleteResultId().get());
        operationProcessor.resultReceived(new EndpointResult(doc1b.getOperationId(), new Result.Detail(endpoint)), 0);
        assertEquals(0, operationProcessor.getIncompleteResultQueueSize());
        assertFalse(operationProcessor.oldestIncompleteResultId().isPresent());
    }

    @Test
    public void assertThatDuplicateResultsFromOneClusterWorks() {
        SessionParams sessionParams = new SessionParams.Builder()
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("localhost")).build())
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("localhost")).build())
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("localhost")).build())
                .build();

        OperationProcessor q = new OperationProcessor(
                new IncompleteResultsThrottler(1000, 1000, null, null),
                (docId, documentResult) -> queue.add(documentResult),
                sessionParams, null);

        q.sendDocument(doc1);
        assertEquals(0, queue.size());

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("a"))), 0);
        assertEquals(0, queue.size());

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("b"))), 0);
        assertEquals(0, queue.size());

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("c"))), 0);
        assertEquals(0, queue.size());
    }

    @Test
    public void testMultipleDuplicateDocIds() {
        SessionParams sessionParams = new SessionParams.Builder()
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("localhost")).build())
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("localhost")).build())
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("localhost")).build())
                .build();

        OperationProcessor q = new OperationProcessor(
                new IncompleteResultsThrottler(1000, 1000, null, null),
                (docId, documentResult) -> queue.add(documentResult),
                sessionParams, null);

        q.sendDocument(doc1);
        assertEquals(0, queue.size());
        q.sendDocument(doc2);
        assertEquals(0, queue.size());
        q.sendDocument(doc3);

        assertEquals(0, queue.size());

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("a"))), 0);
        assertEquals(0, queue.size());

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("a"))), 0);
        assertEquals(0, queue.size());

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("b"))), 1);
        assertEquals(0, queue.size());

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("c"))), 2);
        assertEquals(1, queue.size());

        q.resultReceived(new EndpointResult(doc3.getOperationId(), new Result.Detail(Endpoint.create("a"))), 0);
        assertEquals(1, queue.size());

        q.resultReceived(new EndpointResult(doc2.getOperationId(), new Result.Detail(Endpoint.create("a"))), 0);
        assertEquals(1, queue.size());

        q.resultReceived(new EndpointResult(doc2.getOperationId(), new Result.Detail(Endpoint.create("b"))), 1);
        assertEquals(1, queue.size());

        q.resultReceived(new EndpointResult(doc2.getOperationId(), new Result.Detail(Endpoint.create("c"))), 2);
        assertEquals(2, queue.size());

        q.resultReceived(new EndpointResult(doc3.getOperationId(), new Result.Detail(Endpoint.create("c"))), 2);
        assertEquals(2, queue.size());

        q.resultReceived(new EndpointResult(doc3.getOperationId(), new Result.Detail(Endpoint.create("c"))), 2);
        assertEquals(2, queue.size());

        q.resultReceived(new EndpointResult(doc3.getOperationId(), new Result.Detail(Endpoint.create("b"))), 1);
        assertEquals(3, queue.size());

        q.resultReceived(new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("b"))), 1);
        assertEquals(3, queue.size());
        assertEquals("data doc 1", queue.remove().getDocumentDataAsCharSequence().toString());
        assertEquals("data doc 2", queue.remove().getDocumentDataAsCharSequence().toString());
        assertEquals("data doc 3", queue.remove().getDocumentDataAsCharSequence().toString());
    }

    @Test
    public void testWaitBlocks() throws InterruptedException {
        SessionParams sessionParams = new SessionParams.Builder()
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("localhost")).build())
                .build();

        OperationProcessor operationProcessor = new OperationProcessor(
                new IncompleteResultsThrottler(1, 1, null, null),
                (docId, documentResult) -> {},
                sessionParams, null);

        operationProcessor.sendDocument(doc1);

        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(1);

        Thread shouldWait = new Thread(()-> {
            started.countDown();
            operationProcessor.sendDocument(doc2);
            done.countDown();
        });
        shouldWait.start();
        started.await();
        // We want the test to pass fast so we only wait 40mS to see that it is blocking. This might lead to
        // some false positives, but that is ok.
        assertFalse(done.await(40, TimeUnit.MILLISECONDS));
        operationProcessor.resultReceived(
                new EndpointResult(doc1.getOperationId(), new Result.Detail(Endpoint.create("d"))), 0);
        assertTrue(done.await(120, TimeUnit.SECONDS));

    }

    @Test
    public void testSendsResponseToQueuedDocumentOnClose() throws InterruptedException {
        SessionParams sessionParams = new SessionParams.Builder()
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("localhost")).build())
                .build();

        ScheduledThreadPoolExecutor executor = mock(ScheduledThreadPoolExecutor.class);
        when(executor.awaitTermination(anyLong(), any())).thenReturn(true);

        CountDownLatch countDownLatch = new CountDownLatch(3);

        OperationProcessor operationProcessor = new OperationProcessor(
                new IncompleteResultsThrottler(19, 19, null, null),
                (docId, documentResult) -> {
                    countDownLatch.countDown();
                },
                sessionParams, executor);

        // Will fail due to bogus host name, but will be retried.
        operationProcessor.sendDocument(doc1);
        operationProcessor.sendDocument(doc2);
        operationProcessor.sendDocument(doc3);

        // Will create fail results.
        operationProcessor.close();
        countDownLatch.await();
    }

    @Test
    public void unknownHostThrowsExceptionAtConstructionTime() {
        try {
            SessionParams sessionParams = new SessionParams.Builder()
                    .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("localhost")).build())
                    .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create("unknown.invalid")).build())
                    .build();
            ScheduledThreadPoolExecutor executor = mock(ScheduledThreadPoolExecutor.class);

            CountDownLatch countDownLatch = new CountDownLatch(3);

            new OperationProcessor(
                    new IncompleteResultsThrottler(19, 19, null, null),
                    (docId, documentResult) -> {
                        countDownLatch.countDown();
                    },
                    sessionParams, executor);

            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Unknown host: unknown.invalid:4080 ssl=false", e.getMessage());
        }
    }

}
