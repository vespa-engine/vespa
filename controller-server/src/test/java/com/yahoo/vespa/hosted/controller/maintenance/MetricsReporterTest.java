// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.MetricsMock;
import com.yahoo.vespa.hosted.controller.MetricsMock.MapContext;
import com.yahoo.vespa.hosted.controller.api.integration.chef.AttributeMapping;
import com.yahoo.vespa.hosted.controller.api.integration.chef.Chef;
import com.yahoo.vespa.hosted.controller.api.integration.chef.rest.PartialNodeResult;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.component;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.systemTest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @author mortent
 */
public class MetricsReporterTest {

    private static final Path testData = Paths.get("src/test/resources/");

    @Test
    public void test_chef_metrics() throws IOException {
        ControllerTester tester = new ControllerTester();
        MetricsMock metricsMock = new MetricsMock();
        MetricsReporter metricsReporter = setupMetricsReporter(tester.controller(), metricsMock, SystemName.cd);
        metricsReporter.maintain();
        assertEquals(2, metricsMock.getMetrics().size());

        Map<MapContext, Map<String, Number>> metrics = metricsMock.getMetricsFilteredByHost("fake-node.test");
        assertEquals(1, metrics.size());
        Map.Entry<MapContext, Map<String, Number>> metricEntry = metrics.entrySet().iterator().next();
        MapContext metricContext = metricEntry.getKey();
        assertDimension(metricContext, "tenantName", "ciintegrationtests");
        assertDimension(metricContext, "app", "restart.default");
        assertDimension(metricContext, "zone", "prod.cd-us-east-1");
        assertEquals(727, metricEntry.getValue().get(MetricsReporter.convergeMetric).longValue());
    }

    @Test
    public void test_deployment_metrics() throws IOException {
        DeploymentTester tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .build();
        MetricsMock metricsMock = new MetricsMock();
        MetricsReporter metricsReporter = setupMetricsReporter(tester.controller(), metricsMock, SystemName.cd);

        metricsReporter.maintain();
        assertEquals(0.0, metricsMock.getMetric(MetricsReporter.deploymentFailMetric));

        // Deploy all apps successfully
        Application app1 = tester.createApplication("app1", "tenant1", 1, 11L);
        Application app2 = tester.createApplication("app2", "tenant1", 2, 22L);
        Application app3 = tester.createApplication("app3", "tenant1", 3, 33L);
        Application app4 = tester.createApplication("app4", "tenant1", 4, 44L);
        tester.deployCompletely(app1, applicationPackage);
        tester.deployCompletely(app2, applicationPackage);
        tester.deployCompletely(app3, applicationPackage);
        tester.deployCompletely(app4, applicationPackage);

        metricsReporter.maintain();
        assertEquals(0.0, metricsMock.getMetric(MetricsReporter.deploymentFailMetric));

        // 1 app fails system-test
        tester.jobCompletion(component).application(app4).submit();
        tester.deployAndNotify(app4, applicationPackage, false, systemTest);

        metricsReporter.maintain();
        assertEquals(25.0, metricsMock.getMetric(MetricsReporter.deploymentFailMetric));
    }

    @Test
    public void it_omits_zone_when_unknown() throws IOException {
        ControllerTester tester = new ControllerTester();
        String hostname = "fake-node2.test";
        MapContext metricContext = getMetricsForHost(tester.controller(), hostname);
        assertNull(metricContext.getDimensions().get("zone"));
    }

    private void assertDimension(MapContext metricContext, String dimensionName, String expectedValue) {
        assertEquals(expectedValue, metricContext.getDimensions().get(dimensionName));
    }

    private MetricsReporter setupMetricsReporter(Controller controller, MetricsMock metricsMock, SystemName system) throws IOException {
        Chef client = Mockito.mock(Chef.class);
        PartialNodeResult result = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(testData.resolve("chef_output.json").toFile(), PartialNodeResult.class);
        when(client.partialSearchNodes(anyString(), anyListOf(AttributeMapping.class))).thenReturn(result);

        Clock clock = Clock.fixed(Instant.ofEpochSecond(1475497913), ZoneId.systemDefault());

        return new MetricsReporter(controller, metricsMock, client, clock,
                                   new JobControl(new MockCuratorDb()), system);
    }
    
    private MapContext getMetricsForHost(Controller controller, String hostname) throws IOException {
        MetricsMock metricsMock = new MetricsMock();
        MetricsReporter metricsReporter = setupMetricsReporter(controller, metricsMock, SystemName.main);
        metricsReporter.maintain();

        assertFalse(metricsMock.getMetrics().isEmpty());

        Map<MapContext, Map<String, Number>> metrics = metricsMock.getMetricsFilteredByHost(hostname);
        assertEquals(1, metrics.size());
        Map.Entry<MapContext, Map<String, Number>> metricEntry = metrics.entrySet().iterator().next();
        return metricEntry.getKey();
    }

}

