// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bjorncs
 */
class FeedClientTest {

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdown();
        assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));
    }

    @Test
    void await_returns_list_of_result_on_success() {
        MyResult r1 = new MyResult();
        CompletableFuture<Result> f1 = CompletableFuture.completedFuture(r1);
        MyResult r2 = new MyResult();
        CompletableFuture<Result> f2 = CompletableFuture.completedFuture(r2);
        MyResult r3 = new MyResult();
        CompletableFuture<Result> f3 = CompletableFuture.completedFuture(r3);

        List<Result> aggregated = FeedClient.await(f1, f2, f3);
        assertEquals(3, aggregated.size());
        assertEquals(r1, aggregated.get(0));
        assertEquals(r2, aggregated.get(1));
        assertEquals(r3, aggregated.get(2));
    }

    @Test
    void await_handles_async_completion_with_success() throws ExecutionException, InterruptedException {
        CompletableFuture<Result> f1 = new CompletableFuture<>();
        CompletableFuture<Result> f2 = new CompletableFuture<>();
        CompletableFuture<Result> f3 = new CompletableFuture<>();

        CompletableFuture<List<Result>> awaitPromise = CompletableFuture.supplyAsync(() -> FeedClient.await(f1, f2, f3), executor);
        // Completed in reverse order
        MyResult r3 = new MyResult();
        f3.complete(r3);
        MyResult r2 = new MyResult();
        f2.complete(r2);
        MyResult r1 = new MyResult();
        f1.complete(r1);

        List<Result> aggregated = awaitPromise.get();
        assertEquals(3, aggregated.size());
        assertEquals(r1, aggregated.get(0));
        assertEquals(r2, aggregated.get(1));
        assertEquals(r3, aggregated.get(2));
    }

    @Test
    void await_throws_when_some_results_completes_exceptionally() {
        CompletableFuture<Result> f1 = new CompletableFuture<>();
        DocumentId docId1 = DocumentId.of("music", "music", "doc1");
        FeedException exceptionDoc1 = new FeedException(docId1, "Doc1 failed");
        f1.completeExceptionally(exceptionDoc1);
        CompletableFuture<Result> f2 = new CompletableFuture<>();
        DocumentId docId2 = DocumentId.of("music", "music", "doc2");
        FeedException exceptionDoc2 = new FeedException(docId2, "Doc2 failed");
        f2.completeExceptionally(exceptionDoc2);
        CompletableFuture<Result> f3 = CompletableFuture.completedFuture(new MyResult());

        MultiFeedException multiException = assertThrows(MultiFeedException.class, () -> FeedClient.await(f1, f2, f3));
        Set<DocumentId> expectedDocsIds = new HashSet<>(Arrays.asList(docId1, docId2));
        assertEquals(expectedDocsIds, new HashSet<>(multiException.documentIds()));
        Set<FeedException> expectedExceptions = new HashSet<>(Arrays.asList(exceptionDoc1, exceptionDoc2));
        assertEquals(expectedExceptions, new HashSet<>(multiException.feedExceptions()));
        assertEquals("2 feed operations failed", multiException.getMessage());
    }

    static class MyResult implements Result {
        @Override public Type type() { return null; }
        @Override public DocumentId documentId() { return null; }
        @Override public Optional<String> resultMessage() { return Optional.empty(); }
        @Override public Optional<String> traceMessage() { return Optional.empty(); }
    }
}
