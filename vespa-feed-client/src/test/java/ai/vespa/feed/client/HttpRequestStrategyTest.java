// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import ai.vespa.feed.client.FeedClient.CircuitBreaker;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static ai.vespa.feed.client.FeedClient.CircuitBreaker.State.CLOSED;
import static ai.vespa.feed.client.FeedClient.CircuitBreaker.State.HALF_OPEN;
import static ai.vespa.feed.client.FeedClient.CircuitBreaker.State.OPEN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpRequestStrategyTest {

    @Test
    void testConcurrency() {
        int documents = 1 << 16;
        HttpRequest request = new HttpRequest("PUT", "/", null, null);
        HttpResponse response = HttpResponse.of(200, "{}".getBytes(UTF_8));
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        Cluster cluster = new BenchmarkingCluster((__, vessel) -> executor.schedule(() -> vessel.complete(response), (int) (Math.random() * 2 * 10), TimeUnit.MILLISECONDS));

        HttpRequestStrategy strategy = new HttpRequestStrategy(FeedClientBuilder.create(URI.create("https://dummy.com:123"))
                                                                                .setConnectionsPerEndpoint(1 << 10)
                                                                                .setMaxStreamPerConnection(1 << 12),
                                                               cluster);
        CountDownLatch latch = new CountDownLatch(1);
        new Thread(() -> {
            try {
                while ( ! latch.await(1, TimeUnit.SECONDS))
                    System.err.println(cluster.stats().inflight());
            }
            catch (InterruptedException ignored) { }
        }).start();
        long startNanos = System.nanoTime();
        for (int i = 0; i < documents; i++)
            strategy.enqueue(DocumentId.of("ns", "type", Integer.toString(i)), request);

        strategy.await();
        latch.countDown();
        executor.shutdown();
        cluster.close();
        OperationStats stats = cluster.stats();
        long successes = stats.responsesByCode().get(200);
        System.err.println(successes + " successes in " + (System.nanoTime() - startNanos) * 1e-9 + " seconds");
        System.err.println(stats);

        assertEquals(documents, stats.requests());
        assertEquals(documents, stats.responses());
        assertEquals(documents, stats.responsesByCode().get(200));
        assertEquals(0, stats.inflight());
        assertEquals(0, stats.exceptions());
        assertEquals(0, stats.bytesSent());
        assertEquals(2 * documents, stats.bytesReceived());
    }

    @Test
    void testRetries() throws ExecutionException, InterruptedException {
        int minStreams = 16; // Hard limit for minimum number of streams per connection.
        MockCluster cluster = new MockCluster();
        AtomicLong now = new AtomicLong(0);
        CircuitBreaker breaker = new GracePeriodCircuitBreaker(now::get, Duration.ofSeconds(1), Duration.ofMinutes(10));
        HttpRequestStrategy strategy = new HttpRequestStrategy(FeedClientBuilder.create(URI.create("https://dummy.com:123"))
                                                                                .setRetryStrategy(new FeedClient.RetryStrategy() {
                                                                                    @Override public boolean retry(FeedClient.OperationType type) { return type == FeedClient.OperationType.PUT; }
                                                                                    @Override public int retries() { return 1; }
                                                                                })
                                                                                .setCircuitBreaker(breaker)
                                                                                .setConnectionsPerEndpoint(1)
                                                                                .setMaxStreamPerConnection(minStreams),
                                                               new BenchmarkingCluster(cluster));
        OperationStats initial = strategy.stats();

        DocumentId id1 = DocumentId.of("ns", "type", "1");
        DocumentId id2 = DocumentId.of("ns", "type", "2");
        HttpRequest request = new HttpRequest("POST", "/", null, null);

        // Runtime exception is not retried.
        cluster.expect((__, vessel) -> vessel.completeExceptionally(new RuntimeException("boom")));
        ExecutionException expected = assertThrows(ExecutionException.class,
                                                   () -> strategy.enqueue(id1, request).get());
        assertTrue(expected.getCause() instanceof FeedException);
        assertEquals("java.lang.RuntimeException: boom", expected.getCause().getMessage());
        assertEquals(1, strategy.stats().requests());

        // IOException is retried.
        cluster.expect((__, vessel) -> vessel.completeExceptionally(new IOException("retry me")));
        expected = assertThrows(ExecutionException.class,
                                () -> strategy.enqueue(id1, request).get());
        assertEquals("retry me", expected.getCause().getCause().getMessage());
        assertEquals(3, strategy.stats().requests());

        // Successful response is returned
        HttpResponse success = HttpResponse.of(200, null);
        cluster.expect((__, vessel) -> vessel.complete(success));
        assertEquals(success, strategy.enqueue(id1, request).get());
        assertEquals(4, strategy.stats().requests());

        // Throttled requests are retried. Concurrent operations to same ID (only) are serialised.
        now.set(2000);
        HttpResponse throttled = HttpResponse.of(429, null);
        AtomicInteger count = new AtomicInteger(3);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<CompletableFuture<HttpResponse>> completion = new AtomicReference<>();
        cluster.expect((req, vessel) -> {
            if (req == request) {
                if (count.decrementAndGet() > 0)
                    vessel.complete(throttled);
                else {
                    completion.set(vessel);
                    latch.countDown();
                }
            }
            else vessel.complete(success);
        });
        CompletableFuture<HttpResponse> delayed = strategy.enqueue(id1, request);
        CompletableFuture<HttpResponse> serialised = strategy.enqueue(id1, new HttpRequest("PUT", "/", null, null));
        assertEquals(success, strategy.enqueue(id2, new HttpRequest("DELETE", "/", null, null)).get());
        latch.await();
        assertEquals(8, strategy.stats().requests()); // 3 attempts at throttled and one at id2.
        now.set(4000);
        assertEquals(CLOSED, breaker.state()); // Circuit not broken due to throttled requests.
        completion.get().complete(success);
        assertEquals(success, delayed.get());
        assertEquals(success, serialised.get());

        // Some error responses are retried.
        HttpResponse serverError = HttpResponse.of(500, null);
        cluster.expect((__, vessel) -> vessel.complete(serverError));
        assertEquals(serverError, strategy.enqueue(id1, request).get());
        assertEquals(11, strategy.stats().requests());
        assertEquals(CLOSED, breaker.state()); // Circuit not broken due to throttled requests.

        // Error responses are not retried when not of appropriate type.
        cluster.expect((__, vessel) -> vessel.complete(serverError));
        assertEquals(serverError, strategy.enqueue(id1, new HttpRequest("PUT", "/", null, null)).get());
        assertEquals(12, strategy.stats().requests());

        // Some error responses are not retried.
        HttpResponse badRequest = HttpResponse.of(400, null);
        cluster.expect((__, vessel) -> vessel.complete(badRequest));
        assertEquals(badRequest, strategy.enqueue(id1, request).get());
        assertEquals(13, strategy.stats().requests());

        // Circuit breaker opens some time after starting to fail.
        now.set(6000);
        assertEquals(HALF_OPEN, breaker.state()); // Circuit broken due to failed requests.
        now.set(605000);
        assertEquals(OPEN, breaker.state()); // Circuit broken due to failed requests.

        Map<Integer, Long> codes = new HashMap<>();
        codes.put(200, 4L);
        codes.put(400, 1L);
        codes.put(429, 2L);
        codes.put(500, 3L);
        assertEquals(codes, strategy.stats().responsesByCode());
        assertEquals(3, strategy.stats().exceptions());

        assertEquals(strategy.stats(), strategy.stats().since(initial));
        assertEquals(0, strategy.stats().since(strategy.stats()).averageLatencyMillis());
        assertEquals(0, strategy.stats().since(strategy.stats()).requests());
        assertEquals(0, strategy.stats().since(strategy.stats()).bytesSent());
    }

    @Test
    void testShutdown() {
        MockCluster cluster = new MockCluster();
        AtomicLong now = new AtomicLong(0);
        CircuitBreaker breaker = new GracePeriodCircuitBreaker(now::get, Duration.ofSeconds(1), Duration.ofMinutes(10));
        HttpRequestStrategy strategy = new HttpRequestStrategy(FeedClientBuilder.create(URI.create("https://dummy.com:123"))
                                                                                .setRetryStrategy(new FeedClient.RetryStrategy() {
                                                                                    @Override public int retries() { return 1; }
                                                                                })
                                                                                .setCircuitBreaker(breaker)
                                                                                .setConnectionsPerEndpoint(1),
                                                               new BenchmarkingCluster(cluster));

        DocumentId id1 = DocumentId.of("ns", "type", "1");
        DocumentId id2 = DocumentId.of("ns", "type", "2");
        DocumentId id3 = DocumentId.of("ns", "type", "3");
        DocumentId id4 = DocumentId.of("ns", "type", "4");
        HttpRequest failing = new HttpRequest("POST", "/", null, null);
        HttpRequest request = new HttpRequest("POST", "/", null, null);
        HttpRequest blocking = new HttpRequest("POST", "/", null, null);

        // Enqueue some operations to the same id, which are serialised, and then shut down while operations are in flight.
        Phaser phaser = new Phaser(2);
        Phaser blocker = new Phaser(2);
        AtomicReference<CompletableFuture<HttpResponse>> completion = new AtomicReference<>();
        cluster.expect((req, vessel) -> {
            if (req == blocking) {
                phaser.arriveAndAwaitAdvance();  // Synchronise with tst main thread, and then ...
                blocker.arriveAndAwaitAdvance(); // ... block dispatch thread, so we get something in the queue.
                throw new RuntimeException("armageddon"); // Dispatch thread should die, tearing down everything.
            }
            else if (req == failing) {
                phaser.arriveAndAwaitAdvance();  // Let test thread enqueue more ops before failing (and retrying) this.
                vessel.completeExceptionally(new IOException("failed"));
            }
            else phaser.arriveAndAwaitAdvance(); // Don't complete from mock cluster, but require destruction to do this.
        });
        CompletableFuture<HttpResponse> inflight = strategy.enqueue(id1, request);
        CompletableFuture<HttpResponse> serialised1 = strategy.enqueue(id1, request);
        CompletableFuture<HttpResponse> serialised2 = strategy.enqueue(id1, request);
        CompletableFuture<HttpResponse> failed = strategy.enqueue(id2, failing);
        CompletableFuture<HttpResponse> blocked = strategy.enqueue(id3, blocking);
        CompletableFuture<HttpResponse> delayed = strategy.enqueue(id4, request);
        phaser.arriveAndAwaitAdvance(); // inflight completes dispatch, but causes no response.
        phaser.arriveAndAwaitAdvance(); // failed completes dispatch, and a retry is enqueued.
        phaser.arriveAndAwaitAdvance(); // blocked starts dispatch, and hangs, blocking dispatch thread.

        // Current state: inflight is "inflight to cluster", serialised1/2 are waiting completion of it;
        //                blocked is blocking dispatch, delayed is enqueued, waiting for dispatch;
        //                failed has a partial result, and has a retry in the dispatch queue.
        assertFalse(inflight.isDone());
        assertFalse(serialised1.isDone());
        assertFalse(serialised2.isDone());
        assertFalse(failed.isDone());
        assertFalse(blocked.isDone());
        assertFalse(delayed.isDone());

        // Kill dispatch thread, and see that all enqueued operations, and new ones, complete.
        blocker.arriveAndAwaitAdvance();
        assertEquals("ai.vespa.feed.client.FeedException: Operation aborted",
                     assertThrows(ExecutionException.class, inflight::get).getMessage());
        assertEquals("ai.vespa.feed.client.FeedException: Operation aborted",
                     assertThrows(ExecutionException.class, serialised1::get).getMessage());
        assertEquals("ai.vespa.feed.client.FeedException: Operation aborted",
                     assertThrows(ExecutionException.class, serialised2::get).getMessage());
        assertEquals("ai.vespa.feed.client.FeedException: Operation aborted",
                     assertThrows(ExecutionException.class, blocked::get).getMessage());
        assertEquals("ai.vespa.feed.client.FeedException: Operation aborted",
                     assertThrows(ExecutionException.class, delayed::get).getMessage());
        assertEquals("ai.vespa.feed.client.FeedException: java.io.IOException: failed",
                     assertThrows(ExecutionException.class, failed::get).getMessage());
        assertEquals("ai.vespa.feed.client.FeedException: Operation aborted",
                     assertThrows(ExecutionException.class, strategy.enqueue(id1, request)::get).getMessage());
    }

    static class MockCluster implements Cluster {

        final AtomicReference<BiConsumer<HttpRequest, CompletableFuture<HttpResponse>>> dispatch = new AtomicReference<>();

        void expect(BiConsumer<HttpRequest, CompletableFuture<HttpResponse>> expected) {
            dispatch.set(expected);
        }

        @Override
        public void dispatch(HttpRequest request, CompletableFuture<HttpResponse> vessel) {
            dispatch.get().accept(request, vessel);
        }

    }

}
