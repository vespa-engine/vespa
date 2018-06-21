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
import com.yahoo.vespa.hosted.controller.api.integration.chef.ChefMock;
import com.yahoo.vespa.hosted.controller.api.integration.chef.rest.PartialNodeResult;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.component;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionUsWest1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.systemTest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/**
 * @author mortent
 */
public class MetricsReporterTest {

    private static final Path testData = Paths.get("src/test/resources/");
    private MetricsMock metrics;

    @Before
    public void before() {
        metrics = new MetricsMock();
    }

    @Test
    public void test_chef_metrics() {
        Clock clock = Clock.fixed(Instant.ofEpochSecond(1475497913), ZoneId.systemDefault());;
        ControllerTester tester = new ControllerTester();
        MetricsReporter metricsReporter = createReporter(clock, tester.controller(), metrics, SystemName.cd);
        metricsReporter.maintain();
        assertEquals(2, metrics.getMetrics().size());

        Map<MapContext, Map<String, Number>> hostMetrics = getMetricsByHost("fake-node.test");
        assertEquals(1, hostMetrics.size());
        Map.Entry<MapContext, Map<String, Number>> metricEntry = hostMetrics.entrySet().iterator().next();
        MapContext metricContext = metricEntry.getKey();
        assertDimension(metricContext, "tenantName", "ciintegrationtests");
        assertDimension(metricContext, "app", "restart.default");
        assertDimension(metricContext, "zone", "prod.cd-us-east-1");
        assertEquals(727, metricEntry.getValue().get(MetricsReporter.convergeMetric).longValue());
    }

    @Test
    public void test_deployment_fail_ratio() {
        DeploymentTester tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .build();
        MetricsReporter metricsReporter = createReporter(tester.controller(), metrics, SystemName.cd);

        metricsReporter.maintain();
        assertEquals(0.0, metrics.getMetric(MetricsReporter.deploymentFailMetric));

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
        assertEquals(0.0, metrics.getMetric(MetricsReporter.deploymentFailMetric));

        // 1 app fails system-test
        tester.jobCompletion(component).application(app4).nextBuildNumber().uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app4, applicationPackage, false, systemTest);

        metricsReporter.maintain();
        assertEquals(25.0, metrics.getMetric(MetricsReporter.deploymentFailMetric));
    }

    @Test
    public void it_omits_zone_when_unknown() {
        ControllerTester tester = new ControllerTester();
        String hostname = "fake-node2.test";
        MapContext metricContext = getMetricContextByHost(tester.controller(), hostname);
        assertNull(metricContext.getDimensions().get("zone"));
    }

    @Test
    public void test_deployment_average_duration() {
        DeploymentTester tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .build();

        MetricsReporter reporter = createReporter(tester.controller(), metrics, SystemName.cd);

        Application app = tester.createApplication("app1", "tenant1", 1, 11L);
        tester.deployCompletely(app, applicationPackage);
        reporter.maintain();
        assertEquals(Duration.ZERO, getAverageDeploymentDuration(app)); // An exceptionally fast deployment :-)

        // App spends 3 hours deploying
        tester.jobCompletion(component).application(app).nextBuildNumber().uploadArtifact(applicationPackage).submit();
        tester.clock().advance(Duration.ofHours(1));
        tester.deployAndNotify(app, applicationPackage, true, systemTest);

        tester.clock().advance(Duration.ofMinutes(30));
        tester.deployAndNotify(app, applicationPackage, true, stagingTest);

        tester.clock().advance(Duration.ofMinutes(90));
        tester.deployAndNotify(app, applicationPackage, true, productionUsWest1);
        reporter.maintain();

        // Average time is 1 hour
        assertEquals(Duration.ofMinutes(80), getAverageDeploymentDuration(app));

        // Another deployment starts and stalls for 12 hours
        tester.jobCompletion(component).application(app).nextBuildNumber(2).uploadArtifact(applicationPackage).submit();
        tester.clock().advance(Duration.ofHours(12));
        reporter.maintain();

        assertEquals(Duration.ofHours(12) // hanging system-test
                             .plus(Duration.ofHours(12)) // hanging staging-test
                             .plus(Duration.ofMinutes(90)) // previous production job
                             .dividedBy(3), // Total number of orchestrated jobs
                     getAverageDeploymentDuration(app));
    }

    private Duration getAverageDeploymentDuration(Application application) {
        return metrics.getMetric((dimensions) -> application.id().tenant().value().equals(dimensions.get("tenant")) &&
                                                 appDimension(application).equals(dimensions.get("app")),
                                 MetricsReporter.deploymentAverageDuration)
                      .map(seconds -> Duration.ofSeconds(seconds.longValue()))
                      .orElseThrow(() -> new RuntimeException("Expected metric to exist for " + application.id()));
    }

    private MetricsReporter createReporter(Controller controller, MetricsMock metricsMock, SystemName system) {
        return createReporter(controller.clock(), controller, metricsMock, system);
    }

    private MetricsReporter createReporter(Clock clock, Controller controller, MetricsMock metricsMock,
                                           SystemName system) {
        ChefMock chef = new ChefMock();
        PartialNodeResult result;
        try {
            result = new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(testData.resolve("chef_output.json").toFile(), PartialNodeResult.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        chef.addPartialResult(result.rows);
        return new MetricsReporter(controller, metricsMock, chef, clock, new JobControl(new MockCuratorDb()), system);
    }

    private Map<MapContext, Map<String, Number>> getMetricsByHost(String hostname) {
        return metrics.getMetrics((dimensions) -> hostname.equals(dimensions.get("host")));
    }
    
    private MapContext getMetricContextByHost(Controller controller, String hostname) {
        MetricsReporter metricsReporter = createReporter(controller, metrics, SystemName.main);
        metricsReporter.maintain();

        assertFalse(metrics.getMetrics().isEmpty());

        Map<MapContext, Map<String, Number>> metrics = getMetricsByHost(hostname);
        assertEquals(1, metrics.size());
        Map.Entry<MapContext, Map<String, Number>> metricEntry = metrics.entrySet().iterator().next();
        return metricEntry.getKey();
    }

    private static void assertDimension(MapContext metricContext, String dimensionName, String expectedValue) {
        assertEquals(expectedValue, metricContext.getDimensions().get(dimensionName));
    }

    private static String appDimension(Application application) {
        return application.id().application().value() + "." + application.id().instance().value();
    }

}

