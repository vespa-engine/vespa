// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.google.common.collect.ImmutableList;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.ConfigChangeActions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.RefeedAction;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.RestartAction;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.ServiceInfo;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.LogEntry;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockMailer;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.yahoo.vespa.hosted.controller.api.integration.LogEntry.Type.error;
import static com.yahoo.vespa.hosted.controller.api.integration.LogEntry.Type.info;
import static com.yahoo.vespa.hosted.controller.api.integration.LogEntry.Type.warning;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.applicationPackage;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.publicCdApplicationPackage;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentTester.instanceId;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.deploymentFailed;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.failed;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.succeeded;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.unfinished;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
        tester.jobs().deploy(app.instanceId(), JobType.devUsEast1, Optional.empty(), applicationPackage);
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
    public void refeedRequirementBlocksDeployment() {
        tester.configServer().setConfigChangeActions(new ConfigChangeActions(Collections.emptyList(),
                                                                             singletonList(new RefeedAction("Refeed",
                                                                                                            false,
                                                                                                            "doctype",
                                                                                                            "cluster",
                                                                                                            Collections.emptyList(),
                                                                                                            singletonList("Refeed it!")))));
        tester.jobs().deploy(app.instanceId(), JobType.devUsEast1, Optional.empty(), applicationPackage);
        assertEquals(failed, tester.jobs().last(app.instanceId(), JobType.devUsEast1).get().stepStatuses().get(Step.deployReal));
    }

    @Test
    public void restartsServicesAndWaitsForRestartAndReboot() {
        RunId id = app.newRun(JobType.productionUsCentral1);
        ZoneId zone = id.type().zone(system());
        HostName host = tester.configServer().hostFor(instanceId, zone);

        tester.setEndpoints(app.testerId().id(), JobType.productionUsCentral1.zone(system()));
        tester.runner().run();

        tester.configServer().setConfigChangeActions(new ConfigChangeActions(singletonList(new RestartAction("cluster",
                                                                                                             "container",
                                                                                                             "search",
                                                                                                             singletonList(new ServiceInfo("queries",
                                                                                                                                                                   "search",
                                                                                                                                                                   "config",
                                                                                                                                                                   host.value())),
                                                                                                             singletonList("Restart it!"))),
                                                                             Collections.emptyList()));
        tester.runner().run();
        assertEquals(succeeded, tester.jobs().run(id).get().stepStatuses().get(Step.deployReal));

        tester.configServer().convergeServices(app.instanceId(), zone);
        assertEquals(unfinished, tester.jobs().run(id).get().stepStatuses().get(Step.installReal));

        tester.configServer().nodeRepository().doRestart(app.deploymentIdIn(zone), Optional.of(host));
        tester.configServer().nodeRepository().requestReboot(app.deploymentIdIn(zone), Optional.of(host));
        tester.runner().run();
        assertEquals(unfinished, tester.jobs().run(id).get().stepStatuses().get(Step.installReal));

        tester.clock().advance(InternalStepRunner.installationTimeout.plus(Duration.ofSeconds(1)));
        tester.runner().run();
        assertEquals(RunStatus.error, tester.jobs().run(id).get().status());
    }

    @Test
    public void waitsForEndpointsAndTimesOut() {
        app.newRun(JobType.systemTest);

        // Tester fails to show up for staging tests, and the real deployment for system tests.
        tester.setEndpoints(app.testerId().id(), JobType.systemTest.zone(system()));
        tester.setEndpoints(app.instanceId(), JobType.stagingTest.zone(system()));

        tester.runner().run();
        tester.configServer().convergeServices(app.instanceId(), JobType.stagingTest.zone(system()));
        tester.runner().run();
        tester.configServer().convergeServices(app.instanceId(), JobType.systemTest.zone(system()));
        tester.configServer().convergeServices(app.testerId().id(), JobType.systemTest.zone(system()));
        tester.configServer().convergeServices(app.instanceId(), JobType.stagingTest.zone(system()));
        tester.configServer().convergeServices(app.testerId().id(), JobType.stagingTest.zone(system()));
        tester.runner().run();

        tester.clock().advance(InternalStepRunner.endpointTimeout.plus(Duration.ofSeconds(1)));
        tester.runner().run();
        assertEquals(failed, tester.jobs().last(app.instanceId(), JobType.systemTest).get().stepStatuses().get(Step.installReal));
        assertEquals(failed, tester.jobs().last(app.instanceId(), JobType.stagingTest).get().stepStatuses().get(Step.installTester));
    }

    @Test
    public void startingTestsFailsIfDeploymentExpires() {
        app.newRun(JobType.systemTest);
        tester.runner().run();
        tester.configServer().convergeServices(app.instanceId(), JobType.systemTest.zone(system()));
        tester.setEndpoints(app.instanceId(), JobType.systemTest.zone(system()));
        tester.runner().run();
        assertEquals(succeeded, tester.jobs().last(app.instanceId(), JobType.systemTest).get().stepStatuses().get(Step.installReal));
        assertEquals(unfinished, tester.jobs().last(app.instanceId(), JobType.systemTest).get().stepStatuses().get(Step.installTester));

        tester.applications().deactivate(app.instanceId(), JobType.systemTest.zone(system()));
        tester.setEndpoints(app.testerId().id(), JobType.systemTest.zone(system()));
        tester.configServer().convergeServices(app.testerId().id(), JobType.systemTest.zone(system()));
        tester.runner().run();
        assertEquals(succeeded, tester.jobs().last(app.instanceId(), JobType.systemTest).get().stepStatuses().get(Step.installTester));
        assertEquals(failed, tester.jobs().last(app.instanceId(), JobType.systemTest).get().stepStatuses().get(Step.startTests));
        assertTrue(tester.jobs().last(app.instanceId(), JobType.systemTest).get().hasEnded());
        assertTrue(tester.jobs().last(app.instanceId(), JobType.systemTest).get().hasFailed());
    }

    @Test
    public void startTestsFailsIfDeploymentExpires() {
        app.newRun(JobType.systemTest);
        tester.runner().run();
        tester.configServer().convergeServices(app.instanceId(), JobType.systemTest.zone(system()));
        tester.configServer().convergeServices(app.testerId().id(), JobType.systemTest.zone(system()));
        tester.runner().run();

        tester.applications().deactivate(app.instanceId(), JobType.systemTest.zone(system()));
        tester.runner().run();
        assertEquals(unfinished, tester.jobs().last(app.instanceId(), JobType.systemTest).get().stepStatuses().get(Step.startTests));
    }

    @Test
    public void alternativeEndpointsAreDetected() {
        app.newRun(JobType.systemTest);
        tester.runner().run();;
        tester.configServer().convergeServices(app.instanceId(), JobType.systemTest.zone(system()));
        tester.configServer().convergeServices(app.testerId().id(), JobType.systemTest.zone(system()));
        assertEquals(unfinished, tester.jobs().last(app.instanceId(), JobType.systemTest).get().stepStatuses().get(Step.installReal));
        assertEquals(unfinished, tester.jobs().last(app.instanceId(), JobType.systemTest).get().stepStatuses().get(Step.installTester));
        app.addRoutingPolicy(JobType.systemTest.zone(system()), true);
        app.addTesterRoutingPolicy(JobType.systemTest.zone(system()), true);
        tester.runner().run();;
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
        assertEquals(URI.create(tester.routing().endpoints(new DeploymentId(app.testerId().id(), JobType.systemTest.zone(system()))).get(0).endpoint()),
                     tester.cloud().testerUrl());
        Inspector configObject = SlimeUtils.jsonToSlime(tester.cloud().config()).get();
        assertEquals(app.instanceId().serializedForm(), configObject.field("application").asString());
        assertEquals(JobType.systemTest.zone(system()).value(), configObject.field("zone").asString());
        assertEquals(system().value(), configObject.field("system").asString());
        assertEquals(1, configObject.field("endpoints").children());
        assertEquals(1, configObject.field("endpoints").field(JobType.systemTest.zone(system()).value()).entries());
        configObject.field("endpoints").field(JobType.systemTest.zone(system()).value()).traverse((ArrayTraverser) (__, endpoint) -> assertEquals(tester.routing().endpoints(new DeploymentId(instanceId, JobType.systemTest.zone(system()))).get(0).endpoint(), endpoint.asString()));

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
        tester.jobs().deploy(app.instanceId(), JobType.devUsEast1, Optional.empty(), applicationPackage);
        tester.runner().run();
        RunId id = tester.jobs().last(app.instanceId(), JobType.devUsEast1).get().id();
        assertEquals(unfinished, tester.jobs().run(id).get().stepStatuses().get(Step.installReal));

        Version version = new Version("7.8.9");
        Future<?> concurrentDeployment = Executors.newSingleThreadExecutor().submit(() -> {
            tester.jobs().deploy(app.instanceId(), JobType.devUsEast1, Optional.of(version), applicationPackage);
        });
        while ( ! concurrentDeployment.isDone())
            tester.runner().run();
        assertEquals(id.number() + 1, tester.jobs().last(app.instanceId(), JobType.devUsEast1).get().id().number());

        ApplicationPackage otherPackage = new ApplicationPackageBuilder().region("us-central-1").build();
        tester.jobs().deploy(app.instanceId(), JobType.perfUsEast3, Optional.empty(), otherPackage);

        tester.runner().run(); // Job run order determined by JobType enum order per application.
        tester.configServer().convergeServices(app.instanceId(), zone);
        tester.setEndpoints(app.instanceId(), zone);
        assertEquals(unfinished, tester.jobs().run(id).get().stepStatuses().get(Step.installReal));
        assertEquals(applicationPackage.hash(), tester.configServer().application(app.instanceId(), zone).get().applicationPackage().hash());
        assertEquals(otherPackage.hash(), tester.configServer().application(app.instanceId(), JobType.perfUsEast3.zone(system())).get().applicationPackage().hash());

        tester.configServer().setVersion(app.instanceId(), zone, version);
        tester.runner().run();
        assertEquals(1, tester.jobs().active().size());
        assertEquals(version, tester.instance(app.instanceId()).deployments().get(zone).version());

        try {
            tester.jobs().deploy(app.instanceId(), JobType.productionApNortheast1, Optional.empty(), applicationPackage);
            fail("Deployments outside dev should not be allowed.");
        }
        catch (IllegalArgumentException expected) { }
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
        tester.controllerTester().zoneRegistry().setSystemName(SystemName.PublicCd);
        tester.controllerTester().zoneRegistry().setZones(ZoneApiMock.fromId("test.aws-us-east-1c"),
                                                          ZoneApiMock.fromId("staging.aws-us-east-1c"),
                                                          ZoneApiMock.fromId("prod.aws-us-east-1c"));
        tester.configServer().bootstrap(tester.controllerTester().zoneRegistry().zones().all().ids(), SystemApplication.values());
        RunId id = app.startSystemTestTests();

        List<X509Certificate> trusted = new ArrayList<>(publicCdApplicationPackage.trustedCertificates());
        trusted.add(tester.jobs().run(id).get().testerCertificate().get());
        assertEquals(trusted, tester.configServer().application(app.instanceId(), id.type().zone(system())).get().applicationPackage().trustedCertificates());

        tester.clock().advance(InternalStepRunner.certificateTimeout.plus(Duration.ofSeconds(1)));
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
    public void generates_correct_services_xml_test() {
        assertFile("test_runner_services.xml-cd", new String(InternalStepRunner.servicesXml(AthenzDomain.from("vespa.vespa.cd"),
                                                                                            true,
                                                                                            false,
                                                                                            new NodeResources(2, 12, 75, 1, NodeResources.DiskSpeed.fast, NodeResources.StorageType.local))));
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
