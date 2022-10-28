// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.collections.Pair;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.OrchestratorMock;
import com.yahoo.vespa.applicationmodel.HostName;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class MetricsV2MetricsFetcherTest {

    private static final double delta = 0.00000001;

    @Test
    public void testMetricsFetch() throws Exception {
        NodeResources resources = new NodeResources(1, 10, 100, 1);
        ProvisioningTester tester = new ProvisioningTester.Builder().build();
        OrchestratorMock orchestrator = new OrchestratorMock();
        MockHttpClient httpClient = new MockHttpClient();
        MetricsV2MetricsFetcher fetcher = new MetricsV2MetricsFetcher(tester.nodeRepository(), orchestrator, httpClient);

        tester.makeReadyNodes(4, resources); // Creates (in order) host-1.yahoo.com, host-2.yahoo.com, host-3.yahoo.com, host-4.yahoo.com
        tester.activateTenantHosts();

        ApplicationId application1 = ProvisioningTester.applicationId();
        ApplicationId application2 = ProvisioningTester.applicationId();
        tester.deploy(application1, Capacity.from(new ClusterResources(2, 1, resources))); // host-1.yahoo.com, host-2.yahoo.com
        tester.deploy(application2, Capacity.from(new ClusterResources(2, 1, resources))); // host-4.yahoo.com, host-3.yahoo.com

        orchestrator.suspend(new HostName("host-4.yahoo.com"));

        {
            httpClient.cannedResponse = cannedResponseForApplication1;
            List<Pair<String, NodeMetricSnapshot>> values = new ArrayList<>(fetcher.fetchMetrics(application1).get().nodeMetrics());
            assertEquals("http://host-1.yahoo.com:4080/metrics/v2/values?consumer=autoscaling",
                         httpClient.requestsReceived.get(0));
            assertEquals(2, values.size());

            assertEquals("host-1.yahoo.com", values.get(0).getFirst());
            assertEquals(0.162, values.get(0).getSecond().load().cpu(), delta);
            assertEquals(0.231, values.get(0).getSecond().load().memory(), delta);
            assertEquals(0.820, values.get(0).getSecond().load().disk(), delta);

            assertEquals("host-2.yahoo.com", values.get(1).getFirst());
            assertEquals(0.0,  values.get(1).getSecond().load().cpu(), delta);
            assertEquals(0.35, values.get(1).getSecond().load().memory(), delta);
            assertEquals(0.45,  values.get(1).getSecond().load().disk(), delta);
            assertEquals(45.0, values.get(1).getSecond().queryRate(), delta);
        }

        {
            httpClient.cannedResponse = cannedResponseForApplication2;
            List<Pair<String, NodeMetricSnapshot>> values = new ArrayList<>(fetcher.fetchMetrics(application2).get().nodeMetrics());
            assertEquals("http://host-3.yahoo.com:4080/metrics/v2/values?consumer=autoscaling",
                         httpClient.requestsReceived.get(1));
            assertEquals(1, values.size());
            assertEquals("host-3.yahoo.com", values.get(0).getFirst());
            assertEquals(0.10, values.get(0).getSecond().load().cpu(), delta);
            assertEquals(0.15, values.get(0).getSecond().load().memory(), delta);
            assertEquals(0.20, values.get(0).getSecond().load().disk(), delta);
            assertEquals(3, values.get(0).getSecond().generation(), delta);
            assertTrue(values.get(0).getSecond().stable());
        }

        {
            httpClient.cannedResponse = cannedResponseForApplication2;
            try (Mutex lock = tester.nodeRepository().applications().lock(application1)) {
                tester.nodeRepository().nodes().write(tester.nodeRepository().nodes().list(Node.State.active).owner(application2)
                        .first().get().retire(tester.clock().instant()), lock);
            }
            List<Pair<String, NodeMetricSnapshot>> values = new ArrayList<>(fetcher.fetchMetrics(application2).get().nodeMetrics());
            assertFalse(values.get(0).getSecond().stable());
        }
    }

    private static class MockHttpClient implements MetricsV2MetricsFetcher.AsyncHttpClient {

        List<String> requestsReceived = new ArrayList<>();

        String cannedResponse = null;

        @Override
        public CompletableFuture<String> get(String url) {
            requestsReceived.add(url);
            return CompletableFuture.completedFuture(cannedResponse);
        }

        @Override
        public void close() { }

    }

    final String cannedResponseForApplication1 =
            "{\n" +
            "  \"nodes\": [\n" +
            "    {\n" +
            "      \"hostname\": \"host-1.yahoo.com\",\n" +
            "      \"role\": \"role0\",\n" +
            "      \"node\": {\n" +
            "        \"timestamp\": 1234,\n" +
            "        \"metrics\": [\n" +
            "          {\n" +
            "            \"values\": {\n" +
            "              \"cpu.util\": 16.2,\n" +
            "              \"mem.util\": 23.1,\n" +
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
            "      \"hostname\": \"host-2.yahoo.com\",\n" +
            "      \"role\": \"role1\",\n" +
            "      \"node\": {\n" +
            "        \"timestamp\": 1200,\n" +
            "        \"metrics\": [\n" +
            "          {\n" +
            "            \"values\": {\n" +
            "              \"mem.util\": 30,\n" +
            "              \"disk.util\": 40\n" +
            "            },\n" +
            "            \"dimensions\": {\n" +
            "              \"state\": \"active\"\n" +
            "            }\n" +
            "          }\n" +
            "        ]\n" +
            "      },\n" +
            "      \"services\": [\n" +
            "        {\n" +
            "          \"name\": \"searchnode\",\n" +
            "          \"timestamp\": 1234,\n" +
            "          \"status\": {\n" +
            "            \"code\": \"up\"\n" +
            "          },\n" +
            "          \"metrics\": [\n" +
            "            {\n" +
            "              \"values\": {\n" +
            "                \"content.proton.documentdb.matching.queries.rate\": 20.5\n" +
            "              },\n" +
            "              \"dimensions\": {\n" +
            "                \"documentType\": \"music\"\n" +
            "              }\n" +
            "            },\n" +
            "            {\n" +
            "              \"values\": {\n" +
            "                \"content.proton.resource_usage.memory.average\": 0.35,\n" +
            "                \"content.proton.resource_usage.disk.average\": 0.45\n" +
            "              },\n" +
            "              \"dimensions\": {\n" +
            "              }\n" +
            "            },\n" +
            "            {\n" +
            "              \"values\": {\n" +
            "                \"content.proton.documentdb.matching.queries.rate\": 13.5\n" +
            "              },\n" +
            "              \"dimensions\": {\n" +
            "                \"documentType\": \"books\"\n" +
            "              }\n" +
            "            },\n" +
            "            {\n" +
            "              \"values\": {\n" +
            "                \"queries.rate\": 11.0\n" +
            "              },\n" +
            "              \"dimensions\": {\n" +
            "              }\n" +
            "            }\n" +
            "          ]\n" +
            "        }\n" +
            "      ]\n" +
            "    }\n" +
            "  ]\n" +
            "}\n";

    final String cannedResponseForApplication2 =
            "{\n" +
            "  \"nodes\": [\n" +
            "    {\n" +
            "      \"hostname\": \"host-3.yahoo.com\",\n" +
            "      \"role\": \"role0\",\n" +
            "      \"node\": {\n" +
            "        \"timestamp\": 1300,\n" +
            "        \"metrics\": [\n" +
            "          {\n" +
            "            \"values\": {\n" +
            "              \"cpu.util\": 10,\n" +
            "              \"mem.util\": 15,\n" +
            "              \"disk.util\": 20,\n" +
            "              \"application_generation\": 3\n" +
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

}
