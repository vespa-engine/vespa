// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.ConfigChangeActions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.RefeedAction;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.RestartAction;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.ServiceInfo;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.LogEntry;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static com.yahoo.vespa.hosted.controller.api.integration.LogEntry.Type.debug;
import static com.yahoo.vespa.hosted.controller.api.integration.LogEntry.Type.error;
import static com.yahoo.vespa.hosted.controller.api.integration.LogEntry.Type.info;
import static com.yahoo.vespa.hosted.controller.deployment.JobController.testerOf;
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

    private InternalDeploymentTester tester;

    @Before
    public void setup() {
        tester = new InternalDeploymentTester();
    }

    @Test
    public void canRegisterAndRunDirectly() {
        tester.deployNewSubmission();

        tester.deployNewPlatform(new Version("7.1"));
    }

    @Test
    public void canSwitchFromScrewdriver() {
        // Deploys a default application package with default build number.
        tester.tester().deployCompletely(tester.app(), InternalDeploymentTester.applicationPackage);
        tester.setEndpoints(InternalDeploymentTester.appId, JobType.productionUsWest1.zone(tester.tester().controller().system()));
        tester.setEndpoints(InternalDeploymentTester.appId, JobType.productionUsEast3.zone(tester.tester().controller().system()));

        tester.deployNewSubmission();

        tester.deployNewPlatform(new Version("7.1"));
    }

    @Test
    public void testerHasAthenzIdentity() {
        tester.newRun(JobType.stagingTest);
        tester.runner().run();
        DeploymentSpec spec = tester.configServer().application(testerOf(InternalDeploymentTester.appId)).get().applicationPackage().deploymentSpec();
        assertEquals("domain", spec.athenzDomain().get().value());
        ZoneId zone = JobType.stagingTest.zone(tester.tester().controller().system());
        assertEquals("service", spec.athenzService(zone.environment(), zone.region()).get().value());
    }

    @Test
    public void refeedRequirementBlocksDeployment() {
        RunId id = tester.newRun(JobType.productionUsWest1);
        tester.configServer().setConfigChangeActions(new ConfigChangeActions(Collections.emptyList(),
                                                                                      Collections.singletonList(new RefeedAction("Refeed",
                                                                                                                        false,
                                                                                                                        "doctype",
                                                                                                                        "cluster",
                                                                                                                        Collections.emptyList(),
                                                                                                                        Collections.singletonList("Refeed it!")))));
        tester.runner().run();

        assertEquals(failed, tester.jobs().run(id).get().steps().get(Step.deployReal));
    }

    @Test
    public void restartsServicesAndWaitsForRestartAndReboot() {
        RunId id = tester.newRun(JobType.productionUsWest1);
        ZoneId zone = id.type().zone(tester.tester().controller().system());
        HostName host = tester.configServer().hostFor(InternalDeploymentTester.appId, zone);
        tester.configServer().setConfigChangeActions(new ConfigChangeActions(Collections.singletonList(new RestartAction("cluster",
                                                                                                                                  "container",
                                                                                                                                  "search",
                                                                                                                                  Collections.singletonList(new ServiceInfo("queries",
                                                                                                                                                                   "search",
                                                                                                                                                                   "config",
                                                                                                                                                                   host.value())),
                                                                                                                                  Collections.singletonList("Restart it!"))),
                                                                                      Collections.emptyList()));
        tester.runner().run();
        assertEquals(succeeded, tester.jobs().run(id).get().steps().get(Step.deployReal));

        tester.configServer().convergeServices(InternalDeploymentTester.appId, zone);
        assertEquals(unfinished, tester.jobs().run(id).get().steps().get(Step.installReal));

        tester.configServer().nodeRepository().doRestart(new DeploymentId(InternalDeploymentTester.appId, zone), Optional.of(host));
        tester.configServer().nodeRepository().requestReboot(new DeploymentId(InternalDeploymentTester.appId, zone), Optional.of(host));
        tester.runner().run();
        assertEquals(unfinished, tester.jobs().run(id).get().steps().get(Step.installReal));

        tester.clock().advance(InternalStepRunner.installationTimeout.plus(Duration.ofSeconds(1)));
        tester.runner().run();
        assertEquals(failed, tester.jobs().run(id).get().steps().get(Step.installReal));
    }

    @Test
    public void waitsForEndpointsAndTimesOut() {
        tester.newRun(JobType.systemTest);

        tester.runner().run();
        tester.configServer().convergeServices(InternalDeploymentTester.appId, JobType.stagingTest.zone(tester.tester().controller().system()));
        tester.runner().run();
        tester.configServer().convergeServices(InternalDeploymentTester.appId, JobType.systemTest.zone(tester.tester().controller().system()));
        tester.configServer().convergeServices(testerOf(InternalDeploymentTester.appId), JobType.systemTest.zone(tester.tester().controller().system()));
        tester.configServer().convergeServices(InternalDeploymentTester.appId, JobType.stagingTest.zone(tester.tester().controller().system()));
        tester.configServer().convergeServices(testerOf(InternalDeploymentTester.appId), JobType.stagingTest.zone(tester.tester().controller().system()));
        tester.runner().run();

        // Tester fails to show up for system tests, and the real deployment for staging tests.
        tester.setEndpoints(InternalDeploymentTester.appId, JobType.systemTest.zone(tester.tester().controller().system()));
        tester.setEndpoints(testerOf(InternalDeploymentTester.appId), JobType.stagingTest.zone(tester.tester().controller().system()));

        tester.clock().advance(InternalStepRunner.endpointTimeout.plus(Duration.ofSeconds(1)));
        tester.runner().run();
        assertEquals(failed, tester.jobs().last(InternalDeploymentTester.appId, JobType.systemTest).get().steps().get(Step.startTests));
        assertEquals(failed, tester.jobs().last(InternalDeploymentTester.appId, JobType.stagingTest).get().steps().get(Step.startTests));
    }

    @Test
    public void installationFailsIfDeploymentExpires() {
        tester.newRun(JobType.systemTest);
        tester.runner().run();
        tester.configServer().convergeServices(InternalDeploymentTester.appId, JobType.systemTest.zone(tester.tester().controller().system()));
        tester.runner().run();
        assertEquals(succeeded, tester.jobs().last(InternalDeploymentTester.appId, JobType.systemTest).get().steps().get(Step.installReal));

        tester.applications().deactivate(InternalDeploymentTester.appId, JobType.systemTest.zone(tester.tester().controller().system()));
        tester.runner().run();
        assertEquals(failed, tester.jobs().last(InternalDeploymentTester.appId, JobType.systemTest).get().steps().get(Step.installTester));
        assertTrue(tester.jobs().last(InternalDeploymentTester.appId, JobType.systemTest).get().hasEnded());
        assertTrue(tester.jobs().last(InternalDeploymentTester.appId, JobType.systemTest).get().hasFailed());
    }

    @Test
    public void startTestsFailsIfDeploymentExpires() {
        tester.newRun(JobType.systemTest);
        tester.runner().run();
        tester.configServer().convergeServices(InternalDeploymentTester.appId, JobType.systemTest.zone(tester.tester().controller().system()));
        tester.configServer().convergeServices(testerOf(InternalDeploymentTester.appId), JobType.systemTest.zone(tester.tester().controller().system()));
        tester.runner().run();

        tester.applications().deactivate(InternalDeploymentTester.appId, JobType.systemTest.zone(tester.tester().controller().system()));
        tester.runner().run();
        assertEquals(unfinished, tester.jobs().last(InternalDeploymentTester.appId, JobType.systemTest).get().steps().get(Step.startTests));
    }

    @Test
    public void testsFailIfTesterEndpointsVanish() {
        RunId id = tester.startSystemTestTests();
        tester.routing().removeEndpoints(new DeploymentId(testerOf(InternalDeploymentTester.appId), JobType.systemTest.zone(tester.tester().controller().system())));
        tester.runner().run();
        assertEquals(failed, tester.jobs().run(id).get().steps().get(Step.endTests));
    }

    @Test
    public void testsFailIfTesterRestarts() {
        RunId id = tester.startSystemTestTests();
        tester.cloud().set(TesterCloud.Status.NOT_STARTED);
        tester.runner().run();
        assertEquals(failed, tester.jobs().run(id).get().steps().get(Step.endTests));
    }

    @Test
    public void testsFailIfTestsFailRemotely() {
        RunId id = tester.startSystemTestTests();
        tester.cloud().add(new LogEntry(123, 321, error, "Failure!"));
        tester.cloud().set(TesterCloud.Status.FAILURE);

        long lastId = tester.jobs().details(id).get().lastId().getAsLong();
        tester.runner().run();
        assertTestLogEntries(id, Step.endTests,
                             new LogEntry(lastId + 1, 321, error, "Failure!"),
                             new LogEntry(lastId + 2, tester.clock().millis(), debug, "Tests failed."));
        assertEquals(failed, tester.jobs().run(id).get().steps().get(Step.endTests));
    }

    @Test
    public void testsFailIfTestsErr() {
        RunId id = tester.startSystemTestTests();
        tester.cloud().add(new LogEntry(0, 123, error, "Error!"));
        tester.cloud().set(TesterCloud.Status.ERROR);

        long lastId = tester.jobs().details(id).get().lastId().getAsLong();
        tester.runner().run();
        assertEquals(failed, tester.jobs().run(id).get().steps().get(Step.endTests));
        assertTestLogEntries(id, Step.endTests,
                             new LogEntry(lastId + 1, 123, error, "Error!"),
                             new LogEntry(lastId + 2, tester.clock().millis(), info, "Tester failed running its tests!"));
    }

    @Test
    public void testsSucceedWhenTheyDoRemotely() {
        RunId id = tester.startSystemTestTests();
        tester.runner().run();
        assertEquals(unfinished, tester.jobs().run(id).get().steps().get(Step.endTests));
        assertEquals(URI.create(tester.routing().endpoints(new DeploymentId(testerOf(InternalDeploymentTester.appId), JobType.systemTest.zone(tester.tester().controller().system()))).get(0).getEndpoint()),
                     tester.cloud().testerUrl());
        Inspector configObject = SlimeUtils.jsonToSlime(tester.cloud().config()).get();
        assertEquals(InternalDeploymentTester.appId.serializedForm(), configObject.field("application").asString());
        assertEquals(JobType.systemTest.zone(tester.tester().controller().system()).value(), configObject.field("zone").asString());
        assertEquals(tester.tester().controller().system().name(), configObject.field("system").asString());
        assertEquals(1, configObject.field("endpoints").children());
        assertEquals(1, configObject.field("endpoints").field(JobType.systemTest.zone(tester.tester().controller().system()).value()).entries());
        configObject.field("endpoints").field(JobType.systemTest.zone(tester.tester().controller().system()).value()).traverse((ArrayTraverser) (__, endpoint) ->
                assertEquals(tester.routing().endpoints(new DeploymentId(InternalDeploymentTester.appId, JobType.systemTest.zone(tester.tester().controller().system()))).get(0).getEndpoint(), endpoint.asString()));

        long lastId = tester.jobs().details(id).get().lastId().getAsLong();
        tester.cloud().add(new LogEntry(0, 123, info, "Ready!"));
        tester.runner().run();
        assertTestLogEntries(id, Step.endTests,
                             new LogEntry(lastId + 1, 123, info, "Ready!"));

        tester.cloud().add(new LogEntry(1, 1234, info, "Steady!"));
        tester.runner().run();
        assertTestLogEntries(id, Step.endTests,
                             new LogEntry(lastId + 1, 123, info, "Ready!"),
                             new LogEntry(lastId + 2, 1234, info, "Steady!"));

        tester.cloud().add(new LogEntry(12, 12345, info, "Success!"));
        tester.cloud().set(TesterCloud.Status.SUCCESS);
        tester.runner().run();
        assertTestLogEntries(id, Step.endTests,
                             new LogEntry(lastId + 1, 123, info, "Ready!"),
                             new LogEntry(lastId + 2, 1234, info, "Steady!"),
                             new LogEntry(lastId + 3, 12345, info, "Success!"),
                             new LogEntry(lastId + 4, tester.clock().millis(), debug, "Tests completed successfully."));
        assertEquals(succeeded, tester.jobs().run(id).get().steps().get(Step.endTests));
    }

    private void assertTestLogEntries(RunId id, Step step, LogEntry... entries) {
        assertEquals(Arrays.asList(entries), tester.jobs().details(id).get().get(step).get());
    }

    @Test
    public void generates_correct_services_xml_test() {
        assertFile("test_runner_services.xml-cd", new String(InternalStepRunner.servicesXml(SystemName.cd)));
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
