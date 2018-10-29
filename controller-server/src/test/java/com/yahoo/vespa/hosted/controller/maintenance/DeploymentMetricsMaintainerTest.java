// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.yahoo.config.provision.Environment;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.authority.config.ApiAuthorityConfig;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.integration.MetricsServiceMock;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.getAllServeEvents;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.assertEquals;

/**
 * @author smorgrav
 * @author mpolden
 */
public class DeploymentMetricsMaintainerTest {

    private static final double DELTA = 0.0000001;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(options().dynamicPort(), true);

    @Test
    public void maintain() {
        DeploymentTester tester = new DeploymentTester();
        MetricsServiceMock metricsService = tester.controllerTester().metricsService();
        ApiAuthorityConfig.Builder apiAuthorityConfigBuilder = new ApiAuthorityConfig.Builder().authorities("http://localhost:" + wireMockRule.port() + "/");
        ApiAuthorityConfig apiAuthorityConfig = new ApiAuthorityConfig(apiAuthorityConfigBuilder);
        DeploymentMetricsMaintainer maintainer = new DeploymentMetricsMaintainer(tester.controller(), Duration.ofDays(1), new JobControl(tester.controller().curator()), apiAuthorityConfig);
        Application application = tester.createApplication("app1", "tenant1", 1, 1L);
        ZoneId zone1 = ZoneId.from("prod", "us-west-1");
        ZoneId zone2 = ZoneId.from("prod", "us-east-3");

        // Deploy application with global rotation
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .globalServiceId("foo")
                .region(zone1.region().value())
                .region(zone2.region().value())
                .build();
        tester.deployCompletely(application, applicationPackage);

        String assignedRotation = "rotation-fqdn-01";
        tester.controllerTester().metricsService().addRotation(assignedRotation);

        // One rotation out, one in
        metricsService.setZoneIn(assignedRotation, "proxy.prod.us-west-1.vip.test");
        metricsService.setZoneOut(assignedRotation,"proxy.prod.us-east-3.vip.test");

        wireMockRule.stubFor(post(urlEqualTo("/metricforwarding/v1/deploymentmetrics/"))
                .willReturn(aResponse().withStatus(200)));
        maintainer.maintain();

        List<ServeEvent> allServeEvents = getAllServeEvents();
        assertEquals(1, allServeEvents.size());
        LoggedRequest request = findAll(postRequestedFor(urlEqualTo("/metricforwarding/v1/deploymentmetrics/"))).get(0);

        Slime slime = SlimeUtils.jsonToSlime(request.getBody());
        Inspector inspector = slime.get().entry(0);
        assertEquals("tenant1:app1:default", inspector.field("applicationId").asString());
        MetricsService.ApplicationMetrics applicationMetrics = applicationMetricsFromInspector(inspector.field("applicationMetrics"));
        assertEquals(0.5, applicationMetrics.queryServiceQuality(), DELTA);
        assertEquals(0.7, applicationMetrics.writeServiceQuality(), DELTA);

        Map<String, String> rotationStatus = rotationsStatusFromInspector(inspector.field("rotationStatus"));
        assertEquals("in", rotationStatus.get("proxy.prod.us-west-1.vip.test"));
        assertEquals("out", rotationStatus.get("proxy.prod.us-east-3.vip.test"));

        Map<String, MetricsService.DeploymentMetrics> deploymentMetricsByZone = deploymentMetricsFromInspector(inspector.field("deploymentMetrics"));
        MetricsService.DeploymentMetrics deploymentMetrics = deploymentMetricsByZone.get("prod.us-west-1");
        assertEquals(1.0, deploymentMetrics.queriesPerSecond(), DELTA);
        assertEquals(2.0, deploymentMetrics.writesPerSecond(), DELTA);
        assertEquals(3.0, deploymentMetrics.documentCount(), DELTA);
        assertEquals(4.0, deploymentMetrics.queryLatencyMillis(), DELTA);
        assertEquals(5.0, deploymentMetrics.writeLatencyMillis(), DELTA);

        deploymentMetrics = deploymentMetricsByZone.get("prod.us-east-3");
        assertEquals(1.0, deploymentMetrics.queriesPerSecond(), DELTA);
        assertEquals(2.0, deploymentMetrics.writesPerSecond(), DELTA);
        assertEquals(3.0, deploymentMetrics.documentCount(), DELTA);
        assertEquals(4.0, deploymentMetrics.queryLatencyMillis(), DELTA);
        assertEquals(5.0, deploymentMetrics.writeLatencyMillis(), DELTA);
    }

    private MetricsService.ApplicationMetrics applicationMetricsFromInspector(Inspector inspector) {
        return new MetricsService.ApplicationMetrics(inspector.field("queryServiceQuality").asDouble(), inspector.field("writeServiceQuality").asDouble());
    }

    private Map<String, String> rotationsStatusFromInspector(Inspector inspector) {
        HashMap<String, String> rotationStatus = new HashMap<>();
        inspector.traverse((ArrayTraverser) (index, entry) -> {
            rotationStatus.put(entry.field("hostname").asString(), entry.field("rotationStatus").asString());
        });
        return rotationStatus;
    }

    private Map<String, MetricsService.DeploymentMetrics> deploymentMetricsFromInspector(Inspector inspector) {
        Map<String, MetricsService.DeploymentMetrics> deploymentMetricByZone = new HashMap<>();
        inspector.traverse((ArrayTraverser) (index, entry) -> {
            String zone = entry.field("zoneId").asString();
            MetricsService.DeploymentMetrics deploymentMetrics = new MetricsService.DeploymentMetrics(entry.field("queriesPerSecond").asDouble(), entry.field("writesPerSecond").asDouble(),
                    entry.field("documentCount").asLong(), entry.field("queryLatencyMillis").asDouble(), entry.field("writeLatencyMillis").asDouble());
            deploymentMetricByZone.put(zone, deploymentMetrics);
        });
        return deploymentMetricByZone;
    }
}
