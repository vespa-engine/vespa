// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.metric;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ClusterMetrics;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.integration.ConfigServerMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneRegistryMock;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author olaa
 */
public class ConfigServerMetricsTest {

    private final ApplicationId applicationId = new ApplicationId.Builder()
                .tenant("foo")
                .applicationName("bar")
                .instanceName("default")
                .build();

    private final ZoneId zoneId = ZoneId.from("prod", "us-west-1");

    private ConfigServerMock configServer;
    private ConfigServerMetrics service;

    @Before
    public void before() {
        configServer = new ConfigServerMock(new ZoneRegistryMock(SystemName.main));
        service = new ConfigServerMetrics(configServer);
    }

    @Test
    public void test_returning_metrics() {
        //
        // Wire up the test
        //
        var deploymentId = new DeploymentId(applicationId, zoneId);

        var clusterMetrics1 = new ClusterMetrics("niceCluster", "container") {{
            addMetric("queriesPerSecond", 23.0);
            addMetric("queryLatency", 1337.0);
        }};

        var clusterMetrics2 = new ClusterMetrics("alsoNiceCluster", "container") {{
            addMetric("queriesPerSecond", 11.0);
            addMetric("queryLatency", 12.0);
        }};

        var response = List.of(clusterMetrics1, clusterMetrics2);

        configServer.setMetrics(deploymentId, response);

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

}
