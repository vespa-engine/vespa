// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.zone.UpgradePolicy;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.deployment.InternalDeploymentTester;
import com.yahoo.vespa.hosted.controller.integration.MetricsMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

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
    public void deployment_fail_ratio() {
        var tester = new InternalDeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .build();
        MetricsReporter metricsReporter = createReporter(tester.controller());

        metricsReporter.maintain();
        assertEquals(0.0, metrics.getMetric(MetricsReporter.DEPLOYMENT_FAIL_METRIC));

        // Deploy all apps successfully
        Application app1 = tester.createApplication("app1", "tenant1", "default");
        Application app2 = tester.createApplication("app2", "tenant1", "default");
        Application app3 = tester.createApplication("app3", "tenant1", "default");
        Application app4 = tester.createApplication("app4", "tenant1", "default");
        var version1 = tester.newSubmission(app1.id(), applicationPackage);
        tester.deployNewSubmission(app1.id(), version1);
        tester.deployNewSubmission(app2.id(), tester.newSubmission(app2.id(), applicationPackage));
        tester.deployNewSubmission(app3.id(), tester.newSubmission(app3.id(), applicationPackage));
        tester.deployNewSubmission(app4.id(), tester.newSubmission(app4.id(), applicationPackage));

        metricsReporter.maintain();
        assertEquals(0.0, metrics.getMetric(MetricsReporter.DEPLOYMENT_FAIL_METRIC));

        // 1 app fails system-test
        tester.newSubmission(app4.id(), applicationPackage);
        tester.triggerJobs();
        tester.failDeployment(app4.id().defaultInstance(), systemTest);

        metricsReporter.maintain();
        assertEquals(25.0, metrics.getMetric(MetricsReporter.DEPLOYMENT_FAIL_METRIC));
    }

    @Test
    public void deployment_average_duration() {
        DeploymentTester tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .build();

        MetricsReporter reporter = createReporter(tester.controller());

        Application app = tester.createApplication("app1", "tenant1", 1, 11L);
        tester.deployCompletely(app, applicationPackage);
        reporter.maintain();
        assertEquals(Duration.ZERO, getAverageDeploymentDuration(app.id().defaultInstance())); // An exceptionally fast deployment :-)

        // App spends 3 hours deploying
        tester.jobCompletion(component).application(app).nextBuildNumber().uploadArtifact(applicationPackage).submit();
        tester.clock().advance(Duration.ofHours(1));
        tester.deployAndNotify(app.id().defaultInstance(), Optional.of(applicationPackage), true, systemTest);

        tester.clock().advance(Duration.ofMinutes(30));
        tester.deployAndNotify(app.id().defaultInstance(), Optional.of(applicationPackage), true, stagingTest);

        tester.clock().advance(Duration.ofMinutes(90));
        tester.deployAndNotify(app.id().defaultInstance(), Optional.of(applicationPackage), true, productionUsWest1);
        reporter.maintain();

        // Average time is 1 hour (system-test) + 90 minutes (staging-test runs in parallel with system-test) + 90 minutes (production) / 3 jobs
        assertEquals(Duration.ofMinutes(80), getAverageDeploymentDuration(app.id().defaultInstance()));

        // Another deployment starts and stalls for 12 hours
        tester.jobCompletion(component).application(app).nextBuildNumber(2).uploadArtifact(applicationPackage).submit();
        tester.clock().advance(Duration.ofHours(12));
        reporter.maintain();

        assertEquals(Duration.ofHours(12) // hanging system-test
                             .plus(Duration.ofHours(12)) // hanging staging-test
                             .plus(Duration.ofMinutes(90)) // previous production job
                             .dividedBy(3), // Total number of orchestrated jobs
                     getAverageDeploymentDuration(app.id().defaultInstance()));
    }

    @Test
    public void deployments_failing_upgrade() {
        DeploymentTester tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .build();

        MetricsReporter reporter = createReporter(tester.controller());
        Application app = tester.createApplication("app1", "tenant1", 1, 11L);

        // Initial deployment without failures
        tester.deployCompletely(app, applicationPackage);
        reporter.maintain();
        assertEquals(0, getDeploymentsFailingUpgrade(app.id().defaultInstance()));

        // Failing application change is not counted
        tester.jobCompletion(component).application(app).nextBuildNumber().uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app.id().defaultInstance(), Optional.of(applicationPackage), false, systemTest);
        reporter.maintain();
        assertEquals(0, getDeploymentsFailingUpgrade(app.id().defaultInstance()));

        // Application change completes
        tester.deployAndNotify(app.id().defaultInstance(), Optional.of(applicationPackage), true, systemTest);
        tester.deployAndNotify(app.id().defaultInstance(), Optional.of(applicationPackage), true, stagingTest);
        tester.deployAndNotify(app.id().defaultInstance(), Optional.of(applicationPackage), true, productionUsWest1);
        assertFalse("Change deployed", tester.controller().applications().requireApplication(app.id()).change().hasTargets());

        // New versions is released and upgrade fails in test environments
        Version version = Version.fromString("7.1");
        tester.upgradeSystem(version);
        tester.upgrader().maintain();
        tester.deployAndNotify(app.id().defaultInstance(), Optional.of(applicationPackage), false, systemTest);
        tester.deployAndNotify(app.id().defaultInstance(), Optional.of(applicationPackage), false, stagingTest);
        reporter.maintain();
        assertEquals(2, getDeploymentsFailingUpgrade(app.id().defaultInstance()));

        // Test and staging pass and upgrade fails in production
        tester.deployAndNotify(app.id().defaultInstance(), Optional.of(applicationPackage), true, systemTest);
        tester.deployAndNotify(app.id().defaultInstance(), Optional.of(applicationPackage), true, stagingTest);
        tester.deployAndNotify(app.id().defaultInstance(), Optional.of(applicationPackage), false, productionUsWest1);
        reporter.maintain();
        assertEquals(1, getDeploymentsFailingUpgrade(app.id().defaultInstance()));

        // Upgrade eventually succeeds
        tester.deployAndNotify(app.id().defaultInstance(), Optional.of(applicationPackage), true, productionUsWest1);
        assertFalse("Upgrade deployed", tester.controller().applications().requireApplication(app.id()).change().hasTargets());
        reporter.maintain();
        assertEquals(0, getDeploymentsFailingUpgrade(app.id().defaultInstance()));
    }

    @Test
    public void deployment_warnings_metric() {
        var tester = new InternalDeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .region("us-east-3")
                .build();
        MetricsReporter reporter = createReporter(tester.controller());
        Application application = tester.createApplication("app1", "tenant1", "default");
        tester.configServer().generateWarnings(new DeploymentId(application.id().defaultInstance(), ZoneId.from("prod", "us-west-1")), 3);
        tester.configServer().generateWarnings(new DeploymentId(application.id().defaultInstance(), ZoneId.from("prod", "us-east-3")), 4);
        tester.deployNewSubmission(application.id(), tester.newSubmission(application.id(), applicationPackage));
        reporter.maintain();
        assertEquals(4, getDeploymentWarnings(application.id().defaultInstance()));
    }

    @Test
    public void build_time_reporting() {
        var tester = new InternalDeploymentTester();
        ApplicationVersion version = tester.newSubmission();
        tester.deployNewSubmission(version);
        assertEquals(1000, version.buildTime().get().toEpochMilli());

        MetricsReporter reporter = createReporter(tester.tester().controller());
        reporter.maintain();
        assertEquals(tester.clock().instant().getEpochSecond() - 1,
                     getMetric(MetricsReporter.DEPLOYMENT_BUILD_AGE_SECONDS, tester.instance().id()));
    }

    @Test
    public void name_service_queue_size_metric() {
        var tester = new InternalDeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .globalServiceId("default")
                .region("us-west-1")
                .region("us-east-3")
                .build();
        MetricsReporter reporter = createReporter(tester.controller());
        Application application = tester.createApplication("app1", "tenant1", "default");
        reporter.maintain();
        assertEquals("Queue is empty initially", 0, metrics.getMetric(MetricsReporter.NAME_SERVICE_REQUESTS_QUEUED).intValue());

        tester.deployNewSubmission(application.id(), tester.newSubmission(application.id(), applicationPackage));
        reporter.maintain();
        assertEquals("Deployment queues name services requests", 6, metrics.getMetric(MetricsReporter.NAME_SERVICE_REQUESTS_QUEUED).intValue());

        tester.flushDnsRequests();
        reporter.maintain();
        assertEquals("Queue consumed", 0, metrics.getMetric(MetricsReporter.NAME_SERVICE_REQUESTS_QUEUED).intValue());
    }

    @Test
    public void nodes_failing_system_upgrade() {
        var tester = new ControllerTester();
        var reporter = createReporter(tester.controller());
        var zone1 = ZoneApiMock.fromId("prod.eu-west-1");
        tester.zoneRegistry().setUpgradePolicy(UpgradePolicy.create().upgrade(zone1));
        var systemUpgrader = new SystemUpgrader(tester.controller(), Duration.ofDays(1),
                                                new JobControl(tester.curator()));
        tester.configServer().bootstrap(List.of(zone1.getId()), SystemApplication.configServer);

        // System on initial version
        var version0 = Version.fromString("7.0");
        tester.upgradeSystem(version0);
        reporter.maintain();
        assertEquals(0, getNodesFailingUpgrade());

        for (var version : List.of(Version.fromString("7.1"), Version.fromString("7.2"))) {
            // System starts upgrading to next version
            tester.upgradeController(version);
            reporter.maintain();
            assertEquals(0, getNodesFailingUpgrade());
            systemUpgrader.maintain();

            // 30 minutes pass and nothing happens
            tester.clock().advance(Duration.ofMinutes(30));
            tester.computeVersionStatus();
            reporter.maintain();
            assertEquals(0, getNodesFailingUpgrade());

            // 1/3 nodes upgrade within timeout
            tester.configServer().setVersion(SystemApplication.configServer.id(), zone1.getId(), version, 1);
            tester.clock().advance(Duration.ofMinutes(30).plus(Duration.ofSeconds(1)));
            tester.computeVersionStatus();
            reporter.maintain();
            assertEquals(2, getNodesFailingUpgrade());

            // 3/3 nodes upgrade
            tester.configServer().setVersion(SystemApplication.configServer.id(), zone1.getId(), version);
            tester.computeVersionStatus();
            reporter.maintain();
            assertEquals(0, getNodesFailingUpgrade());
            assertEquals(version, tester.controller().systemVersion());
        }
    }

    @Test
    public void nodes_failing_os_upgrade() {
        var tester = new ControllerTester();
        var reporter = createReporter(tester.controller());
        var zone = ZoneApiMock.fromId("prod.eu-west-1");
        var cloud = CloudName.defaultName();
        tester.zoneRegistry().setOsUpgradePolicy(cloud, UpgradePolicy.create().upgrade(zone));
        var osUpgrader = new OsUpgrader(tester.controller(), Duration.ofDays(1),
                                        new JobControl(tester.curator()), CloudName.defaultName());;
        var statusUpdater = new OsVersionStatusUpdater(tester.controller(), Duration.ofDays(1),
                                                       new JobControl(tester.controller().curator()));
        tester.configServer().bootstrap(List.of(zone.getId()), SystemApplication.configServerHost, SystemApplication.tenantHost);

        // All nodes upgrade to initial OS version
        var version0 = Version.fromString("8.0");
        tester.controller().upgradeOsIn(cloud, version0, false);
        osUpgrader.maintain();
        tester.configServer().setOsVersion(SystemApplication.tenantHost.id(), zone.getId(), version0);
        tester.configServer().setOsVersion(SystemApplication.configServerHost.id(), zone.getId(), version0);
        statusUpdater.maintain();
        reporter.maintain();
        assertEquals(0, getNodesFailingOsUpgrade());

        for (var version : List.of(Version.fromString("8.1"), Version.fromString("8.2"))) {
            // System starts upgrading to next OS version
            tester.controller().upgradeOsIn(cloud, version, false);
            osUpgrader.maintain();
            statusUpdater.maintain();
            reporter.maintain();
            assertEquals(0, getNodesFailingOsUpgrade());

            // 30 minutes pass and nothing happens
            tester.clock().advance(Duration.ofMinutes(30));
            statusUpdater.maintain();
            reporter.maintain();
            assertEquals(0, getNodesFailingOsUpgrade());

            // 2/6 nodes upgrade within timeout
            tester.configServer().setOsVersion(SystemApplication.tenantHost.id(), zone.getId(), version, 2);
            tester.clock().advance(Duration.ofMinutes(30 * 3 /* time allowance * node count */).plus(Duration.ofSeconds(1)));
            statusUpdater.maintain();
            reporter.maintain();
            assertEquals(4, getNodesFailingOsUpgrade());

            // 5/6 nodes upgrade
            tester.configServer().setOsVersion(SystemApplication.tenantHost.id(), zone.getId(), version);
            tester.configServer().setOsVersion(SystemApplication.configServerHost.id(), zone.getId(), version, 2);
            statusUpdater.maintain();
            reporter.maintain();
            assertEquals(1, getNodesFailingOsUpgrade());

            // Final node upgrades
            tester.configServer().setOsVersion(SystemApplication.configServerHost.id(), zone.getId(), version);
            statusUpdater.maintain();
            reporter.maintain();
            assertEquals(0, getNodesFailingOsUpgrade());
        }
    }

    private Duration getAverageDeploymentDuration(ApplicationId id) {
        return Duration.ofSeconds(getMetric(MetricsReporter.DEPLOYMENT_AVERAGE_DURATION, id).longValue());
    }

    private int getDeploymentsFailingUpgrade(ApplicationId id) {
        return getMetric(MetricsReporter.DEPLOYMENT_FAILING_UPGRADES, id).intValue();
    }

    private int getDeploymentWarnings(ApplicationId id) {
        return getMetric(MetricsReporter.DEPLOYMENT_WARNINGS, id).intValue();
    }

    private int getNodesFailingUpgrade() {
        return metrics.getMetric(MetricsReporter.NODES_FAILING_SYSTEM_UPGRADE).intValue();
    }

    private int getNodesFailingOsUpgrade() {
        return metrics.getMetric(MetricsReporter.NODES_FAILING_OS_UPGRADE).intValue();
    }

    private Number getMetric(String name, ApplicationId id) {
        return metrics.getMetric((dimensions) -> id.tenant().value().equals(dimensions.get("tenant")) &&
                                                 appDimension(id).equals(dimensions.get("app")),
                                 name)
                      .orElseThrow(() -> new RuntimeException("Expected metric to exist for " + id));
    }

    private MetricsReporter createReporter(Controller controller) {
        return new MetricsReporter(controller, metrics, new JobControl(new MockCuratorDb()));
    }

    private static String appDimension(ApplicationId id) {
        return id.application().value() + "." + id.instance().value();
    }

}

