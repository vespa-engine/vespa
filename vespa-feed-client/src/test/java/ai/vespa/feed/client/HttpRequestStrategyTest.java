// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpRequestStrategyTest {

    @Test
    void testConcurrency() {
        int documents = 1 << 16;
        SimpleHttpRequest request = new SimpleHttpRequest("PUT", "/");
        SimpleHttpResponse response = new SimpleHttpResponse(200);
        response.setBody("{}".getBytes(UTF_8), ContentType.APPLICATION_JSON);
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        Cluster cluster = new BenchmarkingCluster((__, vessel) -> executor.schedule(() -> vessel.complete(response), 100, TimeUnit.MILLISECONDS));

        HttpRequestStrategy strategy = new HttpRequestStrategy(FeedClientBuilder.create(URI.create("https://dummy.com:123"))
                                                                                .setConnectionsPerEndpoint(1 << 12)
                                                                                .setMaxStreamPerConnection(1 << 4),
                                                               cluster);
        long startNanos = System.nanoTime();
        for (int i = 0; i < documents; i++)
            strategy.enqueue(DocumentId.of("ns", "type", Integer.toString(i)), request);

        strategy.await();
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
    void test() {
    }

}
