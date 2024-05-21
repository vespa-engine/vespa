// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.impl;

import ai.vespa.feed.client.DocumentId;
import ai.vespa.feed.client.FeedClient;
import ai.vespa.feed.client.FeedClient.CircuitBreaker;
import ai.vespa.feed.client.FeedException;
import ai.vespa.feed.client.HttpResponse;
import ai.vespa.feed.client.OperationStats;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpRequestStrategyTest {

    @Test
    void testConcurrency() throws IOException {
        int documents = 1 << 16;
        HttpRequest request = new HttpRequest("PUT", "/", null, null, Duration.ofSeconds(1), () -> 0);
        HttpResponse response = HttpResponse.of(200, "{}".getBytes(UTF_8));
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        Cluster cluster = (__, vessel) -> executor.schedule(() -> vessel.complete(response), (int) (Math.random() * 2 * 10), TimeUnit.MILLISECONDS);

        HttpRequestStrategy strategy = new HttpRequestStrategy(new FeedClientBuilderImpl(List.of(URI.create("https://dummy.com:123")))
                                                                       .setConnectionsPerEndpoint(1 << 10)
                                                                       .setMaxStreamPerConnection(1 << 12),
                                                               () -> cluster);
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
        OperationStats stats = strategy.stats();
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

    @Test()
    void testRetries() throws ExecutionException, InterruptedException, IOException {
        int minStreams = 2; // Hard limit for minimum number of streams per connection.
        MockCluster cluster = new MockCluster();
        AtomicLong nowNanos = new AtomicLong(0);
        CircuitBreaker breaker = new GracePeriodCircuitBreaker(nowNanos::get, Duration.ofSeconds(1), Duration.ofMinutes(10));
        HttpRequestStrategy strategy = new HttpRequestStrategy(new FeedClientBuilderImpl(List.of(URI.create("https://dummy.com:123")))
                                                                       .setRetryStrategy(new FeedClient.RetryStrategy() {
                                                                           @Override public boolean retry(FeedClient.OperationType type) { return type == FeedClient.OperationType.PUT; }
                                                                           @Override public int retries() { return 1; }
                                                                       })
                                                                       .setCircuitBreaker(breaker)
                                                                       .setConnectionsPerEndpoint(1)
                                                                       .setMaxStreamPerConnection(minStreams),
                                                               () -> cluster);
        OperationStats initial = strategy.stats();

        DocumentId id1 = DocumentId.of("ns", "type", "1");
        DocumentId id2 = DocumentId.of("ns", "type", "2");
        HttpRequest request = new HttpRequest("POST", "/", null, null, Duration.ofSeconds(180), nowNanos::get);

        // Runtime exception is not retried.
        cluster.expect((__, vessel) -> vessel.completeExceptionally(new RuntimeException("boom")));
        ExecutionException expected = assertThrows(ExecutionException.class,
                                                   () -> strategy.enqueue(id1, request).get());
        assertInstanceOf(FeedException.class, expected.getCause());
        assertEquals("(id:ns:type::1) java.lang.RuntimeException: boom", expected.getCause().getMessage());
        assertEquals(1, strategy.stats().requests());

        // IOException is retried.
        cluster.expect((__, vessel) -> {
            nowNanos.addAndGet(200_000_000L); // Exceed grace period.
            vessel.completeExceptionally(new IOException("retry me"));
        });
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
        nowNanos.set(2_000_000_000L);
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
        CompletableFuture<HttpResponse> serialised = strategy.enqueue(id1, new HttpRequest("PUT", "/", null, null, Duration.ofSeconds(1), nowNanos::get));
        assertEquals(success, strategy.enqueue(id2, new HttpRequest("DELETE", "/", null, null, Duration.ofSeconds(1), nowNanos::get)).get());
        latch.await();
        assertEquals(8, strategy.stats().requests()); // 3 attempts at throttled and one at id2.
        nowNanos.set(4_000_000_000L);
        assertEquals(CLOSED, breaker.state()); // Circuit not broken due to throttled requests.
        completion.get().complete(success);
        assertEquals(success, delayed.get());
        assertEquals(success, serialised.get());

        // Some error responses are retried.
        HttpResponse serverError = HttpResponse.of(503, null);
        cluster.expect((__, vessel) -> {
            nowNanos.addAndGet(200_000_000L); // Exceed grace period.
            vessel.complete(serverError);
        });
        assertEquals(serverError, strategy.enqueue(id1, request).get());
        assertEquals(11, strategy.stats().requests());
        assertEquals(CLOSED, breaker.state()); // Circuit not broken due to throttled requests.

        // Error responses are not retried when not of appropriate type.
        cluster.expect((__, vessel) -> vessel.complete(serverError));
        assertEquals(serverError, strategy.enqueue(id1, new HttpRequest("PUT", "/", null, null, Duration.ofSeconds(1), nowNanos::get)).get());
        assertEquals(12, strategy.stats().requests());

        // Some error responses are not retried.
        HttpResponse badRequest = HttpResponse.of(400, null);
        cluster.expect((__, vessel) -> vessel.complete(badRequest));
        assertEquals(badRequest, strategy.enqueue(id1, request).get());
        assertEquals(13, strategy.stats().requests());


        // IOException is not retried past timeout.
        cluster.expect((__, vessel) -> {
            nowNanos.addAndGet(50_000_000L); // Exceed grace period after 2 attempts.
            vessel.completeExceptionally(new IOException("retry me"));
        });
        expected = assertThrows(ExecutionException.class,
                                () -> strategy.enqueue(id1, new HttpRequest("POST", "/", null, null, Duration.ofMillis(100), nowNanos::get)).get());
        assertEquals("retry me", expected.getCause().getCause().getMessage());
        assertEquals(15, strategy.stats().requests());


        // Circuit breaker opens some time after starting to fail.
        nowNanos.set(6_000_000_000L);
        assertEquals(HALF_OPEN, breaker.state()); // Circuit broken due to failed requests.
        nowNanos.set(605_000_000_000L);
        assertEquals(OPEN, breaker.state()); // Circuit broken due to failed requests.

        strategy.destroy();
        OperationStats stats = strategy.stats();
        Map<Integer, Long> codes = new HashMap<>();
        codes.put(200, 4L);
        codes.put(400, 1L);
        codes.put(429, 2L);
        codes.put(503, 3L);
        assertEquals(codes, stats.responsesByCode());
        assertEquals(5, stats.exceptions());

        assertEquals(stats, stats.since(initial));
        assertEquals(0, stats.since(stats).averageLatencyMillis());
        assertEquals(0, stats.since(stats).requests());
        assertEquals(0, stats.since(stats).bytesSent());
    }

    @Test
    void testResettingCluster() throws ExecutionException, InterruptedException, IOException {
        List<MockCluster> clusters = List.of(new MockCluster(), new MockCluster());
        AtomicLong now = new AtomicLong(0);
        CircuitBreaker breaker = new GracePeriodCircuitBreaker(now::get, Duration.ofSeconds(1), null);
        HttpRequestStrategy strategy = new HttpRequestStrategy(new FeedClientBuilderImpl(List.of(URI.create("https://dummy.com:123")))
                                                                       .setCircuitBreaker(breaker)
                                                                       .setConnectionsPerEndpoint(1),
                                                               clusters.iterator()::next);
        
        // First operation fails, second remains in flight, and third fails.
        clusters.get(0).expect((__, vessel) -> vessel.complete(HttpResponse.of(200, null)));
        strategy.enqueue(DocumentId.of("ns", "type", "1"), new HttpRequest("POST", "/", null, null, Duration.ofSeconds(1), now::get)).get();
        Exchanger<CompletableFuture<HttpResponse>> exchanger = new Exchanger<>();
        clusters.get(0).expect((__, vessel) -> {
            try { exchanger.exchange(vessel); } catch (InterruptedException e) { throw new RuntimeException(e); }
        });
        CompletableFuture<HttpResponse> secondResponse = strategy.enqueue(DocumentId.of("ns", "type", "2"), new HttpRequest("POST", "/", null, null, Duration.ofSeconds(1), now::get));
        CompletableFuture<HttpResponse> secondVessel = exchanger.exchange(null);
        clusters.get(0).expect((__, vessel) -> vessel.complete(HttpResponse.of(500, null)));
        strategy.enqueue(DocumentId.of("ns", "type", "3"), new HttpRequest("POST", "/", null, null, Duration.ofSeconds(1), now::get)).get();

        // Time advances, and the circuit breaker half-opens.
        assertEquals(CLOSED, breaker.state());
        now.addAndGet(2_000_000_000);
        assertEquals(HALF_OPEN, breaker.state());

        // It's indeterminate which cluster gets the next request, but the second should get the next one after that.
        clusters.get(0).expect((__, vessel) -> vessel.complete(HttpResponse.of(500, null)));
        clusters.get(1).expect((__, vessel) -> vessel.complete(HttpResponse.of(500, null)));
        assertEquals(500, strategy.enqueue(DocumentId.of("ns", "type", "4"), new HttpRequest("POST", "/", null, null, Duration.ofSeconds(1), now::get)).get().code());

        clusters.get(0).expect((__, vessel) -> vessel.completeExceptionally(new AssertionError("should not be called")));
        clusters.get(1).expect((__, vessel) -> vessel.complete(HttpResponse.of(200, null)));
        assertEquals(200, strategy.enqueue(DocumentId.of("ns", "type", "5"), new HttpRequest("POST", "/", null, null, Duration.ofSeconds(1), now::get)).get().code());

        assertFalse(clusters.get(0).closed.get());
        assertFalse(clusters.get(1).closed.get());
        secondVessel.complete(HttpResponse.of(504, null));
        assertEquals(504, secondResponse.get().code());
        assertTrue(clusters.get(0).closed.get());
        assertFalse(clusters.get(1).closed.get());
        strategy.await();
        strategy.destroy();
        assertTrue(clusters.get(1).closed.get());
    }

    @Test
    void testShutdown() throws IOException {
        MockCluster cluster = new MockCluster();
        AtomicLong nowNanos = new AtomicLong(0);
        CircuitBreaker breaker = new GracePeriodCircuitBreaker(nowNanos::get, Duration.ofSeconds(1), Duration.ofMinutes(10));
        HttpRequestStrategy strategy = new HttpRequestStrategy(new FeedClientBuilderImpl(List.of(URI.create("https://dummy.com:123")))
                                                                                .setRetryStrategy(new FeedClient.RetryStrategy() {
                                                                                    @Override public int retries() { return 1; }
                                                                                })
                                                                                .setCircuitBreaker(breaker)
                                                                                .setConnectionsPerEndpoint(3), // Must be >= 0.5x text ops.
                                                               () -> cluster);

        DocumentId id1 = DocumentId.of("ns", "type", "1");
        DocumentId id2 = DocumentId.of("ns", "type", "2");
        DocumentId id3 = DocumentId.of("ns", "type", "3");
        DocumentId id4 = DocumentId.of("ns", "type", "4");
        DocumentId id5 = DocumentId.of("ns", "type", "5");
        HttpRequest failing = new HttpRequest("POST", "/", null, null, Duration.ofSeconds(1), nowNanos::get);
        HttpRequest partial = new HttpRequest("POST", "/", null, null, Duration.ofSeconds(1), nowNanos::get);
        HttpRequest request = new HttpRequest("POST", "/", null, null, Duration.ofSeconds(1), nowNanos::get);
        HttpRequest blocking = new HttpRequest("POST", "/", null, null, Duration.ofSeconds(1), nowNanos::get);

        // Enqueue some operations to the same id, which are serialised, and then shut down while operations are in flight.
        Phaser phaser = new Phaser(2);
        Phaser blocker = new Phaser(2);
        cluster.expect((req, vessel) -> {
            if (req == blocking) {
                phaser.arriveAndAwaitAdvance();  // Synchronise with test main thread, and then ...
                blocker.arriveAndAwaitAdvance(); // ... block dispatch thread, so we get something in the queue.
                throw new RuntimeException("never"); // Dispatch thread should die, tearing down everything.
            }
            else if (req == partial) {
                phaser.arriveAndAwaitAdvance();  // Let test thread enqueue more ops before failing (and retrying) this.
                vessel.completeExceptionally(new IOException("failed"));
            }
            else if (req == failing) {
                System.err.println("failing");
                vessel.completeExceptionally(new RuntimeException("fatal"));
            }
        });
        // inflight completes dispatch, but causes no response.
        CompletableFuture<HttpResponse> inflight = strategy.enqueue(id1, request);
        // serialised 1 and 2 are waiting for the above inflight to complete.
        CompletableFuture<HttpResponse> serialised1 = strategy.enqueue(id1, request);
        CompletableFuture<HttpResponse> serialised2 = strategy.enqueue(id1, request);
        CompletableFuture<HttpResponse> retried = strategy.enqueue(id2, partial);
        CompletableFuture<HttpResponse> failed = strategy.enqueue(id3, failing);
        CompletableFuture<HttpResponse> blocked = strategy.enqueue(id4, blocking);
        CompletableFuture<HttpResponse> delayed = strategy.enqueue(id5, request);
        phaser.arriveAndAwaitAdvance(); // retried is allowed to dispatch, and will be retried async.
        // failed immediately fails, and lets us assert the above retry is indeed enqueued.
        assertEquals("ai.vespa.feed.client.FeedException: (id:ns:type::3) java.lang.RuntimeException: fatal",
                     assertThrows(ExecutionException.class, failed::get).getMessage());
        phaser.arriveAndAwaitAdvance(); // blocked starts dispatch, and hangs, blocking dispatch thread.

        // Current state: inflight is "inflight to cluster", serialised1/2 are waiting completion of it;
        //                blocked is blocking dispatch, delayed is enqueued, waiting for dispatch;
        //                failed has a partial result, and has a retry in the dispatch queue.
        assertFalse(inflight.isDone());
        assertFalse(serialised1.isDone());
        assertFalse(serialised2.isDone());
        assertTrue(failed.isDone());
        assertFalse(retried.isDone());
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
        assertEquals("ai.vespa.feed.client.FeedException: (id:ns:type::2) java.io.IOException: failed",
                     assertThrows(ExecutionException.class, retried::get).getMessage());
        assertEquals("ai.vespa.feed.client.FeedException: Operation aborted",
                     assertThrows(ExecutionException.class, strategy.enqueue(id1, request)::get).getMessage());
    }

    static class MockCluster implements Cluster {

        final AtomicReference<BiConsumer<HttpRequest, CompletableFuture<HttpResponse>>> dispatch = new AtomicReference<>();
        final AtomicBoolean closed = new AtomicBoolean(false);

        void expect(BiConsumer<HttpRequest, CompletableFuture<HttpResponse>> expected) {
            dispatch.set(expected);
        }

        @Override
        public void dispatch(HttpRequest request, CompletableFuture<HttpResponse> vessel) {
            dispatch.get().accept(request, vessel);
        }

        @Override
        public void close() {
            closed.set(true);
        }

    }

}
