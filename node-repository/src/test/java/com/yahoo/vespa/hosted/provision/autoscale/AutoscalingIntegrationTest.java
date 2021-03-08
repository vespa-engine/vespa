// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.test.ManualClock;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.testutils.OrchestratorMock;
import org.junit.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class AutoscalingIntegrationTest {

    @Test
    public void testComponentIntegration() {
        NodeResources nodes = new NodeResources(1, 10, 100, 1);
        NodeResources hosts = new NodeResources(3, 20, 200, 1);

        AutoscalingTester tester = new AutoscalingTester(hosts);
        MetricsV2MetricsFetcher fetcher = new MetricsV2MetricsFetcher(tester.nodeRepository(),
                                                                      new OrchestratorMock(),
                                                                      new MockHttpClient(tester.clock()));
        Autoscaler autoscaler = new Autoscaler(tester.nodeMetricsDb(), tester.nodeRepository());

        ApplicationId application1 = tester.applicationId("test1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "test");
        Set<String> hostnames = tester.deploy(application1, cluster1, 2, 1, nodes)
                                      .stream().map(HostSpec::hostname)
                                      .collect(Collectors.toSet());
        // The metrics response (below) hardcodes these hostnames:
        assertEquals(Set.of("node-1-of-host-1.yahoo.com", "node-1-of-host-10.yahoo.com"), hostnames);

        for (int i = 0; i < 1000; i++) {
            tester.clock().advance(Duration.ofSeconds(10));
            fetcher.fetchMetrics(application1).whenComplete((r, e) -> tester.nodeMetricsDb().addNodeMetrics(r.nodeMetrics()));
            tester.clock().advance(Duration.ofSeconds(10));
            tester.nodeMetricsDb().gc();
        }

        ClusterResources min = new ClusterResources(2, 1, nodes);
        ClusterResources max = new ClusterResources(2, 1, nodes);

        Application application = tester.nodeRepository().applications().get(application1).orElse(Application.empty(application1))
                                        .withCluster(cluster1.id(), false, min, max);
        try (Mutex lock = tester.nodeRepository().nodes().lock(application1)) {
            tester.nodeRepository().applications().put(application, lock);
        }
        var scaledResources = autoscaler.suggest(application, application.clusters().get(cluster1.id()),
                                                 tester.nodeRepository().nodes().list().owner(application1));
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
                "      \"hostname\": \"node-1-of-host-1.yahoo.com\",\n" +
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
                "      \"hostname\": \"node-1-of-host-10.yahoo.com\",\n" +
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
