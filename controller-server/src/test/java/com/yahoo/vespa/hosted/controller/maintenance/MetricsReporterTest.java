// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.UpgradePolicy;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.integration.MetricsMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import org.junit.Test;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionUsWest1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.systemTest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author mortent
 */
public class MetricsReporterTest {

    private final MetricsMock metrics = new MetricsMock();

    @Test
    public void audit_log_metric() {
        var tester = new ControllerTester();

        MetricsReporter metricsReporter = createReporter(tester.controller());
        // Log some operator actions
        HttpRequest req1 = HttpRequest.createTestRequest(
                "http://localhost:8080/zone/v2/prod/some_region/nodes/v2/state/dirty/hostname",
                com.yahoo.jdisc.http.HttpRequest.Method.PUT
        );
        req1.getJDiscRequest().setUserPrincipal(() -> "user.janedoe");
        tester.controller().auditLogger().log((req1));
        HttpRequest req2 = HttpRequest.createTestRequest(
                "http://localhost:8080/routing/v1/inactive/tenant/some_tenant/application/some_app/instance/default/environment/prod/region/some-region",
                com.yahoo.jdisc.http.HttpRequest.Method.POST
        );
        req2.getJDiscRequest().setUserPrincipal(() -> "user.johndoe");
        tester.controller().auditLogger().log((req2));

        // Report metrics
        metricsReporter.maintain();
        assertEquals(1, getMetric(MetricsReporter.OPERATION_PREFIX + "zone", "user.janedoe"));
        assertEquals(1, getMetric(MetricsReporter.OPERATION_PREFIX + "routing", "user.johndoe"));

        // Log some more operator actions
        HttpRequest req3 = HttpRequest.createTestRequest(
                "http://localhost:8080/zone/v2/prod/us-northeast-1/nodes/v2/state/dirty/le04614.ostk.bm2.prod.ca1.yahoo.com",
                com.yahoo.jdisc.http.HttpRequest.Method.PUT
        );
        req3.getJDiscRequest().setUserPrincipal(() -> "user.janedoe");
        tester.controller().auditLogger().log((req3));
        HttpRequest req4 = HttpRequest.createTestRequest(
                "http://localhost:8080/routing/v1/inactive/tenant/some_publishing/application/someindexing/instance/default/environment/prod/region/us-northeast-1",
                com.yahoo.jdisc.http.HttpRequest.Method.POST
        );
        req4.getJDiscRequest().setUserPrincipal(() -> "user.johndoe");
        tester.controller().auditLogger().log((req4));
        HttpRequest req5 = HttpRequest.createTestRequest(
                "http://localhost:8080/zone/v2/prod/us-northeast-1/nodes/v2/state/dirty/le04614.ostk.bm2.prod.ca1.yahoo.com",
                com.yahoo.jdisc.http.HttpRequest.Method.PUT
        );
        req5.getJDiscRequest().setUserPrincipal(() -> "user.johndoe");
        tester.controller().auditLogger().log((req5));
        HttpRequest req6 = HttpRequest.createTestRequest(
                "http://localhost:8080/routing/v1/inactive/tenant/some_publishing/application/someindexing/instance/default/environment/prod/region/us-northeast-1",
                com.yahoo.jdisc.http.HttpRequest.Method.POST
        );
        req6.getJDiscRequest().setUserPrincipal(() -> "user.janedoe");
        tester.controller().auditLogger().log((req6));

        // Report metrics
        metricsReporter.maintain();
        assertEquals(2, getMetric(MetricsReporter.OPERATION_PREFIX + "zone", "user.janedoe"));
        assertEquals(2, getMetric(MetricsReporter.OPERATION_PREFIX + "routing", "user.johndoe"));
        assertEquals(1, getMetric(MetricsReporter.OPERATION_PREFIX + "zone", "user.johndoe"));
        assertEquals(1, getMetric(MetricsReporter.OPERATION_PREFIX + "routing", "user.janedoe"));
    }

    @Test
    public void deployment_fail_ratio() {
        var tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .region("us-west-1")
                .build();
        MetricsReporter metricsReporter = createReporter(tester.controller());

        metricsReporter.maintain();
        assertEquals(0.0, metrics.getMetric(MetricsReporter.DEPLOYMENT_FAIL_METRIC));

        // Deploy all apps successfully
        var context1 = tester.newDeploymentContext("app1", "tenant1", "default");
        var context2 = tester.newDeploymentContext("app2", "tenant1", "default");
        var context3 = tester.newDeploymentContext("app3", "tenant1", "default");
        var context4 = tester.newDeploymentContext("app4", "tenant1", "default");
        context1.submit(applicationPackage).deploy();
        context2.submit(applicationPackage).deploy();
        context3.submit(applicationPackage).deploy();
        context4.submit(applicationPackage).deploy();

        metricsReporter.maintain();
        assertEquals(0.0, metrics.getMetric(MetricsReporter.DEPLOYMENT_FAIL_METRIC));

        // 1 app fails system-test
        context1.submit(applicationPackage)
                .triggerJobs()
                .failDeployment(systemTest);

        metricsReporter.maintain();
        assertEquals(25.0, metrics.getMetric(MetricsReporter.DEPLOYMENT_FAIL_METRIC));
    }

    @Test
    public void deployment_average_duration() {
        var tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .region("us-west-1")
                .build();

        MetricsReporter reporter = createReporter(tester.controller());

        var context = tester.newDeploymentContext()
                            .submit(applicationPackage)
                            .deploy();
        reporter.maintain();
        assertEquals(Duration.ZERO, getAverageDeploymentDuration(context.instanceId())); // An exceptionally fast deployment :-)

        // App spends 3 hours deploying
        context.submit(applicationPackage);
        tester.triggerJobs();
        tester.clock().advance(Duration.ofHours(1));
        context.runJob(systemTest);

        tester.clock().advance(Duration.ofMinutes(30));
        context.runJob(stagingTest);

        tester.triggerJobs();
        tester.clock().advance(Duration.ofMinutes(90));
        context.runJob(productionUsWest1);
        reporter.maintain();

        // Average time is 1 hour (system-test) + 90 minutes (staging-test runs in parallel with system-test) + 90 minutes (production) / 3 jobs
        assertEquals(Duration.ofMinutes(80), getAverageDeploymentDuration(context.instanceId()));

        // Another deployment starts and stalls for 12 hours
        context.submit(applicationPackage)
               .triggerJobs();
        tester.clock().advance(Duration.ofHours(12));
        reporter.maintain();

        assertEquals(Duration.ofHours(12) // hanging system-test
                             .plus(Duration.ofHours(12)) // hanging staging-test
                             .plus(Duration.ofMinutes(90)) // previous production job
                             .dividedBy(3), // Total number of orchestrated jobs
                     getAverageDeploymentDuration(context.instanceId()));
    }

    @Test
    public void deployments_failing_upgrade() {
        var tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .region("us-west-1")
                .build();

        MetricsReporter reporter = createReporter(tester.controller());
        var context = tester.newDeploymentContext();

        // Initial deployment without failures
        context.submit(applicationPackage).deploy();
        reporter.maintain();
        assertEquals(0, getDeploymentsFailingUpgrade(context.instanceId()));

        // Failing application change is not counted
        context.submit(applicationPackage)
               .triggerJobs()
               .failDeployment(systemTest);
        reporter.maintain();
        assertEquals(0, getDeploymentsFailingUpgrade(context.instanceId()));

        // Application change completes
        context.deploy();
        assertFalse("Change deployed", context.instance().change().hasTargets());

        // New versions is released and upgrade fails in test environments
        Version version = Version.fromString("7.1");
        tester.controllerTester().upgradeSystem(version);
        tester.upgrader().maintain();
        context.failDeployment(systemTest)
               .failDeployment(stagingTest);
        reporter.maintain();
        assertEquals(2, getDeploymentsFailingUpgrade(context.instanceId()));

        // Test and staging pass and upgrade fails in production
        context.runJob(systemTest)
               .runJob(stagingTest)
               .failDeployment(productionUsWest1);
        reporter.maintain();
        assertEquals(1, getDeploymentsFailingUpgrade(context.instanceId()));

        // Upgrade eventually succeeds
        context.runJob(productionUsWest1);
        assertFalse("Upgrade deployed", context.instance().change().hasTargets());
        reporter.maintain();
        assertEquals(0, getDeploymentsFailingUpgrade(context.instanceId()));
    }

    @Test
    public void deployment_warnings_metric() {
        var tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .region("us-west-1")
                .region("us-east-3")
                .build();
        MetricsReporter reporter = createReporter(tester.controller());
        var context = tester.newDeploymentContext();
        tester.configServer().generateWarnings(context.deploymentIdIn(ZoneId.from("prod", "us-west-1")), 3);
        tester.configServer().generateWarnings(context.deploymentIdIn(ZoneId.from("prod", "us-west-1")), 4);
        context.submit(applicationPackage).deploy();
        reporter.maintain();
        assertEquals(4, getDeploymentWarnings(context.instanceId()));
    }

    @Test
    public void build_time_reporting() {
        var tester = new DeploymentTester();
        var applicationPackage = new ApplicationPackageBuilder().region("us-west-1").build();
        var context = tester.newDeploymentContext()
                            .submit(applicationPackage)
                            .deploy();
        assertEquals(1000, context.lastSubmission().get().buildTime().get().toEpochMilli());

        MetricsReporter reporter = createReporter(tester.controller());
        reporter.maintain();
        assertEquals(tester.clock().instant().getEpochSecond() - 1,
                     getMetric(MetricsReporter.DEPLOYMENT_BUILD_AGE_SECONDS, context.instanceId()));
    }

    @Test
    public void name_service_queue_size_metric() {
        var tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .globalServiceId("default")
                .region("us-west-1")
                .region("us-east-3")
                .build();
        MetricsReporter reporter = createReporter(tester.controller());
        var context = tester.newDeploymentContext()
                            .deferDnsUpdates();
        reporter.maintain();
        assertEquals("Queue is empty initially", 0, metrics.getMetric(MetricsReporter.NAME_SERVICE_REQUESTS_QUEUED).intValue());

        context.submit(applicationPackage).deploy();
        reporter.maintain();
        assertEquals("Deployment queues name services requests", 15, metrics.getMetric(MetricsReporter.NAME_SERVICE_REQUESTS_QUEUED).intValue());

        context.flushDnsUpdates();
        reporter.maintain();
        assertEquals("Queue consumed", 0, metrics.getMetric(MetricsReporter.NAME_SERVICE_REQUESTS_QUEUED).intValue());
    }

    @Test
    public void platform_change_duration() {
        var tester = new ControllerTester();
        var reporter = createReporter(tester.controller());
        var zone = ZoneId.from("prod.eu-west-1");
        tester.zoneRegistry().setUpgradePolicy(UpgradePolicy.create().upgrade(ZoneApiMock.from(zone)));
        var systemUpgrader = new SystemUpgrader(tester.controller(), Duration.ofDays(1)
        );
        tester.configServer().bootstrap(List.of(zone), SystemApplication.configServer);

        // System on initial version
        var version0 = Version.fromString("7.0");
        tester.upgradeSystem(version0);
        reporter.maintain();
        var hosts = tester.configServer().nodeRepository().list(zone, SystemApplication.configServer.id());
        assertPlatformChangeDuration(Duration.ZERO, hosts);

        var targets = List.of(Version.fromString("7.1"), Version.fromString("7.2"));
        for (int i = 0; i < targets.size(); i++) {
            var version = targets.get(i);
            // System starts upgrading to next version
            tester.upgradeController(version);
            reporter.maintain();
            assertPlatformChangeDuration(Duration.ZERO, hosts);
            systemUpgrader.maintain();

            // 30 minutes pass and nothing happens
            tester.clock().advance(Duration.ofMinutes(30));
            runAll(tester::computeVersionStatus, reporter);
            assertPlatformChangeDuration(Duration.ZERO, hosts);

            // 1/3 nodes upgrade within timeout
            assertEquals("Wanted version is raised for all nodes", version,
                         getNodes(zone, hosts, tester).stream()
                                                      .map(Node::wantedVersion)
                                                      .min(Comparator.naturalOrder())
                                                      .get());
            suspend(hosts, zone, tester);
            var firstHost = hosts.get(0);
            upgradeTo(version, List.of(firstHost), zone, tester);

            // 2/3 spend their budget and are reported as failures
            tester.clock().advance(Duration.ofHours(1));
            runAll(tester::computeVersionStatus, reporter);
            assertPlatformChangeDuration(Duration.ZERO, List.of(firstHost));
            assertPlatformChangeDuration(Duration.ofHours(1), hosts.subList(1, hosts.size()));

            // Remaining nodes eventually upgrade
            upgradeTo(version, hosts.subList(1, hosts.size()), zone, tester);
            runAll(tester::computeVersionStatus, reporter);
            assertPlatformChangeDuration(Duration.ZERO, hosts);
            assertEquals(version, tester.controller().readSystemVersion());
            assertPlatformNodeCount(hosts.size(), version);
        }
    }

    @Test
    public void os_change_duration() {
        var tester = new ControllerTester();
        var reporter = createReporter(tester.controller());
        var zone = ZoneId.from("prod.eu-west-1");
        var cloud = CloudName.defaultName();
        tester.zoneRegistry().setOsUpgradePolicy(cloud, UpgradePolicy.create().upgrade(ZoneApiMock.from(zone)));
        var osUpgrader = new OsUpgrader(tester.controller(), Duration.ofDays(1), CloudName.defaultName());
        var statusUpdater = new OsVersionStatusUpdater(tester.controller(), Duration.ofDays(1)
        );
        tester.configServer().bootstrap(List.of(zone), SystemApplication.configServerHost, SystemApplication.tenantHost);

        // All nodes upgrade to initial OS version
        var version0 = Version.fromString("8.0");
        tester.controller().upgradeOsIn(cloud, version0, Optional.empty(), false);
        osUpgrader.maintain();
        tester.configServer().setOsVersion(version0, SystemApplication.tenantHost.id(), zone);
        tester.configServer().setOsVersion(version0, SystemApplication.configServerHost.id(), zone);
        runAll(statusUpdater, reporter);
        List<Node> hosts = tester.configServer().nodeRepository().list(zone);
        assertOsChangeDuration(Duration.ZERO, hosts);

        var targets = List.of(Version.fromString("8.1"), Version.fromString("8.2"));
        var allVersions = Stream.concat(Stream.of(version0), targets.stream()).collect(Collectors.toSet());
        for (int i = 0; i < targets.size(); i++) {
            var currentVersion = i == 0 ? version0 : targets.get(i - 1);
            var nextVersion = targets.get(i);
            // System starts upgrading to next OS version
            tester.controller().upgradeOsIn(cloud, nextVersion, Optional.empty(), false);
            runAll(osUpgrader, statusUpdater, reporter);
            assertOsChangeDuration(Duration.ZERO, hosts);
            assertOsNodeCount(hosts.size(), currentVersion);

            // Over 30 minutes pass and nothing happens
            tester.clock().advance(Duration.ofMinutes(30).plus(Duration.ofSeconds(1)));
            runAll(statusUpdater, reporter);
            assertOsChangeDuration(Duration.ZERO, hosts);

            // Nodes are told to upgrade, but do not suspend yet
            assertEquals("Wanted OS version is raised for all nodes", nextVersion,
                         tester.configServer().nodeRepository().list(zone, SystemApplication.tenantHost.id()).stream()
                               .map(Node::wantedOsVersion).min(Comparator.naturalOrder()).get());
            assertTrue("No nodes are suspended", tester.controller().serviceRegistry().configServer()
                                                       .nodeRepository().list(zone).stream()
                                                       .noneMatch(node -> node.serviceState() == Node.ServiceState.allowedDown));

            // Another 30 minutes pass
            tester.clock().advance(Duration.ofMinutes(30));
            runAll(statusUpdater, reporter);
            assertOsChangeDuration(Duration.ZERO, hosts);

            // 3/6 hosts suspend
            var suspendedHosts = hosts.subList(0, 3);
            suspend(suspendedHosts, zone, tester);
            runAll(statusUpdater, reporter);
            assertOsChangeDuration(Duration.ZERO, hosts);

            // Two hosts spend 20 minutes upgrading
            var hostsUpgraded = suspendedHosts.subList(0, 2);
            tester.clock().advance(Duration.ofMinutes(20));
            runAll(statusUpdater, reporter);
            assertOsChangeDuration(Duration.ofMinutes(20), hostsUpgraded);
            upgradeOsTo(nextVersion, hostsUpgraded, zone, tester);
            runAll(statusUpdater, reporter);
            assertOsChangeDuration(Duration.ZERO, hostsUpgraded);
            assertOsNodeCount(hostsUpgraded.size(), nextVersion);

            // One host consumes budget without upgrading
            var brokenHost = suspendedHosts.get(2);
            tester.clock().advance(Duration.ofMinutes(15));
            runAll(statusUpdater, reporter);
            assertOsChangeDuration(Duration.ofMinutes(35), List.of(brokenHost));

            // Host eventually upgrades and is no longer reported
            upgradeOsTo(nextVersion, List.of(brokenHost), zone, tester);
            runAll(statusUpdater, reporter);
            assertOsChangeDuration(Duration.ZERO, List.of(brokenHost));
            assertOsNodeCount(hostsUpgraded.size() + 1, nextVersion);

            // Remaining hosts suspend and upgrade successfully
            var remainingHosts = hosts.subList(3, hosts.size());
            suspend(remainingHosts, zone, tester);
            upgradeOsTo(nextVersion, remainingHosts, zone, tester);
            runAll(statusUpdater, reporter);
            assertOsChangeDuration(Duration.ZERO, hosts);
            assertOsNodeCount(hosts.size(), nextVersion);
            assertOsNodeCount(0, currentVersion);

            // Dimensions used for node count metric are only known OS versions
            Set<Version> versionDimensions = metrics.getMetrics((dimensions) -> true)
                                                    .entrySet()
                                                    .stream()
                                                    .filter(kv -> kv.getValue().containsKey(MetricsReporter.OS_NODE_COUNT))
                                                    .map(kv -> kv.getKey().getDimensions())
                                                    .map(dimensions -> dimensions.get("currentVersion"))
                                                    .map(Version::fromString)
                                                    .collect(Collectors.toSet());
            assertTrue("Reports only OS versions", allVersions.containsAll(versionDimensions));
        }
    }

    @Test
    public void broken_system_version() {
        var tester = new DeploymentTester().atMondayMorning();
        var ctx = tester.newDeploymentContext();
        var applicationPackage = new ApplicationPackageBuilder().upgradePolicy("canary").region("us-west-1").build();

        // Application deploys successfully on current system version
        ctx.submit(applicationPackage).deploy();
        tester.controllerTester().computeVersionStatus();
        var reporter = createReporter(tester.controller());
        reporter.maintain();
        assertEquals(VespaVersion.Confidence.high, tester.controller().readVersionStatus().systemVersion().get().confidence());
        assertEquals(0, metrics.getMetric(MetricsReporter.BROKEN_SYSTEM_VERSION));

        // System upgrades. Canary upgrade fails
        Version version0 = Version.fromString("6.2");
        tester.controllerTester().upgradeSystem(version0);
        tester.upgrader().maintain();
        assertEquals(Change.of(version0), ctx.instance().change());
        ctx.failDeployment(stagingTest);
        tester.controllerTester().computeVersionStatus();
        assertEquals(VespaVersion.Confidence.broken, tester.controller().readVersionStatus().systemVersion().get().confidence());
        reporter.maintain();
        assertEquals(1, metrics.getMetric(MetricsReporter.BROKEN_SYSTEM_VERSION));

        // Canary is healed and confidence is raised
        ctx.deployPlatform(version0);
        tester.controllerTester().computeVersionStatus();
        assertEquals(VespaVersion.Confidence.high, tester.controller().readVersionStatus().systemVersion().get().confidence());
        reporter.maintain();
        assertEquals(0, metrics.getMetric(MetricsReporter.BROKEN_SYSTEM_VERSION));
    }

    private void assertNodeCount(String metric, int n, Version version) {
        long nodeCount = metrics.getMetric((dimensions) -> version.toFullString().equals(dimensions.get("currentVersion")), metric)
                                .stream()
                                .map(Number::longValue)
                                .findFirst()
                                .orElseThrow(() -> new IllegalArgumentException("Expected to find metric for version " + version));
        assertEquals("Expected number of nodes are on " + version.toFullString(), n, nodeCount);
    }

    private void assertPlatformNodeCount(int n, Version version) {
        assertNodeCount(MetricsReporter.PLATFORM_NODE_COUNT, n, version);
    }

    private void assertOsNodeCount(int n, Version version) {
        assertNodeCount(MetricsReporter.OS_NODE_COUNT, n, version);
    }

    private void runAll(Runnable... runnables) {
        for (var r : runnables) r.run();
    }

    private void upgradeTo(Version version, List<Node> nodes, ZoneId zone, ControllerTester tester) {
        tester.configServer().setVersion(version, nodes, zone);
        resume(nodes, zone, tester);
    }

    private void upgradeOsTo(Version version, List<Node> nodes, ZoneId zone, ControllerTester tester) {
        tester.configServer().setOsVersion(version, nodes, zone);
        resume(nodes, zone, tester);
    }

    private void resume(List<Node> nodes, ZoneId zone, ControllerTester tester) {
        updateNodes(nodes, (builder) -> builder.serviceState(Node.ServiceState.expectedUp).suspendedSince(null),
                    zone, tester);
    }

    private void suspend(List<Node> nodes, ZoneId zone, ControllerTester tester) {
        var now = tester.clock().instant();
        updateNodes(nodes, (builder) -> builder.serviceState(Node.ServiceState.allowedDown).suspendedSince(now),
                    zone, tester);
    }

    private List<Node> getNodes(ZoneId zone, List<Node> nodes, ControllerTester tester) {
        return tester.configServer().nodeRepository().list(zone, nodes.stream()
                                                                      .map(Node::hostname)
                                                                      .collect(Collectors.toList()));
    }

    private void updateNodes(List<Node> nodes, UnaryOperator<Node.Builder> builderOps, ZoneId zone,
                             ControllerTester tester) {
        var currentNodes = getNodes(zone, nodes, tester);
        var updatedNodes = currentNodes.stream()
                                       .map(node -> builderOps.apply(new Node.Builder(node)).build())
                                       .collect(Collectors.toList());
        tester.configServer().nodeRepository().putNodes(zone, updatedNodes);
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

    private Duration getChangeDuration(String metric, HostName hostname) {
        return metrics.getMetric((dimensions) -> hostname.value().equals(dimensions.get("host")), metric)
                      .map(n -> Duration.ofSeconds(n.longValue()))
                      .orElseThrow(() -> new IllegalArgumentException("Expected to find metric for " + hostname));
    }

    private void assertPlatformChangeDuration(Duration duration, List<Node> nodes) {
        for (var node : nodes) {
            assertEquals("Platform change duration of " + node.hostname(),
                         duration, getChangeDuration(MetricsReporter.PLATFORM_CHANGE_DURATION, node.hostname()));
        }
    }

    private void assertOsChangeDuration(Duration duration, List<Node> nodes) {
        for (var node : nodes) {
            assertEquals("OS change duration of " + node.hostname(),
                         duration, getChangeDuration(MetricsReporter.OS_CHANGE_DURATION, node.hostname()));
        }
    }

    private Number getMetric(String name, ApplicationId id) {
        return metrics.getMetric((dimensions) -> id.tenant().value().equals(dimensions.get("tenant")) &&
                                                 appDimension(id).equals(dimensions.get("app")),
                                 name)
                      .orElseThrow(() -> new RuntimeException("Expected metric to exist for " + id));
    }

    private Number getMetric(String name, String operator) {
        return metrics.getMetric((dimensions) -> operator.equals(dimensions.get("operator")),
                name)
                .orElseThrow(() -> new RuntimeException("Expected metric to exist for " + operator));
    }

    private MetricsReporter createReporter(Controller controller) {
        return new MetricsReporter(controller, metrics);
    }

    private static String appDimension(ApplicationId id) {
        return id.application().value() + "." + id.instance().value();
    }

}

