// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.deployment.InternalDeploymentTester;
import com.yahoo.vespa.hosted.controller.integration.MetricsMock;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Test;

import java.time.Duration;

import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.component;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionUsWest1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.systemTest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author mortent
 */
public class MetricsReporterTest {

    private final MetricsMock metrics = new MetricsMock();

    @Test
    public void test_deployment_fail_ratio() {
        DeploymentTester tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .build();
        MetricsReporter metricsReporter = createReporter(tester.controller());

        metricsReporter.maintain();
        assertEquals(0.0, metrics.getMetric(MetricsReporter.DEPLOYMENT_FAIL_METRIC));

        // Deploy all apps successfully
        Instance app1 = tester.createApplication("app1", "tenant1", 1, 11L);
        Instance app2 = tester.createApplication("app2", "tenant1", 2, 22L);
        Instance app3 = tester.createApplication("app3", "tenant1", 3, 33L);
        Instance app4 = tester.createApplication("app4", "tenant1", 4, 44L);
        tester.deployCompletely(app1, applicationPackage);
        tester.deployCompletely(app2, applicationPackage);
        tester.deployCompletely(app3, applicationPackage);
        tester.deployCompletely(app4, applicationPackage);

        metricsReporter.maintain();
        assertEquals(0.0, metrics.getMetric(MetricsReporter.DEPLOYMENT_FAIL_METRIC));

        // 1 app fails system-test
        tester.jobCompletion(component).application(app4).nextBuildNumber().uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app4, applicationPackage, false, systemTest);

        metricsReporter.maintain();
        assertEquals(25.0, metrics.getMetric(MetricsReporter.DEPLOYMENT_FAIL_METRIC));
    }

    @Test
    public void test_deployment_average_duration() {
        DeploymentTester tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .build();

        MetricsReporter reporter = createReporter(tester.controller());

        Instance app = tester.createApplication("app1", "tenant1", 1, 11L);
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

        // Average time is 1 hour (system-test) + 90 minutes (staging-test runs in parallel with system-test) + 90 minutes (production) / 3 jobs
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

    @Test
    public void test_deployments_failing_upgrade() {
        DeploymentTester tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .build();

        MetricsReporter reporter = createReporter(tester.controller());
        Instance app = tester.createApplication("app1", "tenant1", 1, 11L);

        // Initial deployment without failures
        tester.deployCompletely(app, applicationPackage);
        reporter.maintain();
        assertEquals(0, getDeploymentsFailingUpgrade(app));

        // Failing application change is not counted
        tester.jobCompletion(component).application(app).nextBuildNumber().uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app, applicationPackage, false, systemTest);
        reporter.maintain();
        assertEquals(0, getDeploymentsFailingUpgrade(app));

        // Application change completes
        tester.deployAndNotify(app, applicationPackage, true, systemTest);
        tester.deployAndNotify(app, applicationPackage, true, stagingTest);
        tester.deployAndNotify(app, applicationPackage, true, productionUsWest1);
        assertFalse("Change deployed", tester.controller().applications().require(app.id()).change().hasTargets());

        // New versions is released and upgrade fails in test environments
        Version version = Version.fromString("7.1");
        tester.upgradeSystem(version);
        tester.upgrader().maintain();
        tester.deployAndNotify(app, applicationPackage, false, systemTest);
        tester.deployAndNotify(app, applicationPackage, false, stagingTest);
        reporter.maintain();
        assertEquals(2, getDeploymentsFailingUpgrade(app));

        // Test and staging pass and upgrade fails in production
        tester.deployAndNotify(app, applicationPackage, true, systemTest);
        tester.deployAndNotify(app, applicationPackage, true, stagingTest);
        tester.deployAndNotify(app, applicationPackage, false, productionUsWest1);
        reporter.maintain();
        assertEquals(1, getDeploymentsFailingUpgrade(app));

        // Upgrade eventually succeeds
        tester.deployAndNotify(app, applicationPackage, true, productionUsWest1);
        assertFalse("Upgrade deployed", tester.controller().applications().require(app.id()).change().hasTargets());
        reporter.maintain();
        assertEquals(0, getDeploymentsFailingUpgrade(app));
    }

    @Test
    public void test_deployment_warnings_metric() {
        DeploymentTester tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .region("us-east-3")
                .build();
        MetricsReporter reporter = createReporter(tester.controller());
        Instance instance = tester.createApplication("app1", "tenant1", 1, 11L);
        tester.configServer().generateWarnings(new DeploymentId(instance.id(), ZoneId.from("prod", "us-west-1")), 3);
        tester.configServer().generateWarnings(new DeploymentId(instance.id(), ZoneId.from("prod", "us-east-3")), 4);
        tester.deployCompletely(instance, applicationPackage);
        reporter.maintain();
        assertEquals(4, getDeploymentWarnings(instance));
    }

    @Test
    public void test_build_time_reporting() {
        InternalDeploymentTester tester = new InternalDeploymentTester();
        ApplicationVersion version = tester.deployNewSubmission();
        assertEquals(1000, version.buildTime().get().toEpochMilli());

        MetricsReporter reporter = createReporter(tester.tester().controller());
        reporter.maintain();
        assertEquals(tester.clock().instant().getEpochSecond() - 1,
                     getMetric(MetricsReporter.DEPLOYMENT_BUILD_AGE_SECONDS, tester.app()));
    }

    @Test
    public void test_name_service_queue_size_metric() {
        DeploymentTester tester = new DeploymentTester(new ControllerTester(), false);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .globalServiceId("default")
                .region("us-west-1")
                .region("us-east-3")
                .build();
        MetricsReporter reporter = createReporter(tester.controller());
        Instance instance = tester.createApplication("app1", "tenant1", 1, 11L);
        reporter.maintain();
        assertEquals("Queue is empty initially", 0, metrics.getMetric(MetricsReporter.NAME_SERVICE_REQUESTS_QUEUED).intValue());

        tester.deployCompletely(instance, applicationPackage);
        reporter.maintain();
        assertEquals("Deployment queues name services requests", 6, metrics.getMetric(MetricsReporter.NAME_SERVICE_REQUESTS_QUEUED).intValue());

        tester.flushDnsRequests();
        reporter.maintain();
        assertEquals("Queue consumed", 0, metrics.getMetric(MetricsReporter.NAME_SERVICE_REQUESTS_QUEUED).intValue());
    }

    private Duration getAverageDeploymentDuration(Instance instance) {
        return Duration.ofSeconds(getMetric(MetricsReporter.DEPLOYMENT_AVERAGE_DURATION, instance).longValue());
    }

    private int getDeploymentsFailingUpgrade(Instance instance) {
        return getMetric(MetricsReporter.DEPLOYMENT_FAILING_UPGRADES, instance).intValue();
    }

    private int getDeploymentWarnings(Instance instance) {
        return getMetric(MetricsReporter.DEPLOYMENT_WARNINGS, instance).intValue();
    }

    private Number getMetric(String name, Instance instance) {
        return metrics.getMetric((dimensions) -> instance.id().tenant().value().equals(dimensions.get("tenant")) &&
                                                 appDimension(instance).equals(dimensions.get("app")),
                                 name)
                      .orElseThrow(() -> new RuntimeException("Expected metric to exist for " + instance.id()));
    }

    private MetricsReporter createReporter(Controller controller) {
        return new MetricsReporter(controller, metrics, new JobControl(new MockCuratorDb()));
    }

    private static String appDimension(Instance instance) {
        return instance.id().application().value() + "." + instance.id().instance().value();
    }

}

