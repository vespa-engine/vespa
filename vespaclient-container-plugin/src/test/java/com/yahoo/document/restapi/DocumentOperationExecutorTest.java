// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi;

import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.FixedBucketSpaces;
import com.yahoo.document.restapi.DocumentOperationExecutor.ErrorType;
import com.yahoo.document.restapi.DocumentOperationExecutor.Group;
import com.yahoo.document.restapi.DocumentOperationExecutor.OperationContext;
import com.yahoo.document.restapi.DocumentOperationExecutor.VisitOperationsContext;
import com.yahoo.document.restapi.DocumentOperationExecutor.VisitorOptions;
import com.yahoo.document.restapi.DocumentOperationExecutorImpl.StorageCluster;
import com.yahoo.document.restapi.DocumentOperationExecutorImpl.DelayQueue;
import com.yahoo.documentapi.DocumentAccessParams;
import com.yahoo.documentapi.Result;
import com.yahoo.documentapi.VisitorControlHandler;
import com.yahoo.documentapi.local.LocalAsyncSession;
import com.yahoo.documentapi.local.LocalDocumentAccess;
import com.yahoo.searchdefinition.derived.Deriver;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.config.content.AllClustersBucketSpacesConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static com.yahoo.document.restapi.DocumentOperationExecutor.ErrorType.BAD_REQUEST;
import static com.yahoo.document.restapi.DocumentOperationExecutor.ErrorType.ERROR;
import static com.yahoo.document.restapi.DocumentOperationExecutor.ErrorType.OVERLOAD;
import static com.yahoo.document.restapi.DocumentOperationExecutor.ErrorType.TIMEOUT;
import static com.yahoo.documentapi.DocumentOperationParameters.parameters;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * This test uses a config definition for the "music" document type, which has a single string field "artist".
 * One cluster named "content" exists, and can be reached through the "route" route for "music" documents.
 *
 * @author jonmv
 */
public class DocumentOperationExecutorTest {

    final AllClustersBucketSpacesConfig bucketConfig = new AllClustersBucketSpacesConfig.Builder()
            .cluster("content",
                     new AllClustersBucketSpacesConfig.Cluster.Builder()
                             .documentType("music",
                                           new AllClustersBucketSpacesConfig.Cluster.DocumentType.Builder()
                                                   .bucketSpace(FixedBucketSpaces.defaultSpace())))
            .build();
    final ClusterListConfig clusterConfig = new ClusterListConfig.Builder()
            .storage(new ClusterListConfig.Storage.Builder().configid("config-id")
                                                            .name("content"))
            .build();
    final DocumentOperationExecutorConfig executorConfig = new DocumentOperationExecutorConfig.Builder()
            .resendDelayMillis(10)
            .defaultTimeoutSeconds(1)
            .maxThrottled(2)
            .build();
    final Map<String, StorageCluster> clusters = Map.of("content", new StorageCluster("content",
                                                                                      "config-id",
                                                                                      Map.of("music", "route")));
    final List<Document> received = new ArrayList<>();
    final List<ErrorType> errors = new ArrayList<>();
    final List<String> messages = new ArrayList<>();
    final List<String> tokens = new ArrayList<>();
    ManualClock clock;
    LocalDocumentAccess access;
    DocumentOperationExecutorImpl executor;
    DocumentType musicType;
    Document doc1;
    Document doc2;
    Document doc3;

    OperationContext operationContext() {
        return new OperationContext((type, error) -> { errors.add(type); messages.add(error); },
                                    document -> document.ifPresent(received::add));
    }

    VisitOperationsContext visitContext() {
        return new VisitOperationsContext((type, error) -> { errors.add(type); messages.add(error); },
                                          token -> token.ifPresent(tokens::add),
                                          received::add);
    }

    LocalAsyncSession session() {
        return (LocalAsyncSession) executor.asyncSession();
    }

    @Before
    public void setUp() {
        clock = new ManualClock();
        access = new LocalDocumentAccess(new DocumentAccessParams().setDocumentmanagerConfig(Deriver.getDocumentManagerConfig("src/test/cfg/music.sd").build()));
        executor = new DocumentOperationExecutorImpl(clusterConfig, bucketConfig, executorConfig, access, clock);
        received.clear();
        errors.clear();
        tokens.clear();

        musicType = access.getDocumentTypeManager().getDocumentType("music");
        doc1 = new Document(musicType, "id:ns:music::1"); doc1.setFieldValue("artist", "one");
        doc2 = new Document(musicType, "id:ns:music:n=1:2"); doc2.setFieldValue("artist", "two");
        doc3 = new Document(musicType, "id:ns:music:g=a:3");
    }

    @After
    public void tearDown() {
        access.shutdown();
    }

    @Test
    public void testResolveCluster() {
        assertEquals("[Storage:cluster=content;clusterconfigid=config-id]",
                     executor.routeToCluster("content"));
        try {
            executor.routeToCluster("blargh");
            fail("Should not find this cluster");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Your Vespa deployment has no content cluster 'blargh', only 'content'", e.getMessage());
        }
        assertEquals("content", DocumentOperationExecutorImpl.resolveCluster(Optional.empty(), clusters).name());
        try {
            DocumentOperationExecutorImpl.resolveCluster(Optional.empty(), Map.of());
            fail("No clusters should fail");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Your Vespa deployment has no content clusters, so the document API is not enabled", e.getMessage());
        }
        try {
            DocumentOperationExecutorImpl.resolveCluster(Optional.empty(), Map.of("one", new StorageCluster("one", "one-config", Map.of()),
                                                                                  "two", new StorageCluster("two", "two-config", Map.of())));
            fail("More than one cluster and no document type should fail");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Please specify one of the content clusters in your Vespa deployment: 'one', 'two'", e.getMessage());
        }
    }

    @Test
    public void testThrottling() throws InterruptedException {
        executor.notifyMaintainers(); // Make sure maintainers have gone to sleep before tests starts.
        // Put documents 1 and 2 into backend.
        executor.put(new DocumentPut(doc1), parameters(), operationContext());
        executor.put(new DocumentPut(doc2), parameters(), operationContext());
        assertEquals(List.of(doc1, doc2), received);

        session().setResultType(Result.ResultType.TRANSIENT_ERROR);

        // First two are put on retry queue.
        executor.get(doc1.getId(), parameters(), operationContext());
        executor.get(doc2.getId(), parameters(), operationContext());
        assertEquals(List.of(), errors);

        // Third operation is rejected.
        executor.get(doc3.getId(), parameters(), operationContext());
        assertEquals(List.of(OVERLOAD), errors);

        // Maintainer does not yet run.
        executor.notifyMaintainers();
        // Third operation is rejected again.
        executor.get(doc3.getId(), parameters(), operationContext());
        assertEquals(List.of(OVERLOAD, OVERLOAD), errors);

        // Maintainer retries documents, but they're put back into the queue with a new delay.
        clock.advance(Duration.ofMillis(20));
        executor.notifyMaintainers();
        assertEquals(List.of(OVERLOAD, OVERLOAD), errors);

        session().setResultType(Result.ResultType.SUCCESS);
        // Maintainer retries documents again, this time successfully.
        clock.advance(Duration.ofMillis(20));
        executor.notifyMaintainers();
        assertEquals(List.of(OVERLOAD, OVERLOAD), errors);
        assertEquals(List.of(doc1, doc2, doc1, doc2), received);
    }

    @Test
    public void testTimeout() throws InterruptedException {
        Phaser phaser = new Phaser(1);
        access.setPhaser(phaser);
        executor.notifyMaintainers(); // Make sure maintainers have gone to sleep before tests starts.

        // Put 1 times out after 1010 ms, Put 2 succeeds after 1010 ms
        executor.put(new DocumentPut(doc1), parameters(), operationContext());
        clock.advance(Duration.ofMillis(20));
        executor.put(new DocumentPut(doc2), parameters(), operationContext());
        executor.notifyMaintainers();
        assertEquals(List.of(), received);
        assertEquals(List.of(), errors);

        clock.advance(Duration.ofMillis(990));
        executor.notifyMaintainers();
        phaser.arrive();                // Let responses flow!
        phaser.arriveAndAwaitAdvance(); // Wait for responses to be delivered. <3 Phaser <3
        assertEquals(List.of(doc2), received);
        assertEquals(List.of(TIMEOUT), errors);

        session().setResultType(Result.ResultType.TRANSIENT_ERROR);
        executor.put(new DocumentPut(doc3), parameters(), operationContext());
        clock.advance(Duration.ofMillis(990));
        executor.notifyMaintainers(); // Retry throttled operation.
        clock.advance(Duration.ofMillis(20));
        executor.notifyMaintainers(); // Time out throttled operation.
        assertEquals(List.of(doc2), received);
        assertEquals(List.of(TIMEOUT, TIMEOUT), errors);

        session().setResultType(Result.ResultType.SUCCESS);
        clock.advance(Duration.ofMillis(20));
        executor.notifyMaintainers(); // Retry not attempted since operation already timed out.
        phaser.arrive();
        phaser.arriveAndAwaitAdvance();
        assertEquals(List.of(doc2), received);
        assertEquals(List.of(TIMEOUT, TIMEOUT), errors);
    }

    @Test
    public void testVisit() throws InterruptedException {
        executor.put(new DocumentPut(doc1), parameters(), operationContext());
        executor.put(new DocumentPut(doc2), parameters(), operationContext());
        executor.put(new DocumentPut(doc3), parameters(), operationContext());
        assertEquals(doc1, received.remove(0));
        assertEquals(doc2, received.remove(0));
        assertEquals(doc3, received.remove(0));

        // No cluster or document type set.
        executor.visit(VisitorOptions.builder()
                                     .build(),
                       visitContext());
        assertEquals("Must set 'cluster' parameter to a valid content cluster id when visiting at a root /document/v1/ level", messages.remove(0));
        assertEquals(BAD_REQUEST, errors.remove(0));
        assertEquals(List.of(), received);

        // Cluster not found.
        executor.visit(VisitorOptions.builder()
                                     .cluster("blargh")
                                     .build(),
                       visitContext());
        assertEquals("Your Vespa deployment has no content cluster 'blargh', only 'content'", messages.remove(0));
        assertEquals(BAD_REQUEST, errors.remove(0));
        assertEquals(List.of(), received);

        // Matches doc2 for user 1.
        executor.visit(VisitorOptions.builder()
                                     .cluster("content")
                                     .group(Group.of(1))
                                     .build(),
                       visitContext());
        for (VisitorControlHandler session : executor.visitorSessions()) {
            session.waitUntilDone();
        }
        assertEquals(List.of(), messages);
        assertEquals(List.of(), errors);
        assertEquals(doc2, received.remove(0));

        // Matches documents in namespace ns of type music in group a.
        executor.visit(VisitorOptions.builder()
                                     .concurrency(2)
                                     .wantedDocumentCount(3)
                                     .namespace("ns")
                                     .documentType("music")
                                     .fieldSet("music:artist")
                                     .group(Group.of("a"))
                                     .build(),
                       visitContext());
        for (VisitorControlHandler session : executor.visitorSessions())
            session.waitUntilDone();
        assertEquals(List.of(), messages);
        assertEquals(List.of(), errors);
        assertEquals(doc3, received.remove(0));

        // Matches documents with non-empty artist field.
        executor.visit(VisitorOptions.builder()
                                     .cluster("content")
                                     .selection("music.artist")
                                     .fieldSet("[id]")
                                     .build(),
                       visitContext());
        for (VisitorControlHandler session : executor.visitorSessions())
            session.waitUntilDone();
        assertEquals(List.of(), messages);
        assertEquals(List.of(), errors);
        assertEquals(List.of(doc1.getId(), doc2.getId()), List.of(received.remove(0).getId(), received.remove(0).getId()));

        // Matches all documents, but we'll shut down midway.
        Phaser phaser = new Phaser(1);
        access.setPhaser(phaser);
        executor.visit(VisitorOptions.builder()
                                     .cluster("content")
                                     .bucketSpace("global")
                                     .build(),
                       visitContext());
        phaser.arriveAndAwaitAdvance(); // First document pending
        CountDownLatch latch = new CountDownLatch(1);
        Thread shutdownThread = new Thread(() -> {
            executor.shutdown();
            latch.countDown();
        });
        shutdownThread.start();
        clock.advance(Duration.ofMillis(100));
        executor.notifyMaintainers(); // Purge timeout operations so maintainers can shut down quickly.
        latch.await();                // Make sure visit session is shut down before next document is considered.
        phaser.awaitAdvance(phaser.arriveAndDeregister()); // See above.
        for (VisitorControlHandler session : executor.visitorSessions()) {
            session.waitUntilDone();
        }
        assertEquals(List.of(), messages);
        assertEquals(List.of(), errors);
        assertEquals(List.of(doc1), received);
    }

    @Test
    public void testDelayQueue() throws ExecutionException, InterruptedException, TimeoutException {
        Supplier<Result> nullOperation = () -> null;
        AtomicLong counter1 = new AtomicLong(0);
        AtomicLong counter2 = new AtomicLong(0);
        AtomicLong counter3 = new AtomicLong(0);
        OperationContext context1 = new OperationContext((type, message) -> counter1.decrementAndGet(), doc -> counter1.incrementAndGet());
        OperationContext context2 = new OperationContext((type, message) -> counter2.decrementAndGet(), doc -> counter2.incrementAndGet());
        OperationContext context3 = new OperationContext((type, message) -> counter3.decrementAndGet(), doc -> counter3.incrementAndGet());
        DelayQueue queue = new DelayQueue(3, (operation, context) -> context.success(Optional.empty()), Duration.ofMillis(3), clock);
        synchronized (queue) { queue.notify(); queue.wait(); } // Make sure maintainers have gone to wait before test starts.

        // Add three operations — the first shall be handled by the queue, the second by an external called, and the third during shutdown.
        assertTrue(queue.add(nullOperation, context1));
        clock.advance(Duration.ofMillis(2));
        assertTrue(queue.add(nullOperation, context2));
        assertTrue(queue.add(nullOperation, context3));
        assertFalse(queue.add(nullOperation, context3));
        assertEquals(3, queue.size());
        assertEquals(0, counter1.get());
        assertEquals(0, counter2.get());
        assertEquals(0, counter3.get());

        context2.error(ERROR, "error"); // Marks this as handled, ready to be evicted.
        synchronized (queue) { queue.notify(); queue.wait(); } // Maintainer does not run yet, as it's not yet time.
        assertEquals(0, counter1.get());
        assertEquals(-1, counter2.get());
        assertEquals(0, counter3.get());
        assertEquals(3, queue.size());

        clock.advance(Duration.ofMillis(2));
        synchronized (queue) { queue.notify(); queue.wait(); } // Maintainer now runs, handling first and evicting second entry.
        assertEquals(1, counter1.get());
        assertEquals(-1, counter2.get());
        assertEquals(0, counter3.get());
        assertEquals(1, queue.size());

        queue.shutdown(Duration.ZERO, context -> context.error(ERROR, "shutdown"))
             .get(1, TimeUnit.SECONDS);
        assertEquals(1, counter1.get());
        assertEquals(-1, counter2.get());
        assertEquals(-1, counter3.get());
        assertEquals(0, queue.size());
    }

}
