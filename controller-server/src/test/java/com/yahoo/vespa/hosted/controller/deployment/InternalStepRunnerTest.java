// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.LogEntry;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeFilter;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TestReport;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud.Status;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockMailer;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.maintenance.JobRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
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
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.noTests;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.running;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.success;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.failed;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.succeeded;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.unfinished;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author jonmv
 * @author freva
 */
public class InternalStepRunnerTest {

    private DeploymentTester tester;
    private DeploymentContext app;

    @BeforeEach
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
                                    .application(app.testerId().id(), DeploymentContext.stagingTest.zone()).get()
                                    .applicationPackage().deploymentSpec();
        assertTrue(spec.instance(app.testerId().id().instance()).isPresent());
        assertEquals("domain", spec.athenzDomain().get().value());
        assertEquals("service", spec.athenzService().get().value());
    }

    @Test
    public void retriesDeploymentForOneHour() {
        RuntimeException exception = new ConfigServerException(ConfigServerException.ErrorCode.APPLICATION_LOCK_FAILURE,
                                                               "Exception to retry",
                                                               "test failure");
        tester.configServer().throwOnNextPrepare(exception);
        tester.jobs().deploy(app.instanceId(), DeploymentContext.devUsEast1, Optional.empty(), applicationPackage());
        assertEquals(unfinished, tester.jobs().last(app.instanceId(), DeploymentContext.devUsEast1).get().stepStatuses().get(Step.deployReal));

        tester.configServer().throwOnNextPrepare(exception);
        tester.runner().run();
        assertEquals(unfinished, tester.jobs().last(app.instanceId(), DeploymentContext.devUsEast1).get().stepStatuses().get(Step.deployReal));

        tester.clock().advance(Duration.ofHours(1).plusSeconds(1));
        tester.configServer().throwOnNextPrepare(exception);
        tester.runner().run();
        assertEquals(failed, tester.jobs().last(app.instanceId(), DeploymentContext.devUsEast1).get().stepStatuses().get(Step.deployReal));
        assertEquals(deploymentFailed, tester.jobs().last(app.instanceId(), DeploymentContext.devUsEast1).get().status());
    }

    @Test
    public void restartsServicesAndWaitsForRestartAndReboot() {
        RunId id = app.newRun(DeploymentContext.productionUsCentral1);
        ZoneId zone = id.type().zone();
        HostName host = tester.configServer().hostFor(instanceId, zone);

        tester.runner().run();
        assertEquals(succeeded, tester.jobs().run(id).stepStatuses().get(Step.deployReal));

        tester.configServer().convergeServices(app.instanceId(), zone);
        assertEquals(unfinished, tester.jobs().run(id).stepStatuses().get(Step.installReal));

        tester.configServer().nodeRepository().doRestart(app.deploymentIdIn(zone), Optional.of(host));
        tester.configServer().nodeRepository().requestReboot(app.deploymentIdIn(zone), Optional.of(host));
        tester.runner().run();
        assertEquals(unfinished, tester.jobs().run(id).stepStatuses().get(Step.installReal));

        tester.clock().advance(InternalStepRunner.Timeouts.of(system()).noNodesDown().plus(Duration.ofSeconds(1)));
        tester.runner().run();
        assertEquals(installationFailed, tester.jobs().run(id).status());
    }

    @Test
    public void waitsForEndpointsAndTimesOut() {
        app.newRun(DeploymentContext.systemTest);

        // Tester endpoint fails to show up for staging tests, and the real deployment for system tests.
        var testZone = DeploymentContext.systemTest.zone();
        var stagingZone = DeploymentContext.stagingTest.zone();
        tester.newDeploymentContext(app.testerId().id())
              .deferLoadBalancerProvisioningIn(testZone.environment());
        tester.newDeploymentContext(app.instanceId())
              .deferLoadBalancerProvisioningIn(stagingZone.environment());

        tester.runner().run();
        tester.configServer().convergeServices(app.instanceId(), DeploymentContext.stagingTest.zone());
        tester.runner().run();
        tester.configServer().convergeServices(app.instanceId(), DeploymentContext.systemTest.zone());
        tester.configServer().convergeServices(app.testerId().id(), DeploymentContext.systemTest.zone());
        tester.configServer().convergeServices(app.instanceId(), DeploymentContext.stagingTest.zone());
        tester.configServer().convergeServices(app.testerId().id(), DeploymentContext.stagingTest.zone());
        tester.runner().run();

        tester.clock().advance(InternalStepRunner.Timeouts.of(system()).endpoint().plus(Duration.ofSeconds(1)));
        tester.runner().run();
        assertEquals(failed, tester.jobs().last(app.instanceId(), DeploymentContext.systemTest).get().stepStatuses().get(Step.installReal));
    }

    @Test
    public void timesOutWithoutInstallationProgress() {
        tester.controllerTester().upgradeSystem(new Version("7.1"));
        tester.controllerTester().computeVersionStatus();
        tester.upgrader().maintain();
        app.newRun(DeploymentContext.systemTest);

        // Node is down too long in system test, and no nodes go down in staging.
        tester.runner().run();
        tester.configServer().setVersion(tester.controller().readSystemVersion(), app.testerId().id(), DeploymentContext.systemTest.zone());
        tester.configServer().convergeServices(app.testerId().id(), DeploymentContext.systemTest.zone());
        tester.configServer().setVersion(tester.controller().readSystemVersion(), app.testerId().id(), DeploymentContext.stagingTest.zone());
        tester.configServer().convergeServices(app.testerId().id(), DeploymentContext.stagingTest.zone());
        tester.runner().run();
        assertEquals(succeeded, tester.jobs().last(app.instanceId(), DeploymentContext.systemTest).get().stepStatuses().get(Step.installTester));
        assertEquals(succeeded, tester.jobs().last(app.instanceId(), DeploymentContext.stagingTest).get().stepStatuses().get(Step.installTester));

        Node systemTestNode = tester.configServer().nodeRepository().list(DeploymentContext.systemTest.zone(),
                                                                          NodeFilter.all().applications(app.instanceId())).iterator().next();
        tester.clock().advance(InternalStepRunner.Timeouts.of(system()).noNodesDown().minus(Duration.ofSeconds(1)));
        tester.configServer().nodeRepository().putNodes(DeploymentContext.systemTest.zone(),
                                                        Node.builder(systemTestNode)
                                                            .serviceState(Node.ServiceState.allowedDown)
                                                            .suspendedSince(tester.clock().instant())
                                                            .build());
        tester.runner().run();
        assertEquals(unfinished, tester.jobs().last(app.instanceId(), DeploymentContext.systemTest).get().stepStatuses().get(Step.installReal));
        assertEquals(unfinished, tester.jobs().last(app.instanceId(), DeploymentContext.stagingTest).get().stepStatuses().get(Step.installInitialReal));

        tester.clock().advance(Duration.ofSeconds(2));
        tester.runner().run();
        assertEquals(unfinished, tester.jobs().last(app.instanceId(), DeploymentContext.systemTest).get().stepStatuses().get(Step.installReal));
        assertEquals(failed, tester.jobs().last(app.instanceId(), DeploymentContext.stagingTest).get().stepStatuses().get(Step.installInitialReal));

        tester.clock().advance(InternalStepRunner.Timeouts.of(system()).statelessNodesDown().minus(Duration.ofSeconds(3)));
        tester.runner().run();
        assertEquals(unfinished, tester.jobs().last(app.instanceId(), DeploymentContext.systemTest).get().stepStatuses().get(Step.installReal));

        tester.clock().advance(Duration.ofSeconds(2));
        tester.runner().run();
        assertEquals(failed, tester.jobs().last(app.instanceId(), DeploymentContext.systemTest).get().stepStatuses().get(Step.installReal));
    }

    @Test
    public void startingTestsFailsIfDeploymentExpires() {
        app.newRun(DeploymentContext.systemTest);
        tester.runner().run();
        tester.configServer().convergeServices(app.instanceId(), DeploymentContext.systemTest.zone());
        tester.runner().run();
        assertEquals(succeeded, tester.jobs().last(app.instanceId(), DeploymentContext.systemTest).get().stepStatuses().get(Step.installReal));
        assertEquals(unfinished, tester.jobs().last(app.instanceId(), DeploymentContext.systemTest).get().stepStatuses().get(Step.installTester));

        tester.applications().deactivate(app.instanceId(), DeploymentContext.systemTest.zone());
        tester.configServer().convergeServices(app.testerId().id(), DeploymentContext.systemTest.zone());
        tester.runner().run();
        assertEquals(succeeded, tester.jobs().last(app.instanceId(), DeploymentContext.systemTest).get().stepStatuses().get(Step.installTester));
        assertEquals(failed, tester.jobs().last(app.instanceId(), DeploymentContext.systemTest).get().stepStatuses().get(Step.startTests));
        assertTrue(tester.jobs().last(app.instanceId(), DeploymentContext.systemTest).get().hasEnded());
        assertTrue(tester.jobs().last(app.instanceId(), DeploymentContext.systemTest).get().hasFailed());
    }

    @Test
    public void alternativeEndpointsAreDetected() {
        var systemTestZone =  DeploymentContext.systemTest.zone();
        var stagingZone =  DeploymentContext.stagingTest.zone();
        tester.controllerTester().zoneRegistry().exclusiveRoutingIn(ZoneApiMock.from(systemTestZone), ZoneApiMock.from(stagingZone));
        var applicationPackage = new ApplicationPackageBuilder()
                .athenzIdentity(AthenzDomain.from("domain"), AthenzService.from("service"))
                .upgradePolicy("default")
                .region("us-central-1")
                .parallel("us-west-1", "us-east-3")
                .build();
        app.submit(applicationPackage)
           .triggerJobs();

        tester.runner().run();
        assertEquals(unfinished, tester.jobs().last(app.instanceId(), DeploymentContext.systemTest).get().stepStatuses().get(Step.installReal));
        assertEquals(unfinished, tester.jobs().last(app.instanceId(), DeploymentContext.systemTest).get().stepStatuses().get(Step.installTester));

        app.flushDnsUpdates();
        tester.configServer().convergeServices(app.instanceId(), DeploymentContext.systemTest.zone());
        tester.configServer().convergeServices(app.testerId().id(), DeploymentContext.systemTest.zone());
        tester.runner().run();
        assertEquals(succeeded, tester.jobs().last(app.instanceId(), DeploymentContext.systemTest).get().stepStatuses().get(Step.installReal));
        assertEquals(succeeded, tester.jobs().last(app.instanceId(), DeploymentContext.systemTest).get().stepStatuses().get(Step.installTester));
    }

    @Test
    public void noTestsThenErrorIsError() {
        RunId id = app.startSystemTestTests();
        Run run = tester.jobs().run(id);
        run = run.with(noTests, new LockedStep(() -> { }, Step.endTests));
        assertFalse(run.hasFailed());
        run = run.with(RunStatus.error, new LockedStep(() -> { }, Step.deactivateReal));
        assertTrue(run.hasFailed());
        assertEquals(RunStatus.error, run.status());
    }

    @Test
    public void noTestsThenSuccessIsNoTests() {
        RunId id = app.startSystemTestTests();
        tester.cloud().set(Status.NO_TESTS);
        tester.runner().run();
        assertEquals(succeeded, tester.jobs().run(id).stepStatuses().get(Step.endTests));
        Run run = tester.jobs().run(id);
        assertEquals(noTests, run.status());
    }

    @Test
    public void testsFailIfTesterRestarts() {
        RunId id = app.startSystemTestTests();
        tester.cloud().set(TesterCloud.Status.NOT_STARTED);
        tester.runner().run();
        assertEquals(failed, tester.jobs().run(id).stepStatuses().get(Step.endTests));
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
        assertEquals(failed, tester.jobs().run(id).stepStatuses().get(Step.endTests));
    }

    @Test
    public void testsFailIfTestsErr() {
        RunId id = app.startSystemTestTests();
        tester.cloud().add(new LogEntry(0, Instant.ofEpochMilli(123), error, "Error!"));
        tester.cloud().set(TesterCloud.Status.ERROR);

        long lastId = tester.jobs().details(id).get().lastId().getAsLong();
        tester.runner().run();
        assertEquals(failed, tester.jobs().run(id).stepStatuses().get(Step.endTests));
        assertTestLogEntries(id, Step.endTests,
                             new LogEntry(lastId + 1, Instant.ofEpochMilli(123), error, "Error!"),
                             new LogEntry(lastId + 2, tester.clock().instant(), info, "Tester failed running its tests!"));
    }

    @Test
    public void testsSucceedWhenTheyDoRemotely() {
        RunId id = app.startSystemTestTests();
        tester.runner().run();
        assertEquals(unfinished, tester.jobs().run(id).stepStatuses().get(Step.endTests));
        var testZone = DeploymentContext.systemTest.zone();
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
        assertEquals(succeeded, tester.jobs().run(id).stepStatuses().get(Step.endTests));
    }

    @Test
    public void testCanBeReset() {
        RunId id = app.startSystemTestTests();
        tester.cloud().add(new LogEntry(0, Instant.ofEpochMilli(123), info, "Not enough data!"));
        tester.cloud().set(TesterCloud.Status.INCONCLUSIVE);
        tester.cloud().testReport(TestReport.fromJson("{\"foo\":1}"));

        long lastId1 = tester.jobs().details(id).get().lastId().getAsLong();
        Instant instant1 = tester.clock().instant();
        tester.runner().run();
        assertEquals(unfinished, tester.jobs().run(id).stepStatuses().get(Step.endTests));
        assertEquals(running, tester.jobs().run(id).status());
        tester.cloud().clearLog();

        // Test sleeps for a while.
        tester.runner().run();
        assertEquals(unfinished, tester.jobs().run(id).stepStatuses().get(Step.deployTester));
        Instant nextAttemptAt = tester.clock().instant().plusSeconds(1800);
        tester.clock().advance(Duration.ofSeconds(1799));
        tester.runner().run();
        assertEquals(unfinished, tester.jobs().run(id).stepStatuses().get(Step.deployTester));

        tester.clock().advance(JobRunner.jobTimeout);
        var testZone = DeploymentContext.systemTest.zone();
        tester.runner().run();
        app.flushDnsUpdates();
        tester.configServer().convergeServices(app.instanceId(), testZone);
        tester.configServer().convergeServices(app.testerId().id(), testZone);
        tester.runner().run();
        assertEquals(unfinished, tester.jobs().run(id).stepStatuses().get(Step.endTests));
        assertTrue(tester.jobs().run(id).steps().get(Step.endTests).startTime().isPresent());

        tester.cloud().set(TesterCloud.Status.SUCCESS);
        tester.cloud().testReport(TestReport.fromJson("{\"bar\":2}"));
        long lastId2 = tester.jobs().details(id).get().lastId().getAsLong();
        tester.runner().run();
        assertEquals(success, tester.jobs().run(id).status());

        assertTestLogEntries(id, Step.endTests,
                             new LogEntry(lastId1 + 1, Instant.ofEpochMilli(123), info, "Not enough data!"),
                             new LogEntry(lastId1 + 2, instant1, info, "Tests were inconclusive, and will run again at " + nextAttemptAt + "."),
                             new LogEntry(lastId1 + 15, instant1, info, "### Run will reset, and start over at " + nextAttemptAt),
                             new LogEntry(lastId1 + 16, instant1, info, ""),
                             new LogEntry(lastId2 + 1, tester.clock().instant(), info, "Tests completed successfully."));

        assertEquals("[{\"foo\":1},{\"bar\":2}]", tester.jobs().getTestReports(id).get());
    }

    @Test
    public void deployToDev() {
        ZoneId zone = DeploymentContext.devUsEast1.zone();
        tester.jobs().deploy(app.instanceId(), DeploymentContext.devUsEast1, Optional.empty(), applicationPackage());
        tester.runner().run();
        RunId id = tester.jobs().last(app.instanceId(), DeploymentContext.devUsEast1).get().id();
        assertEquals(unfinished, tester.jobs().run(id).stepStatuses().get(Step.installReal));

        Version version = new Version("7.8.9");
        tester.controllerTester().upgradeSystem(version);
        Future<?> concurrentDeployment = Executors.newSingleThreadExecutor().submit(() -> {
            tester.jobs().deploy(app.instanceId(), DeploymentContext.devUsEast1, Optional.of(version), applicationPackage());
        });
        while ( ! concurrentDeployment.isDone())
            tester.runner().run();
        assertEquals(id.number() + 1, tester.jobs().last(app.instanceId(), DeploymentContext.devUsEast1).get().id().number());

        ApplicationPackage otherPackage = new ApplicationPackageBuilder().region("us-central-1").build();
        tester.jobs().deploy(app.instanceId(), DeploymentContext.perfUsEast3, Optional.empty(), otherPackage);

        tester.runner().run(); // Job run order determined by JobType enum order per application.
        tester.configServer().convergeServices(app.instanceId(), zone);
        assertEquals(unfinished, tester.jobs().run(id).stepStatuses().get(Step.installReal));

        tester.configServer().setVersion(version, app.instanceId(), zone);
        tester.runner().run();
        assertEquals(1, tester.jobs().active().size());
        assertEquals(version, tester.instance(app.instanceId()).deployments().get(zone).version());
    }

    @Test
    public void notificationIsSent() {
        app.submit().failDeployment(DeploymentContext.systemTest);
        MockMailer mailer = tester.controllerTester().serviceRegistry().mailer();
        assertEquals(1, mailer.inbox("a@b").size());
        assertEquals("Vespa application tenant.application: System test failing due to system error",
                     mailer.inbox("a@b").get(0).subject());
        assertEquals(1, mailer.inbox("b@a").size());
        assertEquals("Vespa application tenant.application: System test failing due to system error",
                     mailer.inbox("b@a").get(0).subject());

        // Re-run failing causes no additional email to be sent.
        app.failDeployment(DeploymentContext.systemTest);
        assertEquals(1, mailer.inbox("a@b").size());
        assertEquals(1, mailer.inbox("b@a").size());

        // Failure with new package causes new email to be sent.
        app.submit().failDeployment(DeploymentContext.systemTest);
        assertEquals(2, mailer.inbox("a@b").size());
        assertEquals(2, mailer.inbox("b@a").size());
    }

    @Test
    public void vespaLogIsCopied() {
        // Tests fail. We should get logs. This fails too, on the first attempt.
        tester.controllerTester().computeVersionStatus();
        RunId id = app.startSystemTestTests();
        tester.cloud().set(TesterCloud.Status.ERROR);
        tester.configServer().setLogStream(() -> { throw new ConfigServerException(ConfigServerException.ErrorCode.NOT_FOUND, "404", "context"); });
        long lastId = tester.jobs().details(id).get().lastId().getAsLong();
        tester.runner().run();
        assertEquals(failed, tester.jobs().run(id).stepStatuses().get(Step.endTests));
        assertEquals(unfinished, tester.jobs().run(id).stepStatuses().get(Step.copyVespaLogs));
        assertTestLogEntries(id, Step.copyVespaLogs,
                             new LogEntry(lastId + 2, tester.clock().instant(), info,
                                          "Found no logs, but will retry"));

        // Config servers now provide the log, and we get it.
        tester.configServer().setLogStream(() -> vespaLog(tester.clock().instant()));
        tester.runner().run();
        assertEquals(failed, tester.jobs().run(id).stepStatuses().get(Step.endTests));
        assertTestLogEntries(id, Step.copyVespaLogs,
                             new LogEntry(lastId + 2, tester.clock().instant(), info,
                                          "Found no logs, but will retry"),
                             new LogEntry(lastId + 3, tester.clock().instant().minusSeconds(4), info,
                                          "17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\tcontainer\tstdout\n" +
                                          "ERROR: Bundle canary-application [71] Unable to get module class path. (java.lang.NullPointerException)"),
                             new LogEntry(lastId + 4, tester.clock().instant().minusSeconds(4), info,
                                          "17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\tcontainer\tstdout\n" +
                                          "ERROR: Bundle canary-application [71] Unable to get module class path. (java.lang.NullPointerException)"),
                             new LogEntry(lastId + 5, tester.clock().instant().minusSeconds(4), info,
                                          "17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\tcontainer\tstdout\n" +
                                          "ERROR: Bundle canary-application [71] Unable to get module class path. (java.lang.NullPointerException)"),
                             new LogEntry(lastId + 6, tester.clock().instant().minusSeconds(4), info,
                                          "17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\tcontainer\tstdout\n" +
                                          "ERROR: Bundle canary-application [71] Unable to get module class path. (java.lang.NullPointerException)"),
                             new LogEntry(lastId + 7, tester.clock().instant().minusSeconds(3), info,
                                          "17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\tcontainer\tstdout\n" +
                                          "ERROR: Bundle canary-application [71] Unable to get module class path. (java.lang.NullPointerException)"),
                             new LogEntry(lastId + 8, tester.clock().instant().minusSeconds(3), warning,
                                          "17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\tcontainer\tstderr\n" +
                                          "java.lang.NullPointerException\n\tat org.apache.felix.framework.BundleRevisionImpl.calculateContentPath(BundleRevisionImpl.java:438)\n\tat org.apache.felix.framework.BundleRevisionImpl.initializeContentPath(BundleRevisionImpl.java:371)"),
                             new LogEntry(lastId + 9, tester.clock().instant().minusSeconds(3), info,
                                          "17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\tcontainer\tstdout\n" +
                                          "ERROR: Bundle canary-application [71] Unable to get module class path. (java.lang.NullPointerException)"),
                             new LogEntry(lastId + 10, tester.clock().instant().minusSeconds(3), warning,
                                          "17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\tcontainer\tstderr\n" +
                                          "java.lang.NullPointerException\n\tat org.apache.felix.framework.BundleRevisionImpl.calculateContentPath(BundleRevisionImpl.java:438)\n\tat org.apache.felix.framework.BundleRevisionImpl.initializeContentPath(BundleRevisionImpl.java:371)"));
    }

    @Test
    public void realDeploymentRequiresForTesterCert() {
        List<ZoneApiMock> zones = List.of(ZoneApiMock.fromId("test.aws-us-east-1c"),
                                          ZoneApiMock.fromId("staging.aws-us-east-1c"),
                                          ZoneApiMock.fromId("prod.aws-us-east-1c"));
        ControllerTester wrapped = new ControllerTester(SystemName.Public);
        wrapped.zoneRegistry()
               .setZones(zones)
               .setRoutingMethod(zones, RoutingMethod.exclusive);
        tester = new DeploymentTester(wrapped);
        tester.configServer().bootstrap(tester.controllerTester().zoneRegistry().zones().all().ids(), SystemApplication.values());
        app = tester.newDeploymentContext();
        RunId id = app.newRun(DeploymentContext.systemTest);
        tester.configServer().throwOnPrepare(instanceId -> {
            if (instanceId.instance().isTester())
                throw new ConfigServerException(ConfigServerException.ErrorCode.PARENT_HOST_NOT_READY, "provisioning", "deploy tester");
        });
        tester.runner().run();
        assertEquals(unfinished, tester.jobs().run(id).stepStatuses().get(Step.deployTester));
        assertEquals(unfinished, tester.jobs().run(id).stepStatuses().get(Step.deployReal));

        List<X509Certificate> oldTrusted = new ArrayList<>(DeploymentContext.publicApplicationPackage().trustedCertificates());
        X509Certificate oldCert = tester.jobs().run(id).testerCertificate().get();
        oldTrusted.add(oldCert);
        assertEquals(oldTrusted, tester.configServer().application(app.instanceId(), id.type().zone()).get().applicationPackage().trustedCertificates());

        tester.configServer().throwOnNextPrepare(null);
        tester.clock().advance(Duration.ofSeconds(450));
        tester.runner().run();
        assertEquals(succeeded, tester.jobs().run(id).stepStatuses().get(Step.deployTester));
        assertEquals(succeeded, tester.jobs().run(id).stepStatuses().get(Step.deployReal));

        List<X509Certificate> newTrusted = new ArrayList<>(DeploymentContext.publicApplicationPackage().trustedCertificates());
        X509Certificate newCert = tester.jobs().run(id).testerCertificate().get();
        newTrusted.add(newCert);
        assertEquals(newTrusted, tester.configServer().application(app.instanceId(), id.type().zone()).get().applicationPackage().trustedCertificates());
        assertNotEquals(oldCert, newCert);
    }

    @Test
    public void certificateTimeoutAbortsJob() {
        tester = new DeploymentTester(new ControllerTester(SystemName.Public));
        app = tester.newDeploymentContext();
        RunId id = app.startSystemTestTests();

        List<X509Certificate> trusted = new ArrayList<>(DeploymentContext.publicApplicationPackage().trustedCertificates());
        trusted.add(tester.jobs().run(id).testerCertificate().get());
        assertEquals(trusted, tester.configServer().application(app.instanceId(), id.type().zone()).get().applicationPackage().trustedCertificates());

        tester.clock().advance(InternalStepRunner.Timeouts.of(system()).testerCertificate().plus(Duration.ofSeconds(1)));
        tester.runner().run();
        assertEquals(RunStatus.error, tester.jobs().run(id).status());
    }

    private void assertTestLogEntries(RunId id, Step step, LogEntry... entries) {
        assertEquals(List.of(entries), tester.jobs().details(id).get().get(step));
    }

    private static String vespaLog(Instant now) {
        return "-1\t17480180-v6-3.ostk.bm2.prod.ne1.yahoo.com\t5549/832\tcontainer\tContainer.com.yahoo.container.jdisc.ConfiguredApplication\tinfo\tSwitching to the latest deployed set of configurations and components. Application switch number: 2\n" +
               (now.getEpochSecond() - 4) + "." + now.getNano() / 1000 + "\t17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\t5480\tcontainer\tstdout\tinfo\tERROR: Bundle canary-application [71] Unable to get module class path. (java.lang.NullPointerException)\n" +
               (now.getEpochSecond() - 4) + "." + now.getNano() / 1000 + "\t17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\t5480\tcontainer\tstdout\tinfo\tERROR: Bundle canary-application [71] Unable to get module class path. (java.lang.NullPointerException)\n" +
               (now.getEpochSecond() - 3) + "." + now.getNano() / 1000 + "\t17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\t5480\tcontainer\tstdout\tinfo\tERROR: Bundle canary-application [71] Unable to get module class path. (java.lang.NullPointerException)\n" +
               (now.getEpochSecond() - 3) + "." + now.getNano() / 1000 + "\t17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\t5480\tcontainer\tstderr\twarning\tjava.lang.NullPointerException\\n\\tat org.apache.felix.framework.BundleRevisionImpl.calculateContentPath(BundleRevisionImpl.java:438)\\n\\tat org.apache.felix.framework.BundleRevisionImpl.initializeContentPath(BundleRevisionImpl.java:371)";
    }

}
