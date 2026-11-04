// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.impl;

import ai.vespa.feed.client.DocumentId;
import ai.vespa.feed.client.FeedClient;
import ai.vespa.feed.client.HttpResponse;
import ai.vespa.feed.client.OperationParameters;
import ai.vespa.feed.client.OperationStats;
import ai.vespa.feed.client.Result;
import ai.vespa.feed.client.ShutdownHandle;
import ai.vespa.feed.client.FeedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static ai.vespa.feed.client.impl.ShutdownTestSupport.awaitLatch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpFeedClientShutdownTest {
    @Test
    void gracefulCloseWaitsForAdmittedConstruction() throws Exception {
        // Removing admission ownership must permit close to destroy the strategy too early.
        CountDownLatch constructing = new CountDownLatch(1);
        CountDownLatch resumeConstruction = new CountDownLatch(1);
        AtomicReference<Thread> submittingThread = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ImmediateStrategy strategy = new ImmediateStrategy();
        FeedClientBuilderImpl builder = constructionPausedOn(submittingThread, constructing, resumeConstruction);
        FeedClient client = new HttpFeedClient(builder, DryrunCluster::new, strategy);
        AtomicReference<CompletableFuture<Result>> result = new AtomicReference<>();
        Thread sender = new Thread(() -> {
            submittingThread.set(Thread.currentThread());
            try { result.set(client.remove(DocumentId.of("ns", "type", "1"), OperationParameters.empty())); }
            catch (Throwable t) { failure.set(t); }
        });
        Thread closer = new Thread(() -> {
            try { client.close(); }
            catch (Throwable t) { failure.set(t); }
        });
        boolean destroyedBeforeAdmissionFinished;
        sender.start();
        try {
            assertTrue(constructing.await(10, TimeUnit.SECONDS), "submission reached construction");
            closer.start();
            // The fixture never blocks close itself. Wait for close either to finish (the bug)
            // or to suspend waiting for client-owned work. The clock latch creates the race;
            // this bound only detects a broken fixture or a stalled test process.
            long limit = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (closer.isAlive() && closer.getState() != Thread.State.WAITING &&
                   closer.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < limit)
                Thread.yield();
            assertTrue(!closer.isAlive() || closer.getState() == Thread.State.WAITING ||
                       closer.getState() == Thread.State.TIMED_WAITING, "close reached a stable wait or returned");
            destroyedBeforeAdmissionFinished = strategy.destroyed.get();
        } finally {
            resumeConstruction.countDown();
            sender.join(10000);
            if (closer.getState() != Thread.State.NEW) closer.join(10000);
        }
        assertFalse(sender.isAlive(), "sender stopped");
        assertFalse(closer.isAlive(), "closer stopped");
        assertFalse(destroyedBeforeAdmissionFinished, "graceful close destroyed strategy before admitted construction finished");
        assertNull(failure.get());
        assertEquals(Result.Type.success, result.get().get(10, TimeUnit.SECONDS).type());
    }

    @Test
    void abortDoesNotCertifyTerminationWhileConstructionIsOwned() throws Exception {
        // Removing the post-cleanup ownership wait would certify termination too early.
        CountDownLatch constructing = new CountDownLatch(1);
        CountDownLatch resumeConstruction = new CountDownLatch(1);
        CountDownLatch destroyed = new CountDownLatch(1);
        AtomicReference<Thread> submittingThread = new AtomicReference<>();
        AtomicReference<Throwable> submissionFailure = new AtomicReference<>();
        ImmediateStrategy strategy = new ImmediateStrategy() {
            @Override public void destroy() { super.destroy(); destroyed.countDown(); }
        };
        FeedClientBuilderImpl builder = constructionPausedOn(submittingThread, constructing, resumeConstruction);
        FeedClient client = new HttpFeedClient(builder, DryrunCluster::new, strategy);
        Thread sender = new Thread(() -> {
            submittingThread.set(Thread.currentThread());
            try { client.remove(DocumentId.of("ns", "type", "1"), OperationParameters.empty()).join(); }
            catch (Throwable failure) { submissionFailure.set(failure); }
        });
        sender.start();
        try {
            assertTrue(constructing.await(10, TimeUnit.SECONDS));
            ShutdownHandle handle = client.closeAsync(false);
            assertTrue(destroyed.await(10, TimeUnit.SECONDS), "abort starts cleanup before construction resumes");
            assertThrows(TimeoutException.class, () -> handle.await(Duration.ofMillis(100)),
                         "termination cannot precede admitted construction finishing");
            assertEquals(1, handle.snapshot().pendingAdmissions());
        } finally {
            resumeConstruction.countDown();
            sender.join(10000);
            client.closeAsync(false).await(Duration.ofSeconds(10));
        }
        assertFalse(sender.isAlive());
        assertTrue(submissionFailure.get() != null, "aborted submission fails");
        assertEquals(0, client.termination().snapshot().outstandingOperations());
    }

    @Test
    void observationDoesNotInitiateAndTimeoutDoesNotClose() throws Exception {
        FeedClient client = newClient(new ImmediateStrategy());
        ShutdownHandle handle = assertDoesNotThrow(client::termination, "built-in client supports lifecycle observation");
        assertEquals(ShutdownHandle.Phase.OPEN, handle.snapshot().phase());
        assertThrows(TimeoutException.class, () -> handle.await(Duration.ZERO));
        assertEquals(Result.Type.success, client.remove(DocumentId.of("ns", "type", "1"), OperationParameters.empty()).get().type());
        client.closeAsync(true).await(Duration.ofSeconds(10));
        assertEquals(ShutdownHandle.Phase.TERMINATED, handle.snapshot().phase());
    }

    @Test
    void repeatedCloseSharesCleanupAndAllowsEscalation() throws Exception {
        CountDownLatch cleanupEntered = new CountDownLatch(1);
        CountDownLatch resumeCleanup = new CountDownLatch(1);
        AtomicInteger cleanupCalls = new AtomicInteger();
        ImmediateStrategy strategy = new ImmediateStrategy() {
            @Override public void destroy() {
                cleanupCalls.incrementAndGet();
                cleanupEntered.countDown();
                awaitLatch(resumeCleanup);
                super.destroy();
            }
        };
        FeedClient client = newClient(strategy);
        ShutdownHandle handle = assertDoesNotThrow(() -> client.closeAsync(true), "built-in client supports asynchronous shutdown");
        try {
            assertTrue(cleanupEntered.await(10, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> client.remove(DocumentId.of("ns", "type", "1"), OperationParameters.empty()));
            client.closeAsync(false);
            client.closeAsync(true);
            assertEquals(ShutdownHandle.Phase.ABORTING, handle.snapshot().phase());
            assertThrows(TimeoutException.class, () -> handle.await(Duration.ZERO));
        } finally { resumeCleanup.countDown(); }
        handle.await(Duration.ofSeconds(10));
        client.close();
        assertEquals(1, cleanupCalls.get());
    }

    @Test
    void cleanupCannotWaitForItsOwnTermination() throws Exception {
        // Without the cleanup-context guard, awaiting the shared outcome deadlocks its owner.
        AtomicReference<FeedClient> clientReference = new AtomicReference<>();
        AtomicReference<Throwable> observed = new AtomicReference<>();
        FeedClient client = newClient(new ImmediateStrategy() {
            @Override public void destroy() {
                try { clientReference.get().termination().await(Duration.ZERO); }
                catch (Throwable failure) { observed.set(failure); }
                super.destroy();
            }
        });
        clientReference.set(client);
        client.closeAsync(true).await(Duration.ofSeconds(10));
        assertInstanceOf(IllegalStateException.class, observed.get(),
                   "cleanup must reject self-wait, observed: " + observed.get());
        assertEquals(ShutdownHandle.Phase.TERMINATED, client.termination().snapshot().phase());
    }

    @Test
    void cancelledPublicResultDoesNotDischargeTransportOwnership() throws Exception {
        // Pruning solely by public future completion would let cancellation erase transport work.
        CompletableFuture<HttpResponse> response = new CompletableFuture<>();
        FeedClient client = newClient(new ImmediateStrategy() {
            @Override public CompletableFuture<HttpResponse> enqueue(DocumentId id, HttpRequest request) { return response; }
        });
        var result = client.remove(DocumentId.of("ns", "type", "1"), OperationParameters.empty());
        assertTrue(result.cancel(false));
        ShutdownHandle handle = client.closeAsync(true);
        try {
            assertThrows(TimeoutException.class, () -> handle.await(Duration.ofMillis(100)));
            assertEquals(1, handle.snapshot().outstandingOperations());
        } finally {
            response.complete(HttpResponse.of(200, "{}".getBytes(UTF_8)));
            handle.await(Duration.ofSeconds(10));
        }
        assertTrue(result.isCancelled());
        assertEquals(0, handle.snapshot().outstandingOperations());
    }

    @Test
    void constructionFailureDischargesAdmission() throws Exception {
        // Omitting exceptional publication or registration leaves a permanently owned operation.
        AtomicBoolean failConstruction = new AtomicBoolean();
        RuntimeException injected = new IllegalArgumentException("injected clock failure");
        FeedClientBuilderImpl builder = new FeedClientBuilderImpl(List.of(URI.create("https://dummy:123")))
                .setNanoClock(() -> {
                    if (failConstruction.get()) throw injected;
                    return 0;
                });
        FeedClient client = new HttpFeedClient(builder, DryrunCluster::new, new ImmediateStrategy());
        failConstruction.set(true);
        assertSame(injected, assertThrows(IllegalArgumentException.class,
                () -> client.remove(DocumentId.of("ns", "type", "1"), OperationParameters.empty())));
        assertEquals(0, client.termination().snapshot().pendingAdmissions());
        assertEquals(0, client.termination().snapshot().outstandingOperations());
        client.closeAsync(true).await(Duration.ofSeconds(10));
    }

    @Test
    void interruptedObserverDoesNotInterruptSharedShutdown() throws Exception {
        // Caller interruption must not cancel the shared outcome or the cleanup owner.
        CountDownLatch cleanupEntered = new CountDownLatch(1);
        CountDownLatch releaseCleanup = new CountDownLatch(1);
        FeedClient client = newClient(new ImmediateStrategy() {
            @Override public void destroy() {
                cleanupEntered.countDown();
                awaitLatch(releaseCleanup);
                super.destroy();
            }
        });
        ShutdownHandle handle = client.closeAsync(true);
        AtomicReference<Throwable> interruption = new AtomicReference<>();
        Thread observer = new Thread(() -> {
            try { handle.await(); }
            catch (Throwable failure) { interruption.set(failure); }
        });
        try {
            assertTrue(cleanupEntered.await(10, TimeUnit.SECONDS));
            observer.start();
            observer.interrupt();
            observer.join(10000);
            assertFalse(observer.isAlive());
            assertInstanceOf(InterruptedException.class, interruption.get());
            assertEquals(ShutdownHandle.Phase.DRAINING, handle.snapshot().phase());
        } finally {
            releaseCleanup.countDown();
            handle.await(Duration.ofSeconds(10));
        }
        assertEquals(ShutdownHandle.Phase.TERMINATED, handle.snapshot().phase());
    }

    @ParameterizedTest(name = "progress wait from dispatcher={0}")
    @ValueSource(booleans = {true, false})
    void strategyProgressThreadCannotWaitForShutdown(boolean fromDispatch) throws Exception {
        // Without identity-based strategy guards, transport/policy callbacks can self-deadlock.
        AtomicReference<FeedClient> clientReference = new AtomicReference<>();
        AtomicReference<Throwable> observed = new AtomicReference<>();
        Runnable observeShutdown = () -> {
            try { clientReference.get().closeAsync(true).await(Duration.ZERO); }
            catch (Throwable failure) { observed.set(failure); }
        };
        FeedClientBuilderImpl builder = new FeedClientBuilderImpl(List.of(URI.create("https://dummy:123")))
                .setCircuitBreaker(new FeedClient.CircuitBreaker() {
                    @Override public State state() { return State.CLOSED; }
                    @Override public void success() { if (!fromDispatch) observeShutdown.run(); }
                });
        Cluster cluster = (request, response) -> {
            if (fromDispatch) observeShutdown.run();
            response.complete(HttpResponse.of(200, "{}".getBytes(UTF_8)));
        };
        HttpRequestStrategy strategy = new HttpRequestStrategy(builder, () -> cluster);
        FeedClient client = new HttpFeedClient(builder, DryrunCluster::new, strategy);
        clientReference.set(client);
        try {
            assertEquals(Result.Type.success,
                         client.remove(DocumentId.of("ns", "type", "1"), OperationParameters.empty())
                               .get(10, TimeUnit.SECONDS).type());
            assertInstanceOf(IllegalStateException.class, observed.get(),
                       "strategy progress thread must reject self-wait, observed: " + observed.get());
        } finally { client.closeAsync(false).await(Duration.ofSeconds(10)); }
    }

    @Test
    void successfulTerminationWaitsForDispatcherExit() throws Exception {
        // Resource cleanup alone cannot certify termination while the dispatcher still runs.
        AtomicReference<Thread> dispatcher = new AtomicReference<>();
        AtomicBoolean pause = new AtomicBoolean();
        CountDownLatch dispatcherPaused = new CountDownLatch(1);
        CountDownLatch resumeDispatcher = new CountDownLatch(1);
        FeedClientBuilderImpl builder = new FeedClientBuilderImpl(List.of(URI.create("https://dummy:123")))
                .setCircuitBreaker(() -> {
                    if (Thread.currentThread() == dispatcher.get() && pause.compareAndSet(true, false)) {
                        dispatcherPaused.countDown();
                        awaitLatch(resumeDispatcher);
                    }
                    return FeedClient.CircuitBreaker.State.CLOSED;
                });
        Cluster cluster = (request, response) -> {
            dispatcher.set(Thread.currentThread());
            pause.set(true);
            response.complete(HttpResponse.of(200, "{}".getBytes(UTF_8)));
        };
        HttpRequestStrategy strategy = new HttpRequestStrategy(builder, () -> cluster);
        FeedClient client = new HttpFeedClient(builder, DryrunCluster::new, strategy);
        try {
            client.remove(DocumentId.of("ns", "type", "1"), OperationParameters.empty()).get(10, TimeUnit.SECONDS);
            assertTrue(dispatcherPaused.await(10, TimeUnit.SECONDS));
            ShutdownHandle handle = client.closeAsync(true);
            assertThrows(TimeoutException.class, () -> handle.await(Duration.ofMillis(100)),
                         "successful termination must wait for owned dispatcher exit");
        } finally {
            resumeDispatcher.countDown();
            client.closeAsync(false).await(Duration.ofSeconds(10));
        }
        assertFalse(dispatcher.get().isAlive(), "dispatcher released at successful termination");
    }

    @Test
    void cleanupFailureRemainsObservableToLateWaiters() throws Exception {
        RuntimeException cleanupFailure = new IllegalStateException("injected cleanup failure");
        FeedClient client = newClient(new ImmediateStrategy() {
            @Override public void destroy() { throw cleanupFailure; }
        });
        ShutdownHandle handle = assertDoesNotThrow(() -> client.closeAsync(true), "built-in client supports asynchronous shutdown");
        FeedException failure = assertThrows(FeedException.class, () -> handle.await(Duration.ofSeconds(10)));
        assertSame(cleanupFailure, failure.getCause());
        assertSame(cleanupFailure, client.termination().snapshot().failure().orElseThrow());
        assertEquals(ShutdownHandle.Phase.FAILED, handle.snapshot().phase());
        assertThrows(FeedException.class, client::close);
    }

    @Test
    void responseMappingRemainsOwnedUntilPublicResultIsPublished() throws Exception {
        CompletableFuture<HttpResponse> response = new CompletableFuture<>();
        CountDownLatch mappingEntered = new CountDownLatch(1);
        CountDownLatch resumeMapping = new CountDownLatch(1);
        ImmediateStrategy strategy = new ImmediateStrategy() {
            @Override public CompletableFuture<HttpResponse> enqueue(DocumentId id, HttpRequest request) { return response; }
            @Override public void await() { response.join(); }
        };
        FeedClient client = newClient(strategy);
        CompletableFuture<Result> result = client.remove(DocumentId.of("ns", "type", "1"), OperationParameters.empty());
        Thread completing = new Thread(() -> response.complete(new HttpResponse() {
            @Override public int code() { return 200; }
            @Override public byte[] body() {
                mappingEntered.countDown();
                awaitLatch(resumeMapping);
                return "{\"message\":\"accepted\"}".getBytes(UTF_8);
            }
        }));
        Thread closer = new Thread(client::close);
        boolean prematureDestroy;
        completing.start();
        try {
            assertTrue(mappingEntered.await(10, TimeUnit.SECONDS));
            assertFalse(result.isDone());
            assertEquals(1, client.termination().snapshot().outstandingOperations(), "mapped outcome still owned");
            closer.start();
            long limit = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (closer.isAlive() && closer.getState() != Thread.State.WAITING &&
                   closer.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < limit)
                Thread.yield();
            prematureDestroy = strategy.destroyed.get();
        } finally {
            resumeMapping.countDown();
            completing.join(10000);
            if (closer.getState() != Thread.State.NEW) closer.join(10000);
            client.close();
        }
        assertFalse(prematureDestroy, "public result mapping must remain owned");
        assertEquals(Result.Type.success, result.get(10, TimeUnit.SECONDS).type());
        assertEquals(0, client.termination().snapshot().outstandingOperations());
    }

    @Test
    void callbackWaitIsRejectedWhileShutdownContinues() throws Exception {
        CompletableFuture<HttpResponse> response = new CompletableFuture<>();
        CountDownLatch cleanupEntered = new CountDownLatch(1);
        CountDownLatch resumeCleanup = new CountDownLatch(1);
        FeedClient client = newClient(new ImmediateStrategy() {
            @Override public CompletableFuture<HttpResponse> enqueue(DocumentId id, HttpRequest request) { return response; }
            @Override public void destroy() { cleanupEntered.countDown(); awaitLatch(resumeCleanup); }
        });
        CompletableFuture<Result> result = client.remove(DocumentId.of("ns", "type", "1"), OperationParameters.empty());
        CompletableFuture<Void> callback = result.thenRun(() ->
                assertThrows(IllegalStateException.class, client::close,
                             "callback must not block waiting for its own lifecycle"));
        Thread completing = new Thread(() -> response.complete(HttpResponse.of(200, "{}".getBytes(UTF_8))));
        completing.start();
        try {
            assertTrue(cleanupEntered.await(10, TimeUnit.SECONDS));
            callback.get(10, TimeUnit.SECONDS);
        } finally {
            resumeCleanup.countDown();
            completing.join(10000);
            client.termination().await(Duration.ofSeconds(10));
        }
        assertFalse(completing.isAlive());
    }

    @Test
    void abortEscalatesWhileRealStrategyIsDraining() throws Exception {
        CountDownLatch dispatched = new CountDownLatch(1);
        CountDownLatch draining = new CountDownLatch(1);
        AtomicReference<CompletableFuture<HttpResponse>> transport = new AtomicReference<>();
        FeedClientBuilderImpl builder = new FeedClientBuilderImpl(List.of(URI.create("https://dummy:123")));
        HttpRequestStrategy strategy = new HttpRequestStrategy(builder, () -> new Cluster() {
            @Override public void dispatch(HttpRequest request, CompletableFuture<HttpResponse> vessel) {
                transport.set(vessel);
                dispatched.countDown();
            }
            @Override public void close() {
                if (transport.get() != null) transport.get().completeExceptionally(new FeedException("transport stopped"));
            }
        }) {
            @Override public void await(java.util.function.BooleanSupplier shouldDrain) {
                draining.countDown();
                super.await(shouldDrain);
            }
        };
        FeedClient client = new HttpFeedClient(builder, DryrunCluster::new, strategy);
        CompletableFuture<Result> result = client.remove(DocumentId.of("ns", "type", "1"), OperationParameters.empty());
        try {
            assertTrue(dispatched.await(10, TimeUnit.SECONDS));
            ShutdownHandle handle = client.closeAsync(true);
            assertTrue(draining.await(10, TimeUnit.SECONDS));
            client.closeAsync(false);
            assertDoesNotThrow(() -> handle.await(Duration.ofSeconds(1)), "abort must unblock graceful draining");
            assertTrue(result.isCompletedExceptionally());
        } finally {
            if (transport.get() != null) transport.get().complete(HttpResponse.of(200, "{}".getBytes(UTF_8)));
            client.close();
        }
    }

    private static FeedClient newClient(RequestStrategy strategy) throws Exception {
        return new HttpFeedClient(new FeedClientBuilderImpl(List.of(URI.create("https://dummy:123"))), DryrunCluster::new, strategy);
    }

    private static FeedClientBuilderImpl constructionPausedOn(AtomicReference<Thread> submittingThread,
                                                               CountDownLatch constructing,
                                                               CountDownLatch resumeConstruction) {
        AtomicBoolean pause = new AtomicBoolean(true);
        return new FeedClientBuilderImpl(List.of(URI.create("https://dummy:123"))).setNanoClock(() -> {
            if (Thread.currentThread() == submittingThread.get() && pause.compareAndSet(true, false)) {
                constructing.countDown();
                awaitLatch(resumeConstruction);
            }
            return 0;
        });
    }

    private static class ImmediateStrategy implements RequestStrategy {
        private final AtomicBoolean destroyed = new AtomicBoolean();
        @Override public void await() { }
        @Override public void destroy() { destroyed.set(true); }
        @Override public OperationStats stats() { throw new UnsupportedOperationException(); }
        @Override public void resetStats() { }
        @Override public FeedClient.CircuitBreaker.State circuitBreakerState() { return FeedClient.CircuitBreaker.State.CLOSED; }
        @Override public CompletableFuture<HttpResponse> enqueue(DocumentId id, HttpRequest request) {
            if (destroyed.get()) return CompletableFuture.failedFuture(new IllegalStateException("strategy destroyed"));
            return CompletableFuture.completedFuture(HttpResponse.of(200, "{\"message\":\"accepted\"}".getBytes(UTF_8)));
        }
    }
}
