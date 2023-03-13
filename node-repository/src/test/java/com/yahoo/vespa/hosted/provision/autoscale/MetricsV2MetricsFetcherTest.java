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
            assertFalse(values.get(0).getSecond().inService());
            assertTrue(values.get(0).getSecond().stable());
        }

        { // read response 2 when unstable
            httpClient.cannedResponse = cannedResponseForApplication2;
            try (Mutex lock = tester.nodeRepository().applications().lock(application1)) {
                tester.nodeRepository().nodes().write(tester.nodeRepository().nodes().list(Node.State.active).owner(application2)
                        .first().get().retire(tester.clock().instant()), lock);
            }
            List<Pair<String, NodeMetricSnapshot>> values = new ArrayList<>(fetcher.fetchMetrics(application2).get().nodeMetrics());
            assertFalse(values.get(0).getSecond().stable());
        }

        {
            httpClient.cannedResponse = cannedResponseForApplication3;
            List<Pair<String, NodeMetricSnapshot>> values = new ArrayList<>(fetcher.fetchMetrics(application2).get().nodeMetrics());
            assertEquals("http://host-3.yahoo.com:4080/metrics/v2/values?consumer=autoscaling",
                         httpClient.requestsReceived.get(1));
            assertEquals(1, values.size());
            assertEquals("host-3.yahoo.com", values.get(0).getFirst());
            assertEquals(0.13, values.get(0).getSecond().load().cpu(), delta);
            assertEquals(0.9375, values.get(0).getSecond().load().memory(), delta);
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
            """
                    {
                      "nodes": [
                        {
                          "hostname": "host-1.yahoo.com",
                          "role": "role0",
                          "node": {
                            "timestamp": 1234,
                            "metrics": [
                              {
                                "values": {
                                  "cpu.util": 16.2,
                                  "mem.util": 23.1,
                                  "disk.util": 82
                                },
                                "dimensions": {
                                  "state": "active"
                                }
                              }
                            ]
                          }
                        },
                        {
                          "hostname": "host-2.yahoo.com",
                          "role": "role1",
                          "node": {
                            "timestamp": 1200,
                            "metrics": [
                              {
                                "values": {
                                  "mem.util": 30,
                                  "disk.util": 40
                                },
                                "dimensions": {
                                  "state": "active"
                                }
                              }
                            ]
                          },
                          "services": [
                            {
                              "name": "searchnode",
                              "timestamp": 1234,
                              "status": {
                                "code": "up"
                              },
                              "metrics": [
                                {
                                  "values": {
                                    "content.proton.documentdb.matching.queries.rate": 20.5
                                  },
                                  "dimensions": {
                                    "documentType": "music"
                                  }
                                },
                                {
                                  "values": {
                                    "content.proton.resource_usage.memory.average": 0.35,
                                    "content.proton.resource_usage.disk.average": 0.45
                                  },
                                  "dimensions": {
                                  }
                                },
                                {
                                  "values": {
                                    "content.proton.documentdb.matching.queries.rate": 13.5
                                  },
                                  "dimensions": {
                                    "documentType": "books"
                                  }
                                },
                                {
                                  "values": {
                                    "queries.rate": 11.0
                                  },
                                  "dimensions": {
                                  }
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                    """;

    final String cannedResponseForApplication2 =
            """
                    {
                      "nodes": [
                        {
                          "hostname": "host-3.yahoo.com",
                          "role": "role0",
                          "node": {
                            "timestamp": 1300,
                            "metrics": [
                              {
                                "values": {
                                  "cpu.util": 10,
                                  "gpu.util": 8,
                                  "mem.util": 15,
                                  "gpu.memory.used": 0,
                                  "gpu.memory.total": 8,
                                  "disk.util": 20,
                                  "application_generation.last": 3,
                                  "in_service.last": 0
                                },
                                "dimensions": {
                                  "state": "active"
                                }
                              }
                            ]
                          }
                        }
                      ]
                    }
                    """;

    final String cannedResponseForApplication3 =
            """
                    {
                      "nodes": [
                        {
                          "hostname": "host-3.yahoo.com",
                          "role": "role0",
                          "node": {
                            "timestamp": 1300,
                            "metrics": [
                              {
                                "values": {
                                  "cpu.util": 10,
                                  "gpu.util": 13,
                                  "mem.util": 15,
                                  "gpu.memory.used": 7.5,
                                  "gpu.memory.total": 8,
                                  "disk.util": 20,
                                  "application_generation.last": 3,
                                  "in_service.last": 0
                                },
                                "dimensions": {
                                  "state": "active"
                                }
                              }
                            ]
                          }
                        }
                      ]
                    }
                    """;

}
