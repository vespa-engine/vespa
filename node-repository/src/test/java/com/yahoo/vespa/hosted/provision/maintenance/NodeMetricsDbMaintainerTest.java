// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.autoscale.NodeMetricSnapshot;
import com.yahoo.vespa.hosted.provision.autoscale.MetricsDb;
import com.yahoo.vespa.hosted.provision.autoscale.MetricsV2MetricsFetcher;
import com.yahoo.vespa.hosted.provision.autoscale.NodeTimeseries;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.OrchestratorMock;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 */
public class NodeMetricsDbMaintainerTest {

    @Test
    public void testNodeMetricsDbMaintainer() {
        NodeResources resources = new NodeResources(1, 10, 100, 1);
        ProvisioningTester tester = new ProvisioningTester.Builder().build();
        tester.clock().setInstant(Instant.ofEpochMilli(1400));
        tester.makeReadyNodes(2, resources);
        tester.activateTenantHosts();
        tester.deploy(ProvisioningTester.applicationId("test"),
                      Capacity.from(new ClusterResources(2, 1, resources)));
        OrchestratorMock orchestrator = new OrchestratorMock();
        MockHttpClient httpClient = new MockHttpClient();
        MetricsV2MetricsFetcher fetcher = new MetricsV2MetricsFetcher(tester.nodeRepository(), orchestrator, httpClient);
        MetricsDb db = MetricsDb.createTestInstance(tester.nodeRepository());
        NodeMetricsDbMaintainer maintainer = new NodeMetricsDbMaintainer(tester.nodeRepository(),
                                                                         fetcher,
                                                                         db,
                                                                         Duration.ofHours(1),
                                                                         new TestMetric());
        assertTrue(maintainer.maintain());
        List<NodeTimeseries> timeseriesList = db.getNodeTimeseries(Duration.ofDays(1),
                                                                   Set.of("host-1.yahoo.com", "host-2.yahoo.com"));
        assertEquals(2, timeseriesList.size());
        List<NodeMetricSnapshot> allSnapshots = timeseriesList.stream()
                                                              .flatMap(timeseries -> timeseries.asList().stream())
                                                              .collect(Collectors.toList());
        assertTrue(allSnapshots.stream().anyMatch(snapshot -> snapshot.inService()));
        assertTrue(allSnapshots.stream().anyMatch(snapshot -> ! snapshot.inService()));
    }

    private static class MockHttpClient implements MetricsV2MetricsFetcher.AsyncHttpClient {

        final String cannedResponse =
                "{\n" +
                "  \"nodes\": [\n" +
                "    {\n" +
                "      \"hostname\": \"host-1.yahoo.com\",\n" +
                "      \"role\": \"role0\",\n" +
                "      \"node\": {\n" +
                "        \"timestamp\": 1300,\n" +
                "        \"metrics\": [\n" +
                "          {\n" +
                "            \"values\": {\n" +
                "              \"cpu.util\": 14,\n" + // this value asserted on above
                "              \"mem_total.util\": 15,\n" +
                "              \"disk.util\": 20,\n" +
                "              \"application_generation\": 3,\n" +
                "              \"in_service\": 1\n" +
                "            },\n" +
                "            \"dimensions\": {\n" +
                "              \"state\": \"active\"\n" +
                "            }\n" +
                "          }\n" +
                "        ]\n" +
                "      }\n" +
                "    },\n" +
                "    {\n" +
                "      \"hostname\": \"host-2.yahoo.com\",\n" +
                "      \"role\": \"role0\",\n" +
                "      \"node\": {\n" +
                "        \"timestamp\": 1300,\n" +
                "        \"metrics\": [\n" +
                "          {\n" +
                "            \"values\": {\n" +
                "              \"cpu.util\": 1,\n" +
                "              \"mem_total.util\": 2,\n" +
                "              \"disk.util\": 3,\n" +
                "              \"application_generation\": 3,\n" +
                "              \"in_service\": 0\n" +
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
            return CompletableFuture.completedFuture(cannedResponse);
        }

        @Override
        public void close() { }

    }

}
