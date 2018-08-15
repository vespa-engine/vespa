// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.ConfigChangeActions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.RefeedAction;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.RestartAction;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.ServiceInfo;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingEndpoint;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockTesterCloud;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.integration.RoutingGeneratorMock;
import com.yahoo.vespa.hosted.controller.maintenance.JobControl;
import com.yahoo.vespa.hosted.controller.maintenance.JobRunner;
import com.yahoo.vespa.hosted.controller.maintenance.JobRunnerTest;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.log.LogLevel.DEBUG;
import static com.yahoo.vespa.hosted.controller.deployment.InternalStepRunner.testerOf;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.aborted;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.failed;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.succeeded;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.unfinished;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jonmv
 * @author freva
 */
public class InternalStepRunnerTest {

    private static final ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("default")
                .environment(Environment.prod)
                .region("us-west-1")
                .build();
    private static final ApplicationId appId = ApplicationId.from("tenant", "application", "default");

    private DeploymentTester tester;
    private JobController jobs;
    private RoutingGeneratorMock routing;
    private MockTesterCloud cloud;
    private JobRunner runner;
    private Application app() { return tester.application(appId); }

    @Before
    public void setup() {
        tester = new DeploymentTester();
        tester.createApplication(appId.application().value(), appId.tenant().value(), 1, 1L);
        jobs = tester.controller().jobController();
        routing = tester.controllerTester().routingGenerator();
        cloud = new MockTesterCloud();
        runner = new JobRunner(tester.controller(), Duration.ofDays(1), new JobControl(tester.controller().curator()),
                               JobRunnerTest.inThreadExecutor(), new InternalStepRunner(tester.controller(), cloud));
        routing.putEndpoints(new DeploymentId(null, null), Collections.emptyList()); // Turn off default behaviour for the mock.

        // Get deployment job logs to stderr.
        Logger.getLogger(InternalStepRunner.class.getName()).setLevel(DEBUG);
        Logger.getLogger("").setLevel(DEBUG);
        Logger.getLogger("").getHandlers()[0].setLevel(DEBUG);
    }

    @Test
    public void canRegisterAndRunDirectly() {
        jobs.register(appId);

        deployNewSubmission();

        deployNewPlatform(new Version("7.1"));
    }

    @Test
    public void canSwitchFromScrewdriver() {
        // Deploys a default application package with default build number.
        tester.deployCompletely(app(), applicationPackage);
        setEndpoints(appId, JobType.productionUsWest1.zone(tester.controller().system()));

        jobs.register(appId);

        deployNewSubmission();

        deployNewPlatform(new Version("7.1"));
    }

    /** Submits a new application, and returns the version of the new submission. */
    private ApplicationVersion newSubmission(ApplicationId id) {
        ApplicationVersion version = jobs.submit(id, BuildJob.defaultSourceRevision, applicationPackage.zippedContent(), new byte[0]);
        tester.applicationStore().putApplicationPackage(appId, version.id(), applicationPackage.zippedContent());
        tester.applicationStore().putTesterPackage(testerOf(appId), version.id(), new byte[0]);
        return version;
    }

    /** Sets a single endpoint in the routing mock; this matches that required for the tester. */
    private void setEndpoints(ApplicationId id, ZoneId zone) {
        routing.putEndpoints(new DeploymentId(id, zone),
                             Collections.singletonList(new RoutingEndpoint(String.format("https://%s--%s--%s.%s.%s.vespa:43",
                                                                                         id.instance().value(),
                                                                                         id.application().value(),
                                                                                         id.tenant().value(),
                                                                                         zone.region().value(),
                                                                                         zone.environment().value()),
                                                                           false)));
    }

    /** Completely deploys a new submission. */
    private void deployNewSubmission() {
        assertTrue(app().deploymentJobs().builtInternally());
        ApplicationVersion applicationVersion = newSubmission(appId);

        assertFalse(app().deployments().values().stream()
                         .anyMatch(deployment -> deployment.applicationVersion().equals(applicationVersion)));
        assertEquals(applicationVersion, app().change().application().get());
        assertFalse(app().change().platform().isPresent());

        runJob(JobType.systemTest);
        runJob(JobType.stagingTest);
        runJob(JobType.productionUsWest1);
    }

    /** Completely deploys the given, new platform. */
    private void deployNewPlatform(Version version) {
        assertTrue(app().deploymentJobs().builtInternally());

        tester.upgradeSystem(version);
        assertFalse(app().deployments().values().stream()
                         .anyMatch(deployment -> deployment.version().equals(version)));
        assertEquals(version, app().change().platform().get());
        assertFalse(app().change().application().isPresent());

        runJob(JobType.systemTest);
        runJob(JobType.stagingTest);
        runJob(JobType.productionUsWest1);
        assertTrue(app().productionDeployments().values().stream()
                        .allMatch(deployment -> deployment.version().equals(version)));
        assertTrue(tester.configServer().nodeRepository()
                         .list(JobType.productionUsWest1.zone(tester.controller().system()), appId).stream()
                         .allMatch(node -> node.currentVersion().equals(version)));
        assertFalse(app().change().isPresent());
    }

    /** Runs the whole of the given job, successfully. */
    private void runJob(JobType type) {
        tester.readyJobTrigger().maintain();
        Run run = jobs.active().stream()
                      .filter(r -> r.id().type() == type)
                      .findAny()
                      .orElseThrow(() -> new AssertionError(type + " is not among the active: " + jobs.active()));
        assertFalse(run.hasFailed());
        assertFalse(run.status() == aborted);

        ZoneId zone = type.zone(tester.controller().system());
        DeploymentId deployment = new DeploymentId(appId, zone);

        // First steps are always deployments.
        runner.run();

        if (type == JobType.stagingTest) { // Do the initial deployment and installation of the real application.
            assertEquals(unfinished, jobs.active(run.id()).get().steps().get(Step.installInitialReal));
            tester.configServer().convergeServices(appId, zone);
            run.versions().sourcePlatform().ifPresent(version -> tester.configServer().nodeRepository().doUpgrade(deployment, Optional.empty(), version));
            runner.run();
            assertEquals(Step.Status.succeeded, jobs.active(run.id()).get().steps().get(Step.installInitialReal));
        }

        assertEquals(unfinished, jobs.active(run.id()).get().steps().get(Step.installReal));
        tester.configServer().nodeRepository().doUpgrade(deployment, Optional.empty(), run.versions().targetPlatform());
        runner.run();
        assertEquals(unfinished, jobs.active(run.id()).get().steps().get(Step.installReal));
        tester.configServer().convergeServices(appId, zone);
        runner.run();
        assertEquals(Step.Status.succeeded, jobs.active(run.id()).get().steps().get(Step.installReal));

        assertEquals(unfinished, jobs.active(run.id()).get().steps().get(Step.installTester));
        tester.configServer().convergeServices(testerOf(appId), zone);
        runner.run();
        assertEquals(Step.Status.succeeded, jobs.active(run.id()).get().steps().get(Step.installTester));

        // All installation is complete. We now need endpoints, and the tests will then run, and cleanup finish.
        assertEquals(unfinished, jobs.active(run.id()).get().steps().get(Step.startTests));
        setEndpoints(testerOf(appId), zone);
        runner.run();
        if ( ! run.versions().sourceApplication().isPresent() || ! type.isProduction()) {
            assertEquals(unfinished, jobs.active(run.id()).get().steps().get(Step.startTests));
            setEndpoints(appId, zone);
        }
        runner.run();
        assertEquals(Step.Status.succeeded, jobs.active(run.id()).get().steps().get(Step.startTests));

        assertEquals(unfinished, jobs.active(run.id()).get().steps().get(Step.endTests));
        cloud.set("Awesome!".getBytes(), TesterCloud.Status.SUCCESS);
        runner.run();
        assertTrue(jobs.run(run.id()).get().hasEnded());
        assertFalse(jobs.run(run.id()).get().hasFailed());
        assertEquals(type.isProduction(), app().deployments().containsKey(zone));
        assertTrue(tester.configServer().nodeRepository().list(zone, testerOf(appId)).isEmpty());

        if ( ! app().deployments().containsKey(zone))
            routing.removeEndpoints(deployment);
        routing.removeEndpoints(new DeploymentId(testerOf(appId), zone));
    }

    @Test
    public void refeedRequirementBlocksDeployment() {
        RunId id = newRun(JobType.productionUsWest1);
        tester.configServer().setConfigChangeActions(new ConfigChangeActions(Collections.emptyList(),
                                                                             Collections.singletonList(new RefeedAction("Refeed",
                                                                                                                        false,
                                                                                                                        "doctype",
                                                                                                                        "cluster",
                                                                                                                        Collections.emptyList(),
                                                                                                                        Collections.singletonList("Refeed it!")))));
        runner.run();

        assertEquals(failed, jobs.run(id).get().steps().get(Step.deployReal));
    }

    @Test
    public void restartsServicesAndWaitsForRestartAndReboot() {
        RunId id = newRun(JobType.productionUsWest1);
        ZoneId zone = id.type().zone(tester.controller().system());
        HostName host = tester.configServer().hostFor(appId, zone);
        tester.configServer().setConfigChangeActions(new ConfigChangeActions(Collections.singletonList(new RestartAction("cluster",
                                                                                                                         "container",
                                                                                                                         "search",
                                                                                                                         Collections.singletonList(new ServiceInfo("queries",
                                                                                                                                                                   "search",
                                                                                                                                                                   "config",
                                                                                                                                                                   host.value())),
                                                                                                                         Collections.singletonList("Restart it!"))),
                                                                             Collections.emptyList()));
        runner.run();
        assertEquals(succeeded, jobs.run(id).get().steps().get(Step.deployReal));

        tester.configServer().convergeServices(appId, zone);
        assertEquals(unfinished, jobs.run(id).get().steps().get(Step.installReal));

        tester.configServer().nodeRepository().doRestart(new DeploymentId(appId, zone), Optional.of(host));
        tester.configServer().nodeRepository().requestReboot(new DeploymentId(appId, zone), Optional.of(host));
        runner.run();
        assertEquals(unfinished, jobs.run(id).get().steps().get(Step.installReal));

        tester.clock().advance(InternalStepRunner.installationTimeout.plus(Duration.ofSeconds(1)));
        runner.run();
        assertEquals(failed, jobs.run(id).get().steps().get(Step.installReal));
    }

    @Test
    public void waitsForEndpointsAndTimesOut() {
        newRun(JobType.systemTest);

        runner.run();
        tester.configServer().convergeServices(appId, JobType.stagingTest.zone(tester.controller().system()));
        runner.run();
        tester.configServer().convergeServices(appId, JobType.systemTest.zone(tester.controller().system()));
        tester.configServer().convergeServices(testerOf(appId), JobType.systemTest.zone(tester.controller().system()));
        tester.configServer().convergeServices(appId, JobType.stagingTest.zone(tester.controller().system()));
        tester.configServer().convergeServices(testerOf(appId), JobType.stagingTest.zone(tester.controller().system()));
        runner.run();

        // Tester fails to show up for system tests, and the real deployment for staging tests.
        setEndpoints(appId, JobType.systemTest.zone(tester.controller().system()));
        setEndpoints(testerOf(appId), JobType.stagingTest.zone(tester.controller().system()));

        tester.clock().advance(InternalStepRunner.endpointTimeout.plus(Duration.ofSeconds(1)));
        runner.run();
        assertEquals(failed, jobs.last(appId, JobType.systemTest).get().steps().get(Step.startTests));
        assertEquals(failed, jobs.last(appId, JobType.stagingTest).get().steps().get(Step.startTests));
    }

    @Test
    public void testsFailIfEndpointsAreGone() {
        RunId id = startSystemTestTests();
        cloud.set(new byte[0], TesterCloud.Status.NOT_STARTED);
        runner.run();
        assertEquals(failed, jobs.run(id).get().steps().get(Step.endTests));
    }

    @Test
    public void testsFailIfTestsFailRemotely() {
        RunId id = startSystemTestTests();
        cloud.set("Failure!".getBytes(), TesterCloud.Status.FAILURE);
        runner.run();
        assertEquals(failed, jobs.run(id).get().steps().get(Step.endTests));
        assertLogMessages(id, Step.endTests, "Tests still running ...", "Tests failed.", "Failure!");
    }

    @Test
    public void testsFailIfTestsErr() {
        RunId id = startSystemTestTests();
        cloud.set("Error!".getBytes(), TesterCloud.Status.ERROR);
        runner.run();
        assertEquals(failed, jobs.run(id).get().steps().get(Step.endTests));
        assertLogMessages(id, Step.endTests, "Tests still running ...", "Tester failed running its tests!", "Error!");
    }

    @Test
    public void testsSucceedWhenTheyDoRemotely() {
        RunId id = startSystemTestTests();
        runner.run();
        assertEquals(unfinished, jobs.run(id).get().steps().get(Step.endTests));
        assertEquals(URI.create(routing.endpoints(new DeploymentId(testerOf(appId), JobType.systemTest.zone(tester.controller().system()))).get(0).getEndpoint()),
                     cloud.testerUrl());
        Inspector configObject = SlimeUtils.jsonToSlime(cloud.config()).get();
        assertEquals(appId.serializedForm(), configObject.field("application").asString());
        assertEquals(JobType.systemTest.zone(tester.controller().system()).value(), configObject.field("zone").asString());
        assertEquals(tester.controller().system().name(), configObject.field("system").asString());
        assertEquals(1, configObject.field("endpoints").children());
        assertEquals(1, configObject.field("endpoints").field(JobType.systemTest.zone(tester.controller().system()).value()).entries());
        configObject.field("endpoints").field(JobType.systemTest.zone(tester.controller().system()).value()).traverse((ArrayTraverser) (__, endpoint) ->
                assertEquals(routing.endpoints(new DeploymentId(appId, JobType.systemTest.zone(tester.controller().system()))).get(0).getEndpoint(), endpoint.asString()));

        cloud.set("Success!".getBytes(), TesterCloud.Status.SUCCESS);
        runner.run();
        assertEquals(succeeded, jobs.run(id).get().steps().get(Step.endTests));
        assertLogMessages(id, Step.endTests, "Tests still running ...", "Tests still running ...", "Tests completed successfully.", "Success!");
    }

    private void assertLogMessages(RunId id, Step step, String... messages) {
        String pattern = Stream.of(messages)
                               .map(message -> "\\[[^]]*] : " + message + "\n")
                               .collect(Collectors.joining());
        String logs = new String(jobs.details(id).get().get(step).get());
        if ( ! logs.matches(pattern))
            throw new AssertionError("Expected a match for\n'''\n" + pattern + "\n'''\nbut got\n'''\n" + logs + "\n'''");
    }

    private RunId startSystemTestTests() {
        RunId id = newRun(JobType.systemTest);
        runner.run();
        tester.configServer().convergeServices(appId, JobType.systemTest.zone(tester.controller().system()));
        tester.configServer().convergeServices(testerOf(appId), JobType.systemTest.zone(tester.controller().system()));
        setEndpoints(appId, JobType.systemTest.zone(tester.controller().system()));
        setEndpoints(testerOf(appId), JobType.systemTest.zone(tester.controller().system()));
        runner.run();
        assertEquals(unfinished, jobs.run(id).get().steps().get(Step.endTests));
        return id;
    }

    private RunId newRun(JobType type) {
        assertFalse(app().deploymentJobs().builtInternally()); // Use this only once per test.
        jobs.register(appId);
        newSubmission(appId);
        tester.readyJobTrigger().maintain();

        if (type.isProduction()) {
            runJob(JobType.systemTest);
            runJob(JobType.stagingTest);
            tester.readyJobTrigger().maintain();
        }

        Run run = jobs.active().stream()
                      .filter(r -> r.id().type() == type)
                      .findAny()
                      .orElseThrow(() -> new AssertionError(type + " is not among the active: " + jobs.active()));
        return run.id();
    }

    @Test
    public void generates_correct_services_xml_test() {
        assertFile("test_runner_services.xml-cd", new String(InternalStepRunner.servicesXml(SystemName.cd)));
    }

    private void assertFile(String resourceName, String actualContent) {
        try {
            Path path = Paths.get(getClass().getClassLoader().getResource(resourceName).getPath());
            String expectedContent = new String(Files.readAllBytes(path));
            assertEquals(expectedContent, actualContent);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
