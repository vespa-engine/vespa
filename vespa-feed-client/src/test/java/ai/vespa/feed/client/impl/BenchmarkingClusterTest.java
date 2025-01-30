package ai.vespa.feed.client.impl;

import ai.vespa.feed.client.HttpResponse;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BenchmarkingClusterTest {

    @Test
    void average_operation_latency_includes_retries() {
        var nowNanos = new AtomicLong(0);

        var delegate = mock(Cluster.class);

        doAnswer(invocation -> processRequest(nowNanos, invocation, 429, 1_000_000_000L))
                .doAnswer(invocation -> processRequest(nowNanos, invocation, 429, 5_000_000_000L))
                .doAnswer(invocation -> processRequest(nowNanos, invocation, 200, 2_000_000_000L))
                .doAnswer(invocation -> processRequest(nowNanos, invocation, 200, 4_000_000_000L))
                .doAnswer(invocation -> processRequest(nowNanos, invocation, 200, 7_000_000_000L))
                .when(delegate).dispatch(any(), any());

        var throttler = mock(Throttler.class);
        when(throttler.targetInflight()).thenReturn(0L);

        var cluster = new BenchmarkingCluster(delegate, throttler, nowNanos::get);
        var req1 = new HttpRequest("POST", "/", "", null, null, Duration.ofSeconds(180), nowNanos::get);
        dispatchRequest(cluster, req1);
        dispatchRequest(cluster, req1);
        dispatchRequest(cluster, req1);
        var req2 = new HttpRequest("PUT", "/", "", null, null, Duration.ofSeconds(180), nowNanos::get);
        dispatchRequest(cluster, req2);
        var req3 = new HttpRequest("DELETE", "/", "", null, null, Duration.ofSeconds(180), nowNanos::get);
        dispatchRequest(cluster, req3);

        var stats = cluster.stats();
        assertEquals(6333, stats.operationAverageLatencyMillis());
        assertEquals(4000, stats.operationMinLatencyMillis());
        assertEquals(8000, stats.operationMaxLatencyMillis());

        cluster.resetStats();
        stats = cluster.stats();
        assertEquals(-1, stats.operationAverageLatencyMillis());
        assertEquals(-1, stats.operationMinLatencyMillis());
        assertEquals(-1, stats.operationMaxLatencyMillis());
    }

    private static void dispatchRequest(BenchmarkingCluster cluster, HttpRequest request) {
        cluster.dispatchInternal(request, new CompletableFuture<>()).join();
    }

    private static Void processRequest(AtomicLong nanoClock, InvocationOnMock invocation, int statusCode, long latencyNanos) {
        CompletableFuture<HttpResponse> vessel = invocation.getArgument(1);
        nanoClock.addAndGet(latencyNanos);
        vessel.complete(HttpResponse.of(statusCode, "body".getBytes(StandardCharsets.UTF_8)));
        return null;
    }
}
