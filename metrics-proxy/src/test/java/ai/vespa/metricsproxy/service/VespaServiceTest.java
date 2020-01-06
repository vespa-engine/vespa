// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.metric.Metrics;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static ai.vespa.metricsproxy.TestUtil.getFileContents;
import static ai.vespa.metricsproxy.service.RemoteMetricsFetcher.METRICS_PATH;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

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
        VespaService service = new VespaService("qrserver", "container/qrserver.0");
        assertThat(service.getServiceName(), is("qrserver"));
        assertThat(service.getInstanceName(), is("qrserver"));
        assertThat(service.getPid(), is(-1));
        assertThat(service.getConfigId(), is("container/qrserver.0"));


        service = VespaService.create("qrserver2", "container/qrserver.0", -1);
        assertThat(service.getServiceName(), is("qrserver"));
        assertThat(service.getInstanceName(), is("qrserver2"));
        assertThat(service.getPid(), is(-1));
        assertThat(service.getConfigId(), is("container/qrserver.0"));
    }

    @Test
    // TODO: Make it possible to test this without running a HTTP server to create the response
    public void testMetricsFetching() {
        VespaService service = VespaService.create("service1", "id", httpServer.port());
        Metrics metrics = service.getMetrics();
        assertThat(metrics.getMetric("queries.count").getValue().intValue(), is(28));

        // Shutdown server and check that no metrics are returned (should use empty metrics
        // when unable to fetch new metrics)
        shutdown();

        metrics = service.getMetrics();
        assertThat(metrics.size(), is(0));
    }

    @After
    public void shutdown() {
        this.httpServer.close();
    }

}
