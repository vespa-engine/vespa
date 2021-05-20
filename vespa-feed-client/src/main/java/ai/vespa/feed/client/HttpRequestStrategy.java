// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Controls request execution and retries:
 * <ul>
 *     <li>Retry all IO exceptions; however</li>
 *     <li>abort everything if more than 10% of requests result in an exception for some time.</li>
 *     <li>Whenever throttled, limit inflight to 99% of current; and</li>
 *     <li>on every successful response, increase inflight limit by 0.1.</li>
 * </ul>
 *
 * @author jonmv
 */
class HttpRequestStrategy implements RequestStrategy<SimpleHttpResponse>, HttpRequestRetryStrategy {

    private final Map<DocumentId, CompletableFuture<SimpleHttpResponse>> byId = new ConcurrentHashMap<>();
    private final FeedClient.RetryStrategy wrapped;
    private final long maxInflight;
    private double targetInflight;
    private long inflight;
    private final AtomicReference<Double> errorRate;
    private final double errorThreshold;
    private final Lock lock;
    private final Condition available;

    HttpRequestStrategy(FeedClientBuilder builder) {
        this.wrapped = builder.retryStrategy;
        this.maxInflight = builder.maxConnections * (long) builder.maxStreamsPerConnection;
        this.targetInflight = maxInflight;
        this.inflight = 0;
        this.errorRate = new AtomicReference<>(0.0);
        this.errorThreshold = 0.1;
        this.lock = new ReentrantLock(true);
        this.available = lock.newCondition();
    }

    private double cycle() {
        return targetInflight; // TODO: tune this--could start way too high if limit is set too high.
    }

    @Override
    public boolean retryRequest(HttpRequest request, IOException exception, int execCount, HttpContext context) {
        if (errorRate.updateAndGet(rate -> rate + (1 - rate) / cycle()) > errorThreshold)
            return false;

        if (execCount > wrapped.retries())
            return false;

        switch (request.getMethod().toUpperCase()) {
            case "POST":   return wrapped.retry(FeedClient.OperationType.put);
            case "PUT":    return wrapped.retry(FeedClient.OperationType.update);
            case "DELETE": return wrapped.retry(FeedClient.OperationType.remove);
            default: throw new IllegalStateException("Unexpected HTTP method: " + request.getMethod());
        }
    }

    /**
     * Called when a response is successfully obtained.
     */
    void success() {
        errorRate.updateAndGet(rate -> rate - rate / cycle());
        lock.lock();
        targetInflight = Math.min(targetInflight + 0.1, maxInflight);
        lock.unlock();
    }

    @Override
    public boolean retryRequest(HttpResponse response, int execCount, HttpContext context) {
        if (response.getCode() == 429 || response.getCode() == 503) {
            lock.lock();
            targetInflight = Math.max(100, 99 * inflight / 100);
            lock.unlock();
            return true;
        }
        return false;
    }

    @Override
    public TimeValue getRetryInterval(HttpResponse response, int execCount, HttpContext context) {
        return TimeValue.ofMilliseconds(100);
    }

    void acquireSlot() {
        lock.lock();
        try {
            while (inflight >= targetInflight)
                available.awaitUninterruptibly();

            ++inflight;
        }
        finally {
            lock.unlock();
        }
    }

    void releaseSlot() {
        lock.lock();
        try {
            --inflight;

            if (inflight < targetInflight)
                available.signal();
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public boolean hasFailed() {
        return errorRate.get() > errorThreshold;
    }

    @Override
    public CompletableFuture<SimpleHttpResponse> enqueue(DocumentId documentId, Consumer<CompletableFuture<SimpleHttpResponse>> dispatch) {
        acquireSlot();

        Consumer<CompletableFuture<SimpleHttpResponse>> safeDispatch = vessel -> {
            try { dispatch.accept(vessel); }
            catch (Throwable t) { vessel.completeExceptionally(t); }
        };
        CompletableFuture<SimpleHttpResponse> vessel = new CompletableFuture<>();
        byId.compute(documentId, (id, previous) -> {
            if (previous == null) safeDispatch.accept(vessel);
            else previous.whenComplete((__, ___) -> safeDispatch.accept(vessel));
            return vessel;
        });

        return vessel.whenComplete((__, thrown) -> {
            releaseSlot();
            if (thrown == null)
                success();

            byId.compute(documentId, (id, current) -> current == vessel ? null : current);
        });
    }

}
