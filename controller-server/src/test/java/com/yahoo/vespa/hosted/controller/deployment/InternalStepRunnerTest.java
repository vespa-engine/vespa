// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.google.common.collect.ImmutableList;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.RoutingController;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.ConfigChangeActions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.RestartAction;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.ServiceInfo;
import com.yahoo.vespa.hosted.controller.api.integration.LogEntry;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockMailer;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.config.ControllerConfig;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.yahoo.vespa.hosted.controller.api.integration.LogEntry.Type.error;
import static com.yahoo.vespa.hosted.controller.api.integration.LogEntry.Type.info;
import static com.yahoo.vespa.hosted.controller.api.integration.LogEntry.Type.warning;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.applicationPackage;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentTester.instanceId;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.deploymentFailed;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.installationFailed;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.failed;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.succeeded;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.unfinished;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author jonmv
 * @author freva
 */
public class InternalStepRunnerTest {

    private DeploymentTester tester;
    private DeploymentContext app;

    @Before
    public void setup() {
        tester = new DeploymentTester();
        app = tester.newDeploymentContext();
    }

    private SystemName system() {
        return tester.controller().system();
    }

    @Test
    public void canRegisterAndRunDirectly() {
        app.submit().deploy();
    }

    @Test
    public void testerHasAthenzIdentity() {
        app.submit();
        tester.triggerJobs();
        tester.runner().run();
        DeploymentSpec spec = tester.configServer()
                                    .application(app.testerId().id(), JobType.stagingTest.zone(system())).get()
                                    .applicationPackage().deploymentSpec();
        assertTrue(spec.instance(app.testerId().id().instance()).isPresent());
        assertEquals("domain", spec.athenzDomain().get().value());
        assertEquals("service", spec.athenzService().get().value());
    }

    @Test
    public void retriesDeploymentForOneHour() {
        RuntimeException exception = new ConfigServerException(URI.create("https://server"),
                                                               "test failure",
                                                               "Exception to retry",
                                                               ConfigServerException.ErrorCode.APPLICATION_LOCK_FAILURE,
                                                               new RuntimeException("Retry me"));
        tester.configServer().throwOnNextPrepare(exception);
        tester.jobs().deploy(app.instanceId(), JobType.devUsEast1, Optional.empty(), applicationPackage());
        assertEquals(unfinished, tester.jobs().last(app.instanceId(), JobType.devUsEast1).get().stepStatuses().get(Step.deployReal));

        tester.configServer().throwOnNextPrepare(exception);
        tester.runner().run();
        assertEquals(unfinished, tester.jobs().last(app.instanceId(), JobType.devUsEast1).get().stepStatuses().get(Step.deployReal));

        tester.clock().advance(Duration.ofHours(1).plusSeconds(1));
        tester.configServer().throwOnNextPrepare(exception);
        tester.runner().run();
        assertEquals(failed, tester.jobs().last(app.instanceId(), JobType.devUsEast1).get().stepStatuses().get(Step.deployReal));
        assertEquals(deploymentFailed, tester.jobs().last(app.instanceId(), JobType.devUsEast1).get().status());
    }

    @Test
    // TODO jonmv: Change to only wait for restarts, and remove triggering of restarts from runner.
    public void restartsServicesAndWaitsForRestartAndReboot() {
        RunId id = app.newRun(JobType.productionUsCentral1);
        ZoneId zone = id.type().zone(system());
        HostName host = tester.configServer().hostFor(instanceId, zone);

        tester.configServer().setConfigChangeActions(new ConfigChangeActions(List.of(new RestartAction("cluster",
                                                                                                       "container",
                                                                                                       "search",
                                                                                                       List.of(new ServiceInfo("queries",
                                                                                                                               "search",
                                                                                                                               "config",
                                                                                                                               host.value())),
                                                                                                       List.of("Restart it!"))),
                                                                             List.of(),
                                                                             List.of()));
        tester.runner().run();
        assertEquals(succeeded, tester.jobs().run(id).get().stepStatuses().get(Step.deployReal));

        tester.configServer().convergeServices(app.instanceId(), zone);
        assertEquals(unfinished, tester.jobs().run(id).get().stepStatuses().get(Step.installReal));

        tester.configServer().nodeRepository().doRestart(app.deploymentIdIn(zone), Optional.of(host));
        tester.configServer().nodeRepository().requestReboot(app.deploymentIdIn(zone), Optional.of(host));
        tester.runner().run();
        assertEquals(unfinished, tester.jobs().run(id).get().stepStatuses().get(Step.installReal));

        tester.clock().advance(InternalStepRunner.Timeouts.of(system()).noNodesDown().plus(Duration.ofSeconds(1)));
        tester.runner().run();
        assertEquals(installationFailed, tester.jobs().run(id).get().status());
    }

    @Test
    public void waitsForEndpointsAndTimesOut() {
        app.newRun(JobType.systemTest);

        // Tester endpoint fails to show up for staging tests, and the real deployment for system tests.
        var testZone = JobType.systemTest.zone(system());
        var stagingZone = JobType.stagingTest.zone(system());
        tester.newDeploymentContext(app.testerId().id())
              .deferLoadBalancerProvisioningIn(testZone.environment());
        tester.newDeploymentContext(app.instanceId())
              .deferLoadBalancerProvisioningIn(stagingZone.environment());

        tester.runner().run();
        tester.configServer().convergeServices(app.instanceId(), JobType.stagingTest.zone(system()));
        tester.runner().run();
        tester.configServer().convergeServices(app.instanceId(), JobType.systemTest.zone(system()));
        tester.configServer().convergeServices(app.testerId().id(), JobType.systemTest.zone(system()));
        tester.configServer().convergeServices(app.instanceId(), JobType.stagingTest.zone(system()));
        tester.configServer().convergeServices(app.testerId().id(), JobType.stagingTest.zone(system()));
        tester.runner().run();

        tester.clock().advance(InternalStepRunner.Timeouts.of(system()).endpoint().plus(Duration.ofSeconds(1)));
        tester.runner().run();
        assertEquals(failed, tester.jobs().last(app.instanceId(), JobType.systemTest).get().stepStatuses().get(Step.installReal));
    }

    @Test
    public void timesOutWithoutInstallationProgress() {
        tester.controllerTester().upgradeSystem(new Version("7.1"));
        tester.controllerTester().computeVersionStatus();
        tester.upgrader().maintain();
        app.newRun(JobType.systemTest);

        // Node is down too long in system test, and no nodes go down in staging.
        tester.runner().run();
        tester.configServer().setVersion(tester.controller().readSystemVersion(), app.testerId().id(), JobType.systemTest.zone(system()));
        tester.configServer().convergeServices(app.testerId().id(), JobType.systemTest.zone(system()));
        tester.configServer().setVersion(tester.controller().readSystemVersion(), app.testerId().id(), JobType.stagingTest.zone(system()));
        tester.configServer().convergeServices(app.testerId().id(), JobType.stagingTest.zone(system()));
        tester.runner().run();
        assertEquals(succeeded, tester.jobs().last(app.instanceId(), JobType.systemTest).get().stepStatuses().get(Step.installTester));
        assertEquals(succeeded, tester.jobs().last(app.instanceId(), JobType.stagingTest).get().stepStatuses().get(Step.installTester));

        Node systemTestNode = tester.configServer().nodeRepository().list(JobType.systemTest.zone(system()),
                                                                          app.instanceId()).iterator().next();
        tester.clock().advance(InternalStepRunner.Timeouts.of(system()).noNodesDown().minus(Duration.ofSeconds(1)));
        tester.configServer().nodeRepository().putNodes(JobType.systemTest.zone(system()),
                                                        new Node.Builder(systemTestNode)
                                                                     .serviceState(Node.ServiceState.allowedDown)
                                                                     .suspendedSince(tester.clock().instant())
                                                                     .build());
        tester.runner().run();
        assertEquals(unfinished, tester.jobs().last(app.instanceId(), JobType.systemTest).get().stepStatuses().get(Step.installReal));
        assertEquals(unfinished, tester.jobs().last(app.instanceId(), JobType.stagingTest).get().stepStatuses().get(Step.installInitialReal));

        tester.clock().advance(Duration.ofSeconds(2));
        tester.runner().run();
        assertEquals(unfinished, tester.jobs().last(app.instanceId(), JobType.systemTest).get().stepStatuses().get(Step.installReal));
        assertEquals(failed, tester.jobs().last(app.instanceId(), JobType.stagingTest).get().stepStatuses().get(Step.installInitialReal));

        tester.clock().advance(InternalStepRunner.Timeouts.of(system()).nodesDown().minus(Duration.ofSeconds(3)));
        tester.runner().run();
        assertEquals(unfinished, tester.jobs().last(app.instanceId(), JobType.systemTest).get().stepStatuses().get(Step.installReal));

        tester.clock().advance(Duration.ofSeconds(2));
        tester.runner().run();
        assertEquals(failed, tester.jobs().last(app.instanceId(), JobType.systemTest).get().stepStatuses().get(Step.installReal));
    }

    @Test
    public void startingTestsFailsIfDeploymentExpires() {
        app.newRun(JobType.systemTest);
        tester.runner().run();
        tester.configServer().convergeServices(app.instanceId(), JobType.systemTest.zone(system()));
        tester.runner().run();
        assertEquals(succeeded, tester.jobs().last(app.instanceId(), JobType.systemTest).get().stepStatuses().get(Step.installReal));
        assertEquals(unfinished, tester.jobs().last(app.instanceId(), JobType.systemTest).get().stepStatuses().get(Step.installTester));

        tester.applications().deactivate(app.instanceId(), JobType.systemTest.zone(system()));
        tester.configServer().convergeServices(app.testerId().id(), JobType.systemTest.zone(system()));
        tester.runner().run();
        assertEquals(succeeded, tester.jobs().last(app.instanceId(), JobType.systemTest).get().stepStatuses().get(Step.installTester));
        assertEquals(failed, tester.jobs().last(app.instanceId(), JobType.systemTest).get().stepStatuses().get(Step.startTests));
        assertTrue(tester.jobs().last(app.instanceId(), JobType.systemTest).get().hasEnded());
        assertTrue(tester.jobs().last(app.instanceId(), JobType.systemTest).get().hasFailed());
    }

    @Test
    public void alternativeEndpointsAreDetected() {
        var systemTestZone =  JobType.systemTest.zone(system());
        var stagingZone =  JobType.stagingTest.zone(system());
        tester.controllerTester().zoneRegistry().exclusiveRoutingIn(ZoneApiMock.from(systemTestZone), ZoneApiMock.from(stagingZone));
        var applicationPackage = new ApplicationPackageBuilder()
                .athenzIdentity(AthenzDomain.from("domain"), AthenzService.from("service"))
                .upgradePolicy("default")
                .region("us-central-1")
                .parallel("us-west-1", "us-east-3")
                .compileVersion(RoutingController.DIRECT_ROUTING_MIN_VERSION)
                .build();
        app.submit(applicationPackage)
           .triggerJobs();

        tester.runner().run();
        assertEquals(unfinished, tester.jobs().last(app.instanceId(), JobType.systemTest).get().stepStatuses().get(Step.installReal));
        assertEquals(unfinished, tester.jobs().last(app.instanceId(), JobType.systemTest).get().stepStatuses().get(Step.installTester));

        app.flushDnsUpdates();
        tester.configServer().convergeServices(app.instanceId(), JobType.systemTest.zone(system()));
        tester.configServer().convergeServices(app.testerId().id(), JobType.systemTest.zone(system()));
        tester.runner().run();
        assertEquals(succeeded, tester.jobs().last(app.instanceId(), JobType.systemTest).get().stepStatuses().get(Step.installReal));
        assertEquals(succeeded, tester.jobs().last(app.instanceId(), JobType.systemTest).get().stepStatuses().get(Step.installTester));
    }

    @Test
    public void testsFailIfTesterRestarts() {
        RunId id = app.startSystemTestTests();
        tester.cloud().set(TesterCloud.Status.NOT_STARTED);
        tester.runner().run();
        assertEquals(failed, tester.jobs().run(id).get().stepStatuses().get(Step.endTests));
    }

    @Test
    public void testsFailIfTestsFailRemotely() {
        RunId id = app.startSystemTestTests();
        tester.cloud().add(new LogEntry(123, Instant.ofEpochMilli(321), error, "Failure!"));
        tester.cloud().set(TesterCloud.Status.FAILURE);

        long lastId = tester.jobs().details(id).get().lastId().getAsLong();
        tester.runner().run();
        assertTestLogEntries(id, Step.endTests,
                             new LogEntry(lastId + 1, Instant.ofEpochMilli(321), error, "Failure!"),
                             new LogEntry(lastId + 2, tester.clock().instant(), info, "Tests failed."));
        assertEquals(failed, tester.jobs().run(id).get().stepStatuses().get(Step.endTests));
    }

    @Test
    public void testsFailIfTestsErr() {
        RunId id = app.startSystemTestTests();
        tester.cloud().add(new LogEntry(0, Instant.ofEpochMilli(123), error, "Error!"));
        tester.cloud().set(TesterCloud.Status.ERROR);

        long lastId = tester.jobs().details(id).get().lastId().getAsLong();
        tester.runner().run();
        assertEquals(failed, tester.jobs().run(id).get().stepStatuses().get(Step.endTests));
        assertTestLogEntries(id, Step.endTests,
                             new LogEntry(lastId + 1, Instant.ofEpochMilli(123), error, "Error!"),
                             new LogEntry(lastId + 2, tester.clock().instant(), info, "Tester failed running its tests!"));
    }

    @Test
    public void testsSucceedWhenTheyDoRemotely() {
        RunId id = app.startSystemTestTests();
        tester.runner().run();
        assertEquals(unfinished, tester.jobs().run(id).get().stepStatuses().get(Step.endTests));
        var testZone = JobType.systemTest.zone(system());
        Inspector configObject = SlimeUtils.jsonToSlime(tester.cloud().config()).get();
        assertEquals(app.instanceId().serializedForm(), configObject.field("application").asString());
        assertEquals(testZone.value(), configObject.field("zone").asString());
        assertEquals(system().value(), configObject.field("system").asString());
        assertEquals(1, configObject.field("zoneEndpoints").children());
        assertEquals(1, configObject.field("zoneEndpoints").field(testZone.value()).children());

        long lastId = tester.jobs().details(id).get().lastId().getAsLong();
        tester.cloud().add(new LogEntry(0, Instant.ofEpochMilli(123), info, "Ready!"));
        tester.runner().run();
        assertTestLogEntries(id, Step.endTests,
                             new LogEntry(lastId + 1, Instant.ofEpochMilli(123), info, "Ready!"));

        tester.cloud().add(new LogEntry(1, Instant.ofEpochMilli(1234), info, "Steady!"));
        tester.runner().run();
        assertTestLogEntries(id, Step.endTests,
                             new LogEntry(lastId + 1, Instant.ofEpochMilli(123), info, "Ready!"),
                             new LogEntry(lastId + 2, Instant.ofEpochMilli(1234), info, "Steady!"));

        tester.cloud().add(new LogEntry(12, Instant.ofEpochMilli(12345), info, "Success!"));
        tester.cloud().set(TesterCloud.Status.SUCCESS);
        tester.runner().run();
        assertTestLogEntries(id, Step.endTests,
                             new LogEntry(lastId + 1, Instant.ofEpochMilli(123), info, "Ready!"),
                             new LogEntry(lastId + 2, Instant.ofEpochMilli(1234), info, "Steady!"),
                             new LogEntry(lastId + 3, Instant.ofEpochMilli(12345), info, "Success!"),
                             new LogEntry(lastId + 4, tester.clock().instant(), info, "Tests completed successfully."));
        assertEquals(succeeded, tester.jobs().run(id).get().stepStatuses().get(Step.endTests));
    }

    @Test
    public void deployToDev() {
        ZoneId zone = JobType.devUsEast1.zone(system());
        tester.jobs().deploy(app.instanceId(), JobType.devUsEast1, Optional.empty(), applicationPackage());
        tester.runner().run();
        RunId id = tester.jobs().last(app.instanceId(), JobType.devUsEast1).get().id();
        assertEquals(unfinished, tester.jobs().run(id).get().stepStatuses().get(Step.installReal));

        Version version = new Version("7.8.9");
        Future<?> concurrentDeployment = Executors.newSingleThreadExecutor().submit(() -> {
            tester.jobs().deploy(app.instanceId(), JobType.devUsEast1, Optional.of(version), applicationPackage());
        });
        while ( ! concurrentDeployment.isDone())
            tester.runner().run();
        assertEquals(id.number() + 1, tester.jobs().last(app.instanceId(), JobType.devUsEast1).get().id().number());

        ApplicationPackage otherPackage = new ApplicationPackageBuilder().region("us-central-1").build();
        tester.jobs().deploy(app.instanceId(), JobType.perfUsEast3, Optional.empty(), otherPackage);

        tester.runner().run(); // Job run order determined by JobType enum order per application.
        tester.configServer().convergeServices(app.instanceId(), zone);
        assertEquals(unfinished, tester.jobs().run(id).get().stepStatuses().get(Step.installReal));
        assertEquals(applicationPackage().hash(), tester.configServer().application(app.instanceId(), zone).get().applicationPackage().hash());
        assertEquals(otherPackage.hash(), tester.configServer().application(app.instanceId(), JobType.perfUsEast3.zone(system())).get().applicationPackage().hash());

        tester.configServer().setVersion(version, app.instanceId(), zone);
        tester.runner().run();
        assertEquals(1, tester.jobs().active().size());
        assertEquals(version, tester.instance(app.instanceId()).deployments().get(zone).version());
    }

    @Test
    public void notificationIsSent() {
        app.startSystemTestTests();
        tester.cloud().set(TesterCloud.Status.NOT_STARTED);
        tester.runner().run();
        MockMailer mailer = tester.controllerTester().serviceRegistry().mailer();
        assertEquals(1, mailer.inbox("a@b").size());
        assertEquals("Vespa application tenant.application: System test failing due to system error",
                     mailer.inbox("a@b").get(0).subject());
        assertEquals(1, mailer.inbox("b@a").size());
        assertEquals("Vespa application tenant.application: System test failing due to system error",
                     mailer.inbox("b@a").get(0).subject());
    }

    @Test
    public void vespaLogIsCopied() {
        RunId id = app.startSystemTestTests();
        tester.cloud().set(TesterCloud.Status.ERROR);
        tester.configServer().setLogStream(vespaLog);
        long lastId = tester.jobs().details(id).get().lastId().getAsLong();
        tester.runner().run();
        assertEquals(failed, tester.jobs().run(id).get().stepStatuses().get(Step.endTests));
        assertTestLogEntries(id, Step.copyVespaLogs,
                             new LogEntry(lastId + 2, Instant.EPOCH.plus(3554970337935104L, ChronoUnit.MICROS), info,
                                          "17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\tcontainer\tstdout\n" +
                                          "ERROR: Bundle canary-application [71] Unable to get module class path. (java.lang.NullPointerException)"),
                             new LogEntry(lastId + 3, Instant.EPOCH.plus(3554970337947777L, ChronoUnit.MICROS), info,
                                          "17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\tcontainer\tstdout\n" +
                                          "ERROR: Bundle canary-application [71] Unable to get module class path. (java.lang.NullPointerException)"),
                             new LogEntry(lastId + 4, Instant.EPOCH.plus(3554970337947820L, ChronoUnit.MICROS), info,
                                          "17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\tcontainer\tstdout\n" +
                                          "ERROR: Bundle canary-application [71] Unable to get module class path. (java.lang.NullPointerException)"),
                             new LogEntry(lastId + 5, Instant.EPOCH.plus(3554970337947845L, ChronoUnit.MICROS), warning,
                                          "17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\tcontainer\tstderr\n" +
                                          "java.lang.NullPointerException\n\tat org.apache.felix.framework.BundleRevisionImpl.calculateContentPath(BundleRevisionImpl.java:438)\n\tat org.apache.felix.framework.BundleRevisionImpl.initializeContentPath(BundleRevisionImpl.java:371)"));
    }

    @Test
    public void certificateTimeoutAbortsJob() {
        tester.controllerTester().zoneRegistry().setSystemName(SystemName.Public);
        var zones = List.of(ZoneApiMock.fromId("test.aws-us-east-1c"),
                            ZoneApiMock.fromId("staging.aws-us-east-1c"),
                            ZoneApiMock.fromId("prod.aws-us-east-1c"));
        tester.controllerTester().zoneRegistry()
              .setZones(zones)
              .setRoutingMethod(zones, RoutingMethod.exclusive);
        tester.configServer().bootstrap(tester.controllerTester().zoneRegistry().zones().all().ids(), SystemApplication.values());
        RunId id = app.startSystemTestTests();

        List<X509Certificate> trusted = new ArrayList<>(DeploymentContext.publicApplicationPackage().trustedCertificates());
        trusted.add(tester.jobs().run(id).get().testerCertificate().get());
        assertEquals(trusted, tester.configServer().application(app.instanceId(), id.type().zone(system())).get().applicationPackage().trustedCertificates());

        tester.clock().advance(InternalStepRunner.Timeouts.of(system()).testerCertificate().plus(Duration.ofSeconds(1)));
        tester.runner().run();
        assertEquals(RunStatus.aborted, tester.jobs().run(id).get().status());
    }

    private void assertTestLogEntries(RunId id, Step step, LogEntry... entries) {
        assertEquals(ImmutableList.copyOf(entries), tester.jobs().details(id).get().get(step));
    }

    private static final String vespaLog = "-1554970337.084804\t17480180-v6-3.ostk.bm2.prod.ne1.yahoo.com\t5549/832\tcontainer\tContainer.com.yahoo.container.jdisc.ConfiguredApplication\tinfo\tSwitching to the latest deployed set of configurations and components. Application switch number: 2\n" +
                                           "3554970337.935104\t17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\t5480\tcontainer\tstdout\tinfo\tERROR: Bundle canary-application [71] Unable to get module class path. (java.lang.NullPointerException)\n" +
                                           "3554970337.947777\t17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\t5480\tcontainer\tstdout\tinfo\tERROR: Bundle canary-application [71] Unable to get module class path. (java.lang.NullPointerException)\n" +
                                           "3554970337.947820\t17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\t5480\tcontainer\tstdout\tinfo\tERROR: Bundle canary-application [71] Unable to get module class path. (java.lang.NullPointerException)\n" +
                                           "3554970337.947845\t17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\t5480\tcontainer\tstderr\twarning\tjava.lang.NullPointerException\\n\\tat org.apache.felix.framework.BundleRevisionImpl.calculateContentPath(BundleRevisionImpl.java:438)\\n\\tat org.apache.felix.framework.BundleRevisionImpl.initializeContentPath(BundleRevisionImpl.java:371)";

    @Test
    public void generates_correct_tester_flavor() {
        DeploymentSpec spec = DeploymentSpec.fromXml("<deployment version='1.0' athenz-domain='domain' athenz-service='service'>\n" +
                                                     "    <instance id='first'>\n" +
                                                     "        <test tester-flavor=\"d-6-16-100\" />\n" +
                                                     "        <prod>\n" +
                                                     "            <region active=\"true\">us-west-1</region>\n" +
                                                     "            <test>us-west-1</test>\n" +
                                                     "        </prod>\n" +
                                                     "    </instance>\n" +
                                                     "    <instance id='second'>\n" +
                                                     "        <test />\n" +
                                                     "        <staging />\n" +
                                                     "        <prod tester-flavor=\"d-6-16-100\">\n" +
                                                     "            <parallel>\n" +
                                                     "                <region active=\"true\">us-east-3</region>\n" +
                                                     "                <region active=\"true\">us-central-1</region>\n" +
                                                     "            </parallel>\n" +
                                                     "            <region active=\"true\">us-west-1</region>\n" +
                                                     "            <test>us-west-1</test>\n" +
                                                     "        </prod>\n" +
                                                     "    </instance>\n" +
                                                     "</deployment>\n");

        NodeResources firstResources = InternalStepRunner.testerResourcesFor(ZoneId.from("prod", "us-west-1"), spec.requireInstance("first"));
        assertEquals(InternalStepRunner.DEFAULT_TESTER_RESOURCES, firstResources);

        NodeResources secondResources = InternalStepRunner.testerResourcesFor(ZoneId.from("prod", "us-west-1"), spec.requireInstance("second"));
        assertEquals(6, secondResources.vcpu(), 1e-9);
        assertEquals(16, secondResources.memoryGb(), 1e-9);
        assertEquals(100, secondResources.diskGb(), 1e-9);
    }

    @Test
    public void generates_correct_services_xml_using_osgi_based_runtime() {
        generates_correct_services_xml("test_runner_services.xml-cd-osgi", true);
    }

    @Test
    public void generates_correct_services_xml_using_legacy_runtime() {
        generates_correct_services_xml("test_runner_services.xml-cd-legacy", false);
    }

    private void generates_correct_services_xml(String filenameExpectedOutput, boolean useOsgiBasedRuntime) {
        ControllerConfig.Steprunner.Testerapp config = new ControllerConfig.Steprunner.Testerapp.Builder().build();
        assertFile(filenameExpectedOutput,
                new String(InternalStepRunner.servicesXml(
                        true,
                        false,
                        useOsgiBasedRuntime,
                        new NodeResources(2, 12, 75, 1, NodeResources.DiskSpeed.fast, NodeResources.StorageType.local),
                        config)));
    }

    private void assertFile(String resourceName, String actualContent) {
        try {
            Path path = Paths.get("src/test/resources/").resolve(resourceName);
            String expectedContent = new String(Files.readAllBytes(path));
            assertEquals(expectedContent, actualContent);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
