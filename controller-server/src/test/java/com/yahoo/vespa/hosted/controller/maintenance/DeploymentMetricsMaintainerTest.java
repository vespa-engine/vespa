// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.authority.config.ApiAuthorityConfig;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.integration.MetricsServiceMock;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.getAllServeEvents;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.Assert.assertEquals;

/**
 * @author smorgrav
 * @author mpolden
 */
public class DeploymentMetricsMaintainerTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(4443);

    @Test
    public void maintain() {
        DeploymentTester tester = new DeploymentTester();
        MetricsServiceMock metricsService = tester.controllerTester().metricsService();
        ApiAuthorityConfig.Builder apiAuthorityConfigBuilder = new ApiAuthorityConfig.Builder().authorities("http://localhost:4443/");
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
        String expectedBody = "[{\"applicationId\":\"tenant1:app1:default\",\"applicationMetrics\":{\"queryServiceQuality\":0.5,\"writeServiceQuality\":0.7},\"rotationStatus\":[{\"hostname\":\"proxy.prod.us-east-3.vip.test\",\"rotationStatus\":\"out\"},{\"hostname\":\"proxy.prod.us-west-1.vip.test\",\"rotationStatus\":\"in\"}],\"deploymentMetrics\":[{\"zoneId\":\"prod.us-west-1\",\"queriesPerSecond\":1.0,\"writesPerSecond\":2.0,\"documentCount\":3.0,\"queryLatencyMillis\":4.0,\"writeLatencyMillis\":5.0},{\"zoneId\":\"prod.us-east-3\",\"queriesPerSecond\":1.0,\"writesPerSecond\":2.0,\"documentCount\":3.0,\"queryLatencyMillis\":4.0,\"writeLatencyMillis\":5.0}]}]";
        assertEquals(expectedBody, new String(request.getBody()));
    }

}
