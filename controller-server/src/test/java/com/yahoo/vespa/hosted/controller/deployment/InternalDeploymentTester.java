// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.log.LogLevel;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzDbMock;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.SourceRevision;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterId;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingGeneratorMock;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockTesterCloud;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.integration.ConfigServerMock;
import com.yahoo.vespa.hosted.controller.maintenance.JobControl;
import com.yahoo.vespa.hosted.controller.maintenance.JobRunner;
import com.yahoo.vespa.hosted.controller.maintenance.JobRunnerTest;
import com.yahoo.vespa.hosted.controller.maintenance.NameServiceDispatcher;
import com.yahoo.vespa.hosted.controller.maintenance.OutstandingChangeDeployer;
import com.yahoo.vespa.hosted.controller.maintenance.ReadyJobsTrigger;
import com.yahoo.vespa.hosted.controller.maintenance.Upgrader;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author jonmv
 */
public class InternalDeploymentTester {

    // Set a long interval so that maintainers never do scheduled runs during tests
    private static final Duration maintenanceInterval = Duration.ofDays(1);

    private static final String ATHENZ_DOMAIN = "domain";
    private static final String ATHENZ_SERVICE = "service";

    public static final TenantAndApplicationId appId = TenantAndApplicationId.from("tenant", "application");
    public static final ApplicationId instanceId = appId.defaultInstance();
    public static final TesterId testerId = TesterId.of(instanceId);
    public static final String athenzDomain = "domain";

    private final DeploymentContext defaultContext;
    private final ControllerTester tester;
    private final JobController jobs;
    private final RoutingGeneratorMock routing;
    private final MockTesterCloud cloud;
    private final JobRunner runner;
    private final Upgrader upgrader;
    private final ReadyJobsTrigger readyJobsTrigger;
    private final OutstandingChangeDeployer outstandingChangeDeployer;
    private final NameServiceDispatcher nameServiceDispatcher;

    public JobController jobs() { return jobs; }
    public RoutingGeneratorMock routing() { return routing; }
    public MockTesterCloud cloud() { return cloud; }
    public JobRunner runner() { return runner; }
    public ConfigServerMock configServer() { return tester.configServer(); }
    public Controller controller() { return tester.controller(); }
    public DeploymentTrigger deploymentTrigger() { return applications().deploymentTrigger(); }
    public ControllerTester controllerTester() { return tester; }
    public Upgrader upgrader() { return upgrader; }
    public ApplicationController applications() { return tester.controller().applications(); }
    public ManualClock clock() { return tester.clock(); }
    public Application application() { return application(appId); }
    public Application application(TenantAndApplicationId id ) { return applications().requireApplication(id); }
    public Instance instance() { return instance(instanceId); }
    public Instance instance(ApplicationId id) { return applications().requireInstance(id); }

    public InternalDeploymentTester() {
        this(new ControllerTester());
    }

    public InternalDeploymentTester(ControllerTester controllerTester) {
        tester = controllerTester;
        jobs = tester.controller().jobController();
        routing = tester.serviceRegistry().routingGeneratorMock();
        cloud = (MockTesterCloud) tester.controller().jobController().cloud();
        var jobControl = new JobControl(tester.controller().curator());
        runner = new JobRunner(tester.controller(), Duration.ofDays(1), jobControl,
                               JobRunnerTest.inThreadExecutor(), new InternalStepRunner(tester.controller()));
        upgrader = new Upgrader(tester.controller(), maintenanceInterval, jobControl, tester.curator());
        upgrader.setUpgradesPerMinute(1); // Anything that makes it at least one for any maintenance period is fine.
        readyJobsTrigger = new ReadyJobsTrigger(tester.controller(), maintenanceInterval, jobControl);
        outstandingChangeDeployer = new OutstandingChangeDeployer(tester.controller(), maintenanceInterval, jobControl);
        nameServiceDispatcher = new NameServiceDispatcher(tester.controller(), maintenanceInterval, jobControl,
                                                          Integer.MAX_VALUE);
        defaultContext = newDeploymentContext(instanceId);
        routing.putEndpoints(new DeploymentId(null, null), Collections.emptyList()); // Turn off default behaviour for the mock.

        // Get deployment job logs to stderr.
        Logger.getLogger("").setLevel(LogLevel.DEBUG);
        Logger.getLogger(InternalStepRunner.class.getName()).setLevel(LogLevel.DEBUG);
        tester.configureDefaultLogHandler(handler -> handler.setLevel(LogLevel.DEBUG));

        // Mock Athenz domain to allow launch of service
        AthenzDbMock.Domain domain = tester.athenzDb().getOrCreateDomain(new com.yahoo.vespa.athenz.api.AthenzDomain(ATHENZ_DOMAIN));
        domain.services.put(ATHENZ_SERVICE, new AthenzDbMock.Service(true));
    }

    public ReadyJobsTrigger readyJobsTrigger() {
        return readyJobsTrigger;
    }

    public OutstandingChangeDeployer outstandingChangeDeployer() { return outstandingChangeDeployer; }

    public NameServiceDispatcher nameServiceDispatcher() {
        return nameServiceDispatcher;
    }

    public InternalDeploymentTester atHourOfDay(int hour) {
        var dateTime = tester.clock().instant().atZone(ZoneOffset.UTC);
        return at(LocalDateTime.of(dateTime.getYear(), dateTime.getMonth(), dateTime.getDayOfMonth(), hour,
                                   dateTime.getMinute(), dateTime.getSecond())
                               .toInstant(ZoneOffset.UTC));
    }

    public InternalDeploymentTester at(Instant instant) {
        tester.clock().setInstant(instant);
        return this;
    }

    /** Returns the default deployment context owned by this */
    public DeploymentContext deploymentContext() {
        return defaultContext;
    }

    /** Create a new deployment context for given application */
    public DeploymentContext newDeploymentContext(String tenantName, String applicationName, String instanceName) {
        return newDeploymentContext(ApplicationId.from(tenantName, applicationName, instanceName));
    }

    /** Create a new deployment context for given application */
    public DeploymentContext newDeploymentContext(ApplicationId instance) {
        return new DeploymentContext(instance, this);
    }

    /** Create a new application with given tenant and application name */
    public Application createApplication(String tenantName, String applicationName, String instanceName) {
        return newDeploymentContext(tenantName, applicationName, instanceName).application();
    }

    /** Submits a new application, and returns the version of the new submission. */
    public ApplicationVersion newSubmission(TenantAndApplicationId id, ApplicationPackage applicationPackage, SourceRevision sourceRevision) {
        return newDeploymentContext(id.defaultInstance()).submit(applicationPackage, sourceRevision).lastSubmission().get();
    }

    public ApplicationVersion newSubmission(TenantAndApplicationId id, ApplicationPackage applicationPackage) {
        return newSubmission(id, applicationPackage, DeploymentContext.defaultSourceRevision);
    }

    /**
     * Submits a new application package, and returns the version of the new submission.
     */
    public ApplicationVersion newSubmission(ApplicationPackage applicationPackage) {
        return newSubmission(appId, applicationPackage);
    }

    /**
     * Submits a new application, and returns the version of the new submission.
     */
    public ApplicationVersion newSubmission() {
        return defaultContext.submit().lastSubmission().get();
    }

    /**
     * Sets a single endpoint in the routing mock; this matches that required for the tester.
     */
    public void setEndpoints(ApplicationId id, ZoneId zone) {
        newDeploymentContext(id).setEndpoints(zone);
    }

    /** Completely deploys the given application version, assuming it is the last to be submitted. */
    public void deployNewSubmission(ApplicationVersion version) {
        deployNewSubmission(appId, version);
    }

    /** Completely deploys the given application version, assuming it is the last to be submitted. */
    public void deployNewSubmission(TenantAndApplicationId id, ApplicationVersion version) {
        var context = newDeploymentContext(id.defaultInstance());
        var application = context.application();
        assertFalse(application.instances().values().stream()
                               .anyMatch(instance -> instance.deployments().values().stream()
                                                             .anyMatch(deployment -> deployment.applicationVersion().equals(version))));
        assertEquals(version, application.change().application().get());
        assertFalse(application.change().platform().isPresent());
        context.completeRollout();
        assertFalse(context.application().change().hasTargets());
    }

    /** Completely deploys the given, new platform. */
    public void deployNewPlatform(Version version) {
        deployNewPlatform(appId, version);
    }

    /** Completely deploys the given, new platform. */
    public void deployNewPlatform(TenantAndApplicationId id, Version version) {
        newDeploymentContext(id.defaultInstance()).deployPlatform(version);
    }

    /** Aborts and finishes all running jobs. */
    public void abortAll() {
        triggerJobs();
        for (Run run : jobs.active()) {
            jobs.abort(run.id());
            runner.advance(jobs.run(run.id()).get());
            assertTrue(jobs.run(run.id()).get().hasEnded());
        }
    }

    /** Triggers jobs until nothing more triggers, and returns the number of triggered jobs. */
    public int triggerJobs() {
        int triggered = 0;
        while (triggered != (triggered += deploymentTrigger().triggerReadyJobs()));
        return triggered;
    }

    /** Starts a manual deployment of the given package, and then runs the whole of the given job, successfully. */
    public void runJob(ApplicationId instanceId, JobType type, ApplicationPackage applicationPackage) {
        jobs.deploy(instanceId, type, Optional.empty(), applicationPackage);
        newDeploymentContext(instanceId).runJob(type);
    }

    /** Pulls the ready job trigger, and then runs the whole of the given job, successfully. */
    public void runJob(JobType type) {
        defaultContext.runJob(type);
    }

    /** Pulls the ready job trigger, and then runs the whole of the given job, successfully. */
    public void runJob(ApplicationId instanceId, JobType type) {
        if (type.environment().isManuallyDeployed())
            throw new IllegalArgumentException("Use overload with application package for dev/perf jobs");
        newDeploymentContext(instanceId).runJob(type);
    }

    public void failDeployment(JobType type) {
        defaultContext.failDeployment(type);
    }

    public void failDeployment(ApplicationId instanceId, JobType type) {
        newDeploymentContext(instanceId).failDeployment(type);
    }

    public void timeOutUpgrade(JobType type) {
        defaultContext.timeOutUpgrade(type);
    }

    public void timeOutUpgrade(ApplicationId instanceId, JobType type) {
        newDeploymentContext(instanceId).timeOutConvergence(type);
    }

    public void timeOutConvergence(JobType type) {
        defaultContext.timeOutConvergence(type);
    }

    public void timeOutConvergence(ApplicationId instanceId, JobType type) {
        newDeploymentContext(instanceId).timeOutConvergence(type);
    }

    public RunId startSystemTestTests() {
        return defaultContext.startSystemTestTests();
    }

    /** Creates and submits a new application, and then starts the job of the given type. Use only once per test. */
    public RunId newRun(JobType type) {
        return defaultContext.newRun(type);
    }

    public void assertRunning(JobType type) {
        assertRunning(instanceId, type);
    }

    public void assertRunning(ApplicationId id, JobType type) {
        assertTrue(jobs.active().stream().anyMatch(run -> run.id().application().equals(id) && run.id().type() == type));
    }

}
