// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    private final AtomicLong requests = new AtomicLong();
    private long results = 0;
    private long responses = 0;
    private final long[] responsesByCode = new long[600];
    private long exceptions = 0;
    private long totalLatencyMillis = 0;
    private long minLatencyMillis = Long.MAX_VALUE;
    private long maxLatencyMillis = 0;
    private long bytesSent = 0;
    private long bytesReceived = 0;

    public BenchmarkingCluster(Cluster delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public void dispatch(HttpRequest request, CompletableFuture<HttpResponse> vessel) {
        requests.incrementAndGet();
        long startNanos = System.nanoTime();
        delegate.dispatch(request, vessel);
        vessel.whenCompleteAsync((response, thrown) -> {
                                     results++;
                                     if (thrown == null) {
                                         responses++;
                                         responsesByCode[response.code()]++;
                                         long latency = (System.nanoTime() - startNanos) / 1_000_000;
                                         totalLatencyMillis += latency;
                                         minLatencyMillis = Math.min(minLatencyMillis, latency);
                                         maxLatencyMillis = Math.max(maxLatencyMillis, latency);
                                         bytesSent += request.body() == null ? 0 : request.body().length;
                                         bytesReceived += response.body() == null ? 0 : response.body().length;
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
        long requests = this.requests.get();

        Map<Integer, Long> responses = new HashMap<>();
        for (int code = 0; code < responsesByCode.length; code++)
            if (responsesByCode[code] > 0)
                responses.put(code, responsesByCode[code]);

        return new OperationStats(requests,
                                  responses,
                                  exceptions,
                                  requests - results,
                                  this.responses == 0 ? -1 : totalLatencyMillis / this.responses,
                                  this.responses == 0 ? -1 : minLatencyMillis,
                                  this.responses == 0 ? -1 : maxLatencyMillis,
                                  bytesSent,
                                  bytesReceived);
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
