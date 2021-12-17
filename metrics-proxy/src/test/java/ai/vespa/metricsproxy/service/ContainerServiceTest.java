// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.metric.Metric;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static ai.vespa.metricsproxy.TestUtil.getFileContents;
import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;
import static ai.vespa.metricsproxy.metric.model.MetricId.toMetricId;
import static ai.vespa.metricsproxy.service.RemoteMetricsFetcher.METRICS_PATH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Unknown
 */
public class ContainerServiceTest {

    private MockHttpServer httpServer;

    @BeforeClass
    public static void init() {
        HttpMetricFetcher.CONNECTION_TIMEOUT = 60000; // 60 secs in unit tests
    }

    @Before
    public void setupHTTPServer() {
        try {
            String response = getFileContents("metrics-container-state-multi-chain.json");
            httpServer = new MockHttpServer(response, METRICS_PATH);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testMultipleQueryDimensions() {
        int count = 0;
        VespaService service = VespaService.create("service1", "id", httpServer.port());
        for (Metric m : service.getMetrics().list()) {
            if (m.getName().equals(toMetricId("queries.rate"))) {
                count++;
                System.out.println("Name: " + m.getName() + " value: " + m.getValue());
                if (m.getDimensions().get(toDimensionId("chain")).equals("asvBlendingResult")) {
                    assertEquals(26.4, m.getValue());
                } else if (m.getDimensions().get(toDimensionId("chain")).equals("blendingResult")) {
                    assertEquals(0.36666666666666664, m.getValue());
                } else {
                    assertTrue("Unknown unknown chain", false);
                }
            }
        }
        assertEquals(2, count);
    }

    @After
    public void shutdown() {
        this.httpServer.close();
    }
}
