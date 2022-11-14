// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.provision.testutils.OrchestratorMock;
import org.junit.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class AutoscalingIntegrationTest {

    @Test
    public void testComponentIntegration() {
        var fixture = AutoscalingTester.fixture()
                                       .hostCount(20)
                                       .hostFlavors(new NodeResources(3, 20, 200, 1))
                                       .initialResources(Optional.of(new ClusterResources(2, 1,
                                                                                          new NodeResources(1, 10, 100, 1))))
                                       .build();
        MetricsV2MetricsFetcher fetcher = new MetricsV2MetricsFetcher(fixture.tester().nodeRepository(),
                                                                      new OrchestratorMock(),
                                                                      new MockHttpClient(fixture.tester().clock()));
        Autoscaler autoscaler = new Autoscaler(fixture.tester().nodeRepository());

        // The metrics response (below) hardcodes these hostnames:
        assertEquals(Set.of("host-1-1.yahoo.com", "host-10-1.yahoo.com"), fixture.nodes().hostnames());

        for (int i = 0; i < 1000; i++) {
            fixture.tester().clock().advance(Duration.ofSeconds(10));
            fetcher.fetchMetrics(fixture.applicationId()).whenComplete((r, e) -> fixture.tester().nodeMetricsDb().addNodeMetrics(r.nodeMetrics()));
            fixture.tester().clock().advance(Duration.ofSeconds(10));
            fixture.tester().nodeMetricsDb().gc();
        }

        var scaledResources = autoscaler.suggest(fixture.application(), fixture.cluster(), fixture.nodes());
        assertTrue(scaledResources.isPresent());
    }

    private static class MockHttpClient implements MetricsV2MetricsFetcher.AsyncHttpClient {

        private final ManualClock clock;

        public MockHttpClient(ManualClock clock) {
            this.clock = clock;
        }

        final String cannedResponse =
                "{\n" +
                "  \"nodes\": [\n" +
                "    {\n" +
                "      \"hostname\": \"host-1-1.yahoo.com\",\n" +
                "      \"role\": \"role0\",\n" +
                "      \"node\": {\n" +
                "        \"timestamp\": [now],\n" +
                "        \"metrics\": [\n" +
                "          {\n" +
                "            \"values\": {\n" +
                "              \"cpu.util\": 16.2,\n" +
                "              \"mem_total.util\": 23.1,\n" +
                "              \"disk.util\": 82\n" +
                "            },\n" +
                "            \"dimensions\": {\n" +
                "              \"state\": \"active\"\n" +
                "            }\n" +
                "          }\n" +
                "        ]\n" +
                "      }\n" +
                "    },\n" +
                "    {\n" +
                "      \"hostname\": \"host-10-1.yahoo.com\",\n" +
                "      \"role\": \"role1\",\n" +
                "      \"node\": {\n" +
                "        \"timestamp\": [now],\n" +
                "        \"metrics\": [\n" +
                "          {\n" +
                "            \"values\": {\n" +
                "              \"cpu.util\": 20,\n" +
                "              \"mem_total.util\": 23.1,\n" +
                "              \"disk.util\": 40\n" +
                "            },\n" +
                "            \"dimensions\": {\n" +
                "              \"state\": \"active\"\n" +
                "            }\n" +
                "          }\n" +
                "        ]\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}\n";

        @Override
        public CompletableFuture<String> get(String url) {
            return CompletableFuture.completedFuture(cannedResponse.replace("[now]",
                                                                            String.valueOf(clock.millis()))); }

        @Override
        public void close() { }

    }

}
