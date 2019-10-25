// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.log.LogLevel;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzDbMock;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.SourceRevision;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterId;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingEndpoint;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingGeneratorMock;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockTesterCloud;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.integration.ConfigServerMock;
import com.yahoo.vespa.hosted.controller.maintenance.JobControl;
import com.yahoo.vespa.hosted.controller.maintenance.JobRunner;
import com.yahoo.vespa.hosted.controller.maintenance.JobRunnerTest;
import com.yahoo.vespa.hosted.controller.maintenance.NameServiceDispatcher;
import com.yahoo.vespa.hosted.controller.maintenance.Upgrader;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.aborted;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.unfinished;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class InternalDeploymentTester {

    private static final String ATHENZ_DOMAIN = "domain";
    private static final String ATHENZ_SERVICE = "service";

    public static final ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
            .athenzIdentity(AthenzDomain.from(ATHENZ_DOMAIN), AthenzService.from(ATHENZ_SERVICE))
            .upgradePolicy("default")
            .region("us-central-1")
            .parallel("us-west-1", "us-east-3")
            .emailRole("author")
            .emailAddress("b@a")
            .build();
    public static final ApplicationPackage publicCdApplicationPackage = new ApplicationPackageBuilder()
            .athenzIdentity(AthenzDomain.from(ATHENZ_DOMAIN), AthenzService.from(ATHENZ_SERVICE))
            .upgradePolicy("default")
            .region("aws-us-east-1c")
            .emailRole("author")
            .emailAddress("b@a")
            .trust(generateCertificate())
            .build();
    public static final TenantAndApplicationId appId = TenantAndApplicationId.from("tenant", "application");
    public static final ApplicationId instanceId = appId.defaultInstance();
    public static final TesterId testerId = TesterId.of(instanceId);
    public static final String athenzDomain = "domain";

    private final DeploymentTester tester;
    private final JobController jobs;
    private final RoutingGeneratorMock routing;
    private final MockTesterCloud cloud;
    private final JobRunner runner;
    private final NameServiceDispatcher nameServiceDispatcher;

    private final AtomicLong nextPropertyId = new AtomicLong(1);
    private final AtomicInteger nextProjectId = new AtomicInteger(1);
    private final AtomicInteger nextDomainId = new AtomicInteger(100);

    public DeploymentTester tester() { return tester; }
    public JobController jobs() { return jobs; }
    public RoutingGeneratorMock routing() { return routing; }
    public MockTesterCloud cloud() { return cloud; }
    public JobRunner runner() { return runner; }
    public ConfigServerMock configServer() { return tester.configServer(); }
    public Controller controller() { return tester.controller(); }
    public ControllerTester controllerTester() { return tester.controllerTester(); }
    public Upgrader upgrader() { return tester.upgrader(); }
    public ApplicationController applications() { return tester.applications(); }
    public ManualClock clock() { return tester.clock(); }
    public Application application() { return tester.application(appId); }
    public Instance instance() { return tester.instance(instanceId); }

    public InternalDeploymentTester() {
        tester = new DeploymentTester();
        createApplication(instanceId.tenant().value(), instanceId.application().value(), instanceId.instance().value());
        jobs = tester.controller().jobController();
        routing = tester.controllerTester().serviceRegistry().routingGeneratorMock();
        cloud = (MockTesterCloud) tester.controller().jobController().cloud();
        runner = new JobRunner(tester.controller(), Duration.ofDays(1), new JobControl(tester.controller().curator()),
                               JobRunnerTest.inThreadExecutor(), new InternalStepRunner(tester.controller()));
        this.nameServiceDispatcher = new NameServiceDispatcher(tester.controller(), Duration.ofHours(12),
                                                               new JobControl(tester.controller().curator()),
                                                               Integer.MAX_VALUE);
        routing.putEndpoints(new DeploymentId(null, null), Collections.emptyList()); // Turn off default behaviour for the mock.

        // Get deployment job logs to stderr.
        Logger.getLogger(InternalStepRunner.class.getName()).setLevel(LogLevel.DEBUG);
        Logger.getLogger("").setLevel(LogLevel.DEBUG);
        tester.controllerTester().configureDefaultLogHandler(handler -> handler.setLevel(LogLevel.DEBUG));

        // Mock Athenz domain to allow launch of service
        AthenzDbMock.Domain domain = tester.controllerTester().athenzDb().getOrCreateDomain(new com.yahoo.vespa.athenz.api.AthenzDomain(ATHENZ_DOMAIN));
        domain.services.put(ATHENZ_SERVICE, new AthenzDbMock.Service(true));
    }

    /** Create a new application with given tenant and application name */
    public Application createApplication(String tenantName, String applicationName, String instanceName) {
        return tester.controllerTester().createApplication(tester.controllerTester().createTenant(tenantName,
                                                                                                  athenzDomain + nextDomainId.getAndIncrement(),
                                                                                                  nextPropertyId.getAndIncrement()),
                                                           applicationName,
                                                           instanceName,
                                                           nextProjectId.getAndIncrement());
    }

    /** Submits a new application, and returns the version of the new submission. */
    public ApplicationVersion newSubmission(TenantAndApplicationId id, ApplicationPackage applicationPackage,
                                            SourceRevision revision, String authorEmail, long projectId) {
        return jobs.submit(id, revision, authorEmail, projectId, applicationPackage, new byte[0]);
    }

    /** Submits a new application, and returns the version of the new submission. */
    public ApplicationVersion newSubmission(TenantAndApplicationId id, ApplicationPackage applicationPackage) {
        var projectId = tester.application(id).projectId().orElseThrow(() -> new IllegalArgumentException("No project ID set for " + id));
        return newSubmission(id, applicationPackage, BuildJob.defaultSourceRevision, "a@b", projectId);
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
        return newSubmission(appId, tester.controller().system().isPublic() ? publicCdApplicationPackage : applicationPackage);
    }

    /**
     * Sets a single endpoint in the routing mock; this matches that required for the tester.
     */
    public void setEndpoints(ApplicationId id, ZoneId zone) {
        routing.putEndpoints(new DeploymentId(id, zone),
                             Collections.singletonList(new RoutingEndpoint(String.format("https://%s--%s--%s.%s.%s.vespa:43",
                                                                                         id.instance().value(),
                                                                                         id.application().value(),
                                                                                         id.tenant().value(),
                                                                                         zone.region().value(),
                                                                                         zone.environment().value()),
                                                                           "host1",
                                                                           false,
                                                                           String.format("cluster1.%s.%s.%s.%s",
                                                                                         id.application().value(),
                                                                                         id.tenant().value(),
                                                                                         zone.region().value(),
                                                                                         zone.environment().value()))));
    }

    /** Runs and returns all remaining jobs for the application, at most once, and asserts the current change is rolled out. */
    public List<JobType> completeRollout(TenantAndApplicationId id) {
        tester.readyJobTrigger().run();
        Set<JobType> jobs = new HashSet<>();
        List<Run> activeRuns;
        while ( ! (activeRuns = jobs().active(id)).isEmpty())
            for (Run run : activeRuns)
                if (jobs.add(run.id().type())) {
                    runJob(run.id().job());
                    tester.readyJobTrigger().run();
                }
                else
                    throw new AssertionError("Job '" + run.id().type() + "' was run twice for '" + instanceId + "'");

        assertFalse("Change should have no targets, but was " + application().change(), application().change().hasTargets());
        return List.copyOf(jobs);
    }

    /** Completely deploys the given application version, assuming it is the last to be submitted. */
    public void deployNewSubmission(ApplicationVersion version) {
        deployNewSubmission(appId, version);
    }

    /** Completely deploys the given application version, assuming it is the last to be submitted. */
    public void deployNewSubmission(TenantAndApplicationId id, ApplicationVersion version) {
        assertFalse(tester.application(id).instances().values().stream()
                          .anyMatch(instance -> instance.deployments().values().stream()
                                                        .anyMatch(deployment -> deployment.applicationVersion().equals(version))));
        assertEquals(version, tester.application(id).change().application().get());
        assertFalse(tester.application(id).change().platform().isPresent());
        completeRollout(id);
        assertFalse(tester.application(id).change().hasTargets());
    }

    /** Completely deploys the given, new platform. */
    public void deployNewPlatform(Version version) {
        deployNewPlatform(appId, version);
    }

    /** Completely deploys the given, new platform. */
    public void deployNewPlatform(TenantAndApplicationId id, Version version) {
        assertEquals(tester.controller().systemVersion(), version);
        assertFalse(tester.application(id).instances().values().stream()
                          .anyMatch(instance -> instance.deployments().values().stream()
                                                        .anyMatch(deployment -> deployment.version().equals(version))));
        assertEquals(version, tester.application(id).change().platform().get());
        assertFalse(tester.application(id).change().application().isPresent());

        completeRollout(id);

        assertTrue(tester.application(id).productionDeployments().values().stream()
                         .allMatch(deployments -> deployments.stream()
                                                             .allMatch(deployment -> deployment.version().equals(version))));

        for (JobType type : new DeploymentSteps(application().deploymentSpec(), tester.controller()::system).productionJobs())
            assertTrue(tester.configServer().nodeRepository()
                             .list(type.zone(tester.controller().system()), id.defaultInstance()).stream() // TODO jonmv: support more
                             .allMatch(node -> node.currentVersion().equals(version)));

        assertFalse(tester.application(id).change().hasTargets());
    }

    public void triggerJobs() {
        tester.triggerUntilQuiescence();
    }

    /** Returns the current run for the given job type, and verifies it is still running normally. */
    public Run currentRun(JobId job) {
        Run run = jobs.last(job)
                      .filter(r -> r.id().type() == job.type())
                      .orElseThrow(() -> new AssertionError(job.type() + " is not among the active: " + jobs.active()));
        assertFalse(run.hasFailed());
        assertFalse(run.hasEnded());
        return run;
    }

    /** Deploys tester and real app, and completes initial staging installation first if needed. */
    public void doDeploy(JobType type) {
        doDeploy(new JobId(instanceId, type));
    }

    /** Deploys tester and real app, and completes initial staging installation first if needed. */
    public void doDeploy(ApplicationId instanceId, JobType type) {
        doDeploy(new JobId(instanceId, type));
    }

    /** Deploys tester and real app, and completes initial staging installation first if needed. */
    public void doDeploy(JobId job) {
        RunId id = currentRun(job).id();
        ZoneId zone = job.type().zone(tester.controller().system());
        DeploymentId deployment = new DeploymentId(job.application(), zone);

        // First steps are always deployments.
        runner.advance(currentRun(job));

        if (job.type() == JobType.stagingTest) { // Do the initial deployment and installation of the real application.
            assertEquals(unfinished, jobs.run(id).get().steps().get(Step.installInitialReal));
            Versions versions = currentRun(job).versions();
            tester.configServer().nodeRepository().doUpgrade(deployment, Optional.empty(), versions.sourcePlatform().orElse(versions.targetPlatform()));
            tester.configServer().convergeServices(id.application(), zone);
            setEndpoints(id.application(), zone);
            runner.advance(currentRun(job));
            assertEquals(Step.Status.succeeded, jobs.run(id).get().steps().get(Step.installInitialReal));
        }
    }

    /** Upgrades nodes to target version. */
    public void doUpgrade(JobType type) {
        doUpgrade(new JobId(instanceId, type));
    }

    /** Upgrades nodes to target version. */
    public void doUpgrade(ApplicationId instanceId, JobType type) {
        doUpgrade(new JobId(instanceId, type));
    }

    /** Upgrades nodes to target version. */
    public void doUpgrade(JobId job) {
        RunId id = currentRun(job).id();
        ZoneId zone = job.type().zone(tester.controller().system());
        DeploymentId deployment = new DeploymentId(job.application(), zone);

        assertEquals(unfinished, jobs.run(id).get().steps().get(Step.installReal));
        tester.configServer().nodeRepository().doUpgrade(deployment, Optional.empty(), currentRun(job).versions().targetPlatform());
        runner.advance(currentRun(job));
    }

    /** Lets nodes converge on new application version. */
    public void doConverge(JobType type) {
        doConverge(new JobId(instanceId, type));
    }

    /** Lets nodes converge on new application version. */
    public void doConverge(ApplicationId instanceId, JobType type) {
        doConverge(new JobId(instanceId, type));
    }

    /** Lets nodes converge on new application version. */
    public void doConverge(JobId job) {
        RunId id = currentRun(job).id();
        ZoneId zone = job.type().zone(tester.controller().system());

        assertEquals(unfinished, jobs.run(id).get().steps().get(Step.installReal));
        tester.configServer().convergeServices(id.application(), zone);
        setEndpoints(id.application(), zone);
        runner.advance(currentRun(job));
        if (job.type().environment().isManuallyDeployed()) {
            assertEquals(Step.Status.succeeded, jobs.run(id).get().steps().get(Step.installReal));
            assertTrue(jobs.run(id).get().hasEnded());
            return;
        }
        assertEquals(Step.Status.succeeded, jobs.run(id).get().steps().get(Step.installReal));
    }

    /** Installs tester and starts tests. */
    public void doInstallTester(JobType type) {
        doInstallTester(new JobId(instanceId, type));
    }

    /** Installs tester and starts tests. */
    public void doInstallTester(ApplicationId instanceId, JobType type) {
        doInstallTester(new JobId(instanceId, type));
    }

    /** Installs tester and starts tests. */
    public void doInstallTester(JobId job) {
        RunId id = currentRun(job).id();
        ZoneId zone = job.type().zone(tester.controller().system());

        assertEquals(unfinished, jobs.run(id).get().steps().get(Step.installTester));
        tester.configServer().nodeRepository().doUpgrade(new DeploymentId(TesterId.of(job.application()).id(), zone), Optional.empty(), currentRun(job).versions().targetPlatform());
        runner.advance(currentRun(job));
        assertEquals(unfinished, jobs.run(id).get().steps().get(Step.installTester));
        tester.configServer().convergeServices(TesterId.of(id.application()).id(), zone);
        runner.advance(currentRun(job));
        assertEquals(unfinished, jobs.run(id).get().steps().get(Step.installTester));
        setEndpoints(TesterId.of(id.application()).id(), zone);
        runner.advance(currentRun(job));
    }

    /** Completes tests with success. */
    public void doTests(JobType type) {
        doTests(new JobId(instanceId, type));
    }

    /** Completes tests with success. */
    public void doTests(ApplicationId instanceId, JobType type) {
        doTests(new JobId(instanceId, type));
    }

    /** Completes tests with success. */
    public void doTests(JobId job) {
        RunId id = currentRun(job).id();
        ZoneId zone = job.type().zone(tester.controller().system());

        // All installation is complete and endpoints are ready, so tests may begin.
        assertEquals(Step.Status.succeeded, jobs.run(id).get().steps().get(Step.installReal));
        assertEquals(Step.Status.succeeded, jobs.run(id).get().steps().get(Step.installTester));
        assertEquals(Step.Status.succeeded, jobs.run(id).get().steps().get(Step.startTests));

        assertEquals(unfinished, jobs.run(id).get().steps().get(Step.endTests));
        cloud.set(TesterCloud.Status.SUCCESS);
        runner.advance(currentRun(job));
        assertTrue(jobs.run(id).get().hasEnded());
        assertFalse(jobs.run(id).get().hasFailed());
        assertEquals(job.type().isProduction(), tester.instance(job.application()).deployments().containsKey(zone));
        assertTrue(tester.configServer().nodeRepository().list(zone, TesterId.of(id.application()).id()).isEmpty());
    }

    /** Removes endpoints from routing layer — always call this. */
    public void doTeardown(JobType type) {
        doTeardown(new JobId(instanceId, type));
    }

    /** Removes endpoints from routing layer — always call this. */
    public void doTeardown(ApplicationId instanceId, JobType type) {
        doTeardown(new JobId(instanceId, type));
    }

    /** Removes endpoints from routing layer — always call this. */
    public void doTeardown(JobId job) {
        ZoneId zone = job.type().zone(tester.controller().system());
        DeploymentId deployment = new DeploymentId(job.application(), zone);

        if ( ! instance().deployments().containsKey(zone))
            routing.removeEndpoints(deployment);
        routing.removeEndpoints(new DeploymentId(TesterId.of(job.application()).id(), zone));
    }

    /** Pulls the ready job trigger, and then runs the whole of the given job, successfully. */
    public void runJob(JobType type) {
        runJob(instanceId, type);
    }

    /** Pulls the ready job trigger, and then runs the whole of the given job, successfully. */
    public void runJob(ApplicationId instanceId, JobType type) {
        runJob(new JobId(instanceId, type));
    }

    /** Pulls the ready job trigger, and then runs the whole of the given job, successfully. */
    public void runJob(JobId job) {
        triggerJobs();
        doDeploy(job);
        doUpgrade(job);
        doConverge(job);
        if (job.type().environment().isManuallyDeployed())
            return;

        doInstallTester(job);
        doTests(job);
        doTeardown(job);
    }

    public void jobAborted(JobType type) {
        jobAborted(new JobId(instanceId, type));
    }

    public void jobAborted(ApplicationId instanceId, JobType type) {
        jobAborted(new JobId(instanceId, type));
    }

    public void jobAborted(JobId job) {
        triggerJobs();
        RunId id = currentRun(job).id();
        runner.advance(currentRun(job));
        assertEquals(aborted, jobs.run(id).get().status());
        assertTrue(jobs.run(id).get().hasEnded());
    }

    public void failDeployment(JobType type) {
        failDeployment(new JobId(instanceId, type));
    }

    public void failDeployment(ApplicationId instanceId, JobType type) {
        failDeployment(new JobId(instanceId, type));
    }

    public void failDeployment(JobId job) {
        triggerJobs();
        RunId id = currentRun(job).id();
        tester.configServer().throwOnNextPrepare(new IllegalArgumentException("Exception"));
        runner.advance(currentRun(job));
        assertTrue(jobs.run(id).get().hasFailed());
        assertTrue(jobs.run(id).get().hasEnded());
        doTeardown(job);
    }

    public void timeOutUpgrade(JobType type) {
        timeOutUpgrade(new JobId(instanceId, type));
    }

    public void timeOutUpgrade(ApplicationId instanceId, JobType type) {
        timeOutUpgrade(new JobId(instanceId, type));
    }

    public void timeOutUpgrade(JobId job) {
        triggerJobs();
        RunId id = currentRun(job).id();
        doDeploy(job);
        clock().advance(InternalStepRunner.installationTimeout.plusSeconds(1));
        runner.advance(currentRun(job));
        assertTrue(jobs.run(id).get().hasFailed());
        assertTrue(jobs.run(id).get().hasEnded());
        doTeardown(job);
    }

    public void timeOutConvergence(JobType type) {
        timeOutConvergence(new JobId(instanceId, type));
    }

    public void timeOutConvergence(ApplicationId instanceId, JobType type) {
        timeOutConvergence(new JobId(instanceId, type));
    }

    public void timeOutConvergence(JobId job) {
        triggerJobs();
        RunId id = currentRun(job).id();
        doDeploy(job);
        doUpgrade(job);
        clock().advance(InternalStepRunner.installationTimeout.plusSeconds(1));
        runner.advance(currentRun(job));
        assertTrue(jobs.run(id).get().hasFailed());
        assertTrue(jobs.run(id).get().hasEnded());
        doTeardown(job);
    }

    public RunId startSystemTestTests() {
        RunId id = newRun(JobType.systemTest);
        runner.run();
        tester.configServer().convergeServices(instanceId, JobType.systemTest.zone(tester.controller().system()));
        tester.configServer().convergeServices(testerId.id(), JobType.systemTest.zone(tester.controller().system()));
        setEndpoints(instanceId, JobType.systemTest.zone(tester.controller().system()));
        setEndpoints(testerId.id(), JobType.systemTest.zone(tester.controller().system()));
        runner.run();
        assertEquals(unfinished, jobs.run(id).get().steps().get(Step.endTests));
        return id;
    }

    /** Creates and submits a new application, and then starts the job of the given type. Use only once per test. */
    public RunId newRun(JobType type) {
        assertFalse(application().internal()); // Use this only once per test.
        newSubmission();
        tester.readyJobTrigger().maintain();

        if (type.isProduction()) {
            runJob(new JobId(instanceId, JobType.systemTest));
            runJob(new JobId(instanceId, JobType.stagingTest));
            tester.readyJobTrigger().maintain();
        }

        Run run = jobs.active().stream()
                      .filter(r -> r.id().type() == type)
                      .findAny()
                      .orElseThrow(() -> new AssertionError(type + " is not among the active: " + jobs.active()));
        return run.id();
    }

    static X509Certificate generateCertificate() {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        X500Principal subject = new X500Principal("CN=subject");
        return X509CertificateBuilder.fromKeypair(keyPair,
                                                  subject,
                                                  Instant.now(),
                                                  Instant.now().plusSeconds(1),
                                                  SignatureAlgorithm.SHA512_WITH_ECDSA,
                                                  BigInteger.valueOf(1))
                                     .build();
    }

    public void assertRunning(JobType type) {
        assertRunning(instanceId, type);
    }

    public void assertRunning(ApplicationId id, JobType type) {
        assertTrue(jobs.active().stream().anyMatch(run -> run.id().application().equals(id) && run.id().type() == type));
    }

    /** Flush all pending name services requests */
    public void flushDnsRequests() {
        nameServiceDispatcher.run();
        assertTrue("All name service requests dispatched",
                   controller().curator().readNameServiceQueue().requests().isEmpty());
    }

}
