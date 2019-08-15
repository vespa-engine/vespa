package com.yahoo.vespa.hosted.controller.api.integration.metrics;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ClusterMetrics;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConfigServerMetricsServiceTest {

    private final ApplicationId applicationId = new ApplicationId.Builder()
                .tenant("foo")
                .applicationName("bar")
                .instanceName("default")
                .build();

    private final ZoneId zoneId = ZoneId.from("prod", "us-west-1");

    private ConfigServer configServer;
    private ConfigServerMetricsService service;

    @Before
    public void before() {
        configServer = Mockito.mock(ConfigServer.class);
        service = new ConfigServerMetricsService(configServer);
    }

    @Test
    public void test_returning_metrics() {
        //
        // Wire up the test
        //
        var deploymentId = new DeploymentId(applicationId, zoneId);

        var clusterMetrics1 = new ClusterMetrics("niceCluster", ClusterMetrics.ClusterType.container) {{
            addMetric("queriesPerSecond", 23.0);
            addMetric("queryLatency", 1337.0);
        }};

        var clusterMetrics2 = new ClusterMetrics("alsoNiceCluster", ClusterMetrics.ClusterType.container) {{
            addMetric("queriesPerSecond", 11.0);
            addMetric("queryLatency", 12.0);
        }};

        var response = List.of(clusterMetrics1, clusterMetrics2);

        Mockito.when(configServer.getMetrics(deploymentId)).thenReturn(response);

        //
        // Now we can actually test stuff :(
        //
        var deploymentMetrics = service.getDeploymentMetrics(applicationId, zoneId);

        assertEquals(23.0 + 11.0, deploymentMetrics.queriesPerSecond(), 0.001);
        assertEquals(908.323, deploymentMetrics.queryLatencyMillis(), 0.001);
        assertEquals(0, deploymentMetrics.documentCount());
        assertEquals(0.0, deploymentMetrics.writeLatencyMillis(), 0.001);
        assertEquals(0.0, deploymentMetrics.writesPerSecond(), 0.001);
    }

    @Test
    public void test_not_implemented_application_metrics() {
        var applicationMetrics = service.getApplicationMetrics(applicationId);
        assertEquals(0.0, applicationMetrics.queryServiceQuality(), 0.001);
        assertEquals(0.0, applicationMetrics.writeServiceQuality(), 0.001);
    }

    @Test
    public void test_not_implemented_metrics() {
        assertTrue(service.getRotationStatus("foo").isEmpty());
        assertTrue(service.getSystemMetrics(applicationId, zoneId).isEmpty());
    }
}
