// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.impl;

import ai.vespa.feed.client.HttpResponse;
import ai.vespa.feed.client.OperationStats;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Objects.requireNonNull;

public class BenchmarkingCluster implements Cluster {

    private final Cluster delegate;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "cluster-stats-collector");
        thread.setDaemon(true);
        return thread;
    });

    private static class ResponseSpecificStats {
        long count = 0;
        long totalLatencyMillis = 0;
        long minLatencyMillis = Long.MAX_VALUE;
        long maxLatencyMillis = 0;
        long bytesReceived = 0;
    }

    private final AtomicLong timeOfFirstDispatch = new AtomicLong(0);
    private final AtomicLong requests = new AtomicLong();
    private final Throttler throttler;
    private final Map<Integer, ResponseSpecificStats> statsByCode = new HashMap<>(10);
    private long results = 0;
    private long exceptions = 0;
    private long bytesSent = 0;

    BenchmarkingCluster(Cluster delegate, Throttler throttler) {
        this.delegate = requireNonNull(delegate);
        this.throttler = throttler;
    }

    @Override
    public void dispatch(HttpRequest request, CompletableFuture<HttpResponse> vessel) {
        requests.incrementAndGet();
        long startNanos = System.nanoTime();
        timeOfFirstDispatch.compareAndSet(0, startNanos);
        delegate.dispatch(request, vessel);
        vessel.whenCompleteAsync((response, thrown) -> {
                    results++;
                    if (thrown == null) {
                        var stats = statsByCode.computeIfAbsent(response.code(), __ -> new ResponseSpecificStats());
                        long latency = (System.nanoTime() - startNanos) / 1_000_000;
                        stats.count++;
                        stats.totalLatencyMillis += latency;
                        stats.minLatencyMillis = Math.min(stats.minLatencyMillis, latency);
                        stats.maxLatencyMillis = Math.max(stats.maxLatencyMillis, latency);
                        stats.bytesReceived += response.body() == null ? 0 : response.body().length;
                        bytesSent += request.body() == null ? 0 : request.body().length;
                    }
                    else
                        exceptions++;
                },
                executor);
    }

    @Override
    public OperationStats stats() {
        try {
            try {
                return executor.submit(this::getStats).get();
            }
            catch (RejectedExecutionException ignored) {
                executor.awaitTermination(10, TimeUnit.SECONDS);
                return getStats();
            }
        }
        catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private OperationStats getStats() {
        var requests = this.requests.get();
        var duration = (System.nanoTime() - timeOfFirstDispatch.get()) * 1e-9;
        var statsByCode = new HashMap<Integer, OperationStats.Response>();
        this.statsByCode.forEach((code, stats) -> {
            statsByCode.put(code, new OperationStats.Response(
                    stats.count, stats.totalLatencyMillis, stats.count == 0 ? -1 : stats.totalLatencyMillis / stats.count,
                    stats.count == 0 ? -1 : stats.minLatencyMillis, stats.count == 0 ? -1 : stats.maxLatencyMillis,
                    stats.bytesReceived, stats.count / duration
            ));
        });
        return new OperationStats(
                duration, requests, exceptions, requests - results, throttler.targetInflight(), bytesSent, statsByCode);
    }

    @Override
    public void close() {
        delegate.close();
        Instant doom = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(doom) && getStats().inflight() != 0)
            try  {
                Thread.sleep(10);
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        executor.shutdown();
    }

}
