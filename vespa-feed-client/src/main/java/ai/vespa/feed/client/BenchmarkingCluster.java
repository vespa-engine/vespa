// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;

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
    private long minLatencyMillis = 0;
    private long maxLatencyMillis = 0;
    private long bytesSent = 0;
    private long bytesReceived = 0;

    public BenchmarkingCluster(Cluster delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public void dispatch(SimpleHttpRequest request, CompletableFuture<SimpleHttpResponse> vessel) {
        requests.incrementAndGet();
        long startMillis = System.currentTimeMillis();
        delegate.dispatch(request, vessel);
        vessel.whenCompleteAsync((response, thrown) -> {
                                     results++;
                                     if (thrown == null) {
                                         responses++;
                                         responsesByCode[response.getCode()]++;
                                         long latency = System.currentTimeMillis() - startMillis;
                                         totalLatencyMillis += latency;
                                         minLatencyMillis = Math.min(minLatencyMillis, latency);
                                         maxLatencyMillis = Math.max(maxLatencyMillis, latency);
                                         bytesSent += request.getBodyBytes() == null ? 0 : request.getBodyBytes().length;
                                         bytesReceived += response.getBodyBytes() == null ? 0 : response.getBodyBytes().length;
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
        catch (InterruptedException | ExecutionException ignored) {
            throw new RuntimeException(ignored);
        }
    }

    private OperationStats getStats() {
        Map<Integer, Long> responses = new HashMap<>();
        for (int code = 0; code < responsesByCode.length; code++)
            if (responsesByCode[code] > 0)
                responses.put(code, responsesByCode[code]);

        return new OperationStats(requests.get(),
                                  responses,
                                  exceptions,
                         requests.get() - results,
                         totalLatencyMillis / this.responses,
                                  minLatencyMillis,
                                  maxLatencyMillis,
                                  bytesSent,
                                  bytesReceived);
    }

    @Override
    public void close() {
        delegate.close();
        executor.shutdown();
    }

}
