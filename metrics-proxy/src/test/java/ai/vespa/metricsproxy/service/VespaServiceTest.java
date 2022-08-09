// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.metric.Metric;
import ai.vespa.metricsproxy.metric.Metrics;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static ai.vespa.metricsproxy.TestUtil.getFileContents;
import static ai.vespa.metricsproxy.metric.model.MetricId.toMetricId;
import static ai.vespa.metricsproxy.service.RemoteMetricsFetcher.METRICS_PATH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Unknown
 */
public class VespaServiceTest {
    private MockHttpServer httpServer;
    private static final String response;

    static {
        response = getFileContents("metrics-state.json");
        HttpMetricFetcher.CONNECTION_TIMEOUT = 60000; // 60 secs in unit tests
    }

    @Before
    public void setupHTTPServer() {
        try {
            httpServer = new MockHttpServer(response, METRICS_PATH);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testService() {
        VespaService service = new VespaService("container", "container/container.0");
        assertEquals("container", service.getServiceName());
        assertEquals("container", service.getInstanceName());
        assertEquals(-1, service.getPid());
        assertEquals("container/container.0", service.getConfigId());


        service = VespaService.create("container2", "container/container.0", -1);
        assertEquals("container", service.getServiceName());
        assertEquals("container2", service.getInstanceName());
        assertEquals(-1, service.getPid());
        assertEquals("container/container.0", service.getConfigId());
    }

    @Test
    // TODO: Make it possible to test this without running a HTTP server to create the response
    public void testMetricsFetching() {
        VespaService service = VespaService.create("service1", "id", httpServer.port());
        assertEquals(28, getMetric("queries.count", service.getMetrics()).getValue().intValue());

        // Shutdown server and check that no metrics are returned (should use empty metrics
        // when unable to fetch new metrics)
        shutdown();

        assertTrue(service.getMetrics().list().isEmpty());
    }

    @After
    public void shutdown() {
        this.httpServer.close();
    }

    public Metric getMetric(String metric, Metrics metrics) {
        for (Metric m: metrics.list()) {
            if (m.getName().equals(toMetricId(metric)))
                return m;
        }
        return null;
    }

}
