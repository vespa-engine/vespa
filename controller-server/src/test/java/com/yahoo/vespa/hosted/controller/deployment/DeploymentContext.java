// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.SourceRevision;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.integration.ConfigServerMock;
import com.yahoo.vespa.hosted.controller.maintenance.JobRunner;
import com.yahoo.vespa.hosted.controller.maintenance.NameServiceDispatcher;
import com.yahoo.vespa.hosted.controller.routing.GlobalRouting;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicy;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicyId;
import com.yahoo.vespa.hosted.controller.routing.Status;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.net.URI;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.succeeded;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.unfinished;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A deployment context for an application. This allows fine-grained control of the deployment of an application's
 * instances.
 *
 * References to this should be acquired through {@link DeploymentTester#newDeploymentContext}.
 *
 * Tester code that is not specific to a single application's deployment context should be added to either
 * {@link ControllerTester} or {@link DeploymentTester} instead of this class.
 *
 * @author mpolden
 * @author jonmv
 */
public class DeploymentContext {

    // Application packages are expensive to construct, and a given test typically only needs to the test in the context
    // of a single system. Construct them lazily.
    private static final Supplier<ApplicationPackage> applicationPackage = Suppliers.memoize(() -> new ApplicationPackageBuilder()
            .athenzIdentity(AthenzDomain.from("domain"), AthenzService.from("service"))
            .upgradePolicy("default")
            .region("us-central-1")
            .parallel("us-west-1", "us-east-3")
            .emailRole("author")
            .emailAddress("b@a")
            .build());

    private static final Supplier<ApplicationPackage> publicCdApplicationPackage = Suppliers.memoize(() -> new ApplicationPackageBuilder()
            .athenzIdentity(AthenzDomain.from("domain"), AthenzService.from("service"))
            .upgradePolicy("default")
            .region("aws-us-east-1c")
            .emailRole("author")
            .emailAddress("b@a")
            .trust(generateCertificate())
            .build());

    public static final SourceRevision defaultSourceRevision = new SourceRevision("repository1", "master", "commit1");

    private final TenantAndApplicationId applicationId;
    private final ApplicationId instanceId;
    private final TesterId testerId;
    private final JobController jobs;
    private final JobRunner runner;
    private final DeploymentTester tester;

    private ApplicationVersion lastSubmission = null;
    private boolean deferDnsUpdates = false;

    public DeploymentContext(ApplicationId instanceId, DeploymentTester tester) {
        this.applicationId = TenantAndApplicationId.from(instanceId);
        this.instanceId = instanceId;
        this.testerId = TesterId.of(instanceId);
        this.jobs = tester.controller().jobController();
        this.runner = tester.runner();
        this.tester = tester;
        createTenantAndApplication();
    }

    public static ApplicationPackage applicationPackage() {
        return applicationPackage.get();
    }

    public static ApplicationPackage publicApplicationPackage() {
        return publicCdApplicationPackage.get();
    }

    private void createTenantAndApplication() {
        try {
            var tenant = tester.controllerTester().createTenant(instanceId.tenant().value());
            tester.controllerTester().createApplication(tenant.value(), instanceId.application().value(), instanceId.instance().value());
        } catch (IllegalArgumentException ignored) { } // Tenant and or application may already exist with custom setup.
    }

    public Application application() {
        return tester.controller().applications().requireApplication(applicationId);
    }

    public Instance instance() {
        return tester.controller().applications().requireInstance(instanceId);
    }

    public DeploymentStatus deploymentStatus() {
        return tester.controller().jobController().deploymentStatus(application());
    }

    public Map<JobType, JobStatus> instanceJobs() {
        return deploymentStatus().instanceJobs(instanceId.instance());
    }

    public Deployment deployment(ZoneId zone) {
        return instance().deployments().get(zone);
    }

    public ApplicationId instanceId() {
        return instanceId;
    }

    public TesterId testerId() { return testerId; }

    public DeploymentId deploymentIdIn(ZoneId zone) {
        return new DeploymentId(instanceId, zone);
    }


    /** Completely deploy the latest change */
    public DeploymentContext deploy() {
        assertTrue("Application package submitted", application().latestVersion().isPresent());
        assertFalse("Submission is not already deployed", application().instances().values().stream()
                                                                       .anyMatch(instance -> instance.deployments().values().stream()
                                                                                                     .anyMatch(deployment -> deployment.applicationVersion().equals(lastSubmission))));
        assertEquals(application().latestVersion(), instance().change().application());
        completeRollout();
        assertFalse(instance().change().hasTargets());
        return this;
    }

    /** Upgrade platform of this to given version */
    public DeploymentContext deployPlatform(Version version) {
        assertEquals(instance().change().platform().get(), version);
        assertFalse(application().instances().values().stream()
                          .anyMatch(instance -> instance.deployments().values().stream()
                                                        .anyMatch(deployment -> deployment.version().equals(version))));
        assertEquals(version, instance().change().platform().get());
        assertFalse(instance().change().application().isPresent());

        completeRollout();

        assertTrue(application().productionDeployments().values().stream()
                                .allMatch(deployments -> deployments.stream()
                                                                    .allMatch(deployment -> deployment.version().equals(version))));

        for (var spec : application().deploymentSpec().instances())
            for (JobType type : new DeploymentSteps(spec, tester.controller()::system).productionJobs())
                assertTrue(tester.configServer().nodeRepository()
                                 .list(type.zone(tester.controller().system()), applicationId.defaultInstance()).stream() // TODO jonmv: support more
                                 .allMatch(node -> node.currentVersion().equals(version)));

        assertFalse(instance().change().hasTargets());
        return this;
    }

    /** Defer provisioning of load balancers in zones in given environment */
    public DeploymentContext deferLoadBalancerProvisioningIn(Environment... environment) {
        return deferLoadBalancerProvisioningIn(Set.of(environment));
    }

    public DeploymentContext deferLoadBalancerProvisioningIn(Set<Environment> environments) {
        configServer().deferLoadBalancerProvisioningIn(environments);
        return this;
    }

    /** Defer DNS updates */
    public DeploymentContext deferDnsUpdates() {
        deferDnsUpdates = true;
        return this;
    }

    /** Flush all pending DNS updates */
    public DeploymentContext flushDnsUpdates() {
        flushDnsUpdates(Integer.MAX_VALUE);
        assertTrue("All name service requests dispatched",
                   tester.controller().curator().readNameServiceQueue().requests().isEmpty());
        return this;
    }

    /** Flush count pending DNS updates */
    public DeploymentContext flushDnsUpdates(int count) {
        var dispatcher = new NameServiceDispatcher(tester.controller(), Duration.ofDays(1), count);
        dispatcher.run();
        return this;
    }

    /** Add a routing policy for this in given zone, with status set to inactive */
    public DeploymentContext addInactiveRoutingPolicy(ZoneId zone) {
        var clusterId = "default-inactive";
        var id = new RoutingPolicyId(instanceId, ClusterSpec.Id.from(clusterId), zone);
        var policies = new LinkedHashMap<>(tester.controller().curator().readRoutingPolicies(instanceId));
        policies.put(id, new RoutingPolicy(id, HostName.from("lb-host"),
                                           Optional.empty(),
                                           Set.of(EndpointId.of("default")),
                                           new Status(false, GlobalRouting.DEFAULT_STATUS)));
        tester.controller().curator().writeRoutingPolicies(instanceId, policies);
        return this;
    }

    /** Submit given application package for deployment */
    public DeploymentContext submit(ApplicationPackage applicationPackage) {
        return submit(applicationPackage, Optional.of(defaultSourceRevision));
    }

    /** Submit given application package for deployment */
    public DeploymentContext submit(ApplicationPackage applicationPackage, Optional<SourceRevision> sourceRevision) {
        var projectId = tester.controller().applications()
                              .requireApplication(applicationId)
                              .projectId()
                              .orElse(1000); // These are really set through submission, so just pick one if it hasn't been set.
        lastSubmission = jobs.submit(applicationId, sourceRevision, Optional.of("a@b"), Optional.empty(),
                                     projectId, applicationPackage, new byte[0]);
        return this;
    }


    /** Submit the default application package for deployment */
    public DeploymentContext submit() {
        return submit(tester.controller().system().isPublic() ? publicApplicationPackage() : applicationPackage());
    }

    /** Trigger all outstanding jobs, if any */
    public DeploymentContext triggerJobs() {
        tester.triggerJobs();
        return this;
    }

    /** Fail current deployment in given job */
    public DeploymentContext outOfCapacity(JobType type) {
        return failDeployment(type,
                              new ConfigServerException(URI.create("https://config.server"),
                                                        "Failed to deploy application",
                                                        "Out of capacity",
                                                        ConfigServerException.ErrorCode.OUT_OF_CAPACITY,
                                                        new RuntimeException("Out of capacity from test code")));
    }

    /** Fail current deployment in given job */
    public DeploymentContext failDeployment(JobType type) {
        return failDeployment(type, new IllegalArgumentException("Exception from test code"));
    }

    /** Fail current deployment in given job */
    private DeploymentContext failDeployment(JobType type, RuntimeException exception) {
        triggerJobs();
        var job = jobId(type);
        RunId id = currentRun(job).id();
        configServer().throwOnNextPrepare(exception);
        runner.advance(currentRun(job));
        assertTrue(jobs.run(id).get().hasFailed());
        assertTrue(jobs.run(id).get().hasEnded());
        return this;
    }

    /** Returns the last submitted application version */
    public Optional<ApplicationVersion> lastSubmission() {
        return Optional.ofNullable(lastSubmission);
    }

    /** Runs and returns all remaining jobs for the application, at most once, and asserts the current change is rolled out. */
    public DeploymentContext completeRollout() {
        triggerJobs();
        Set<JobType> jobs = new HashSet<>();
        List<Run> activeRuns;
        while ( ! (activeRuns = this.jobs.active(applicationId)).isEmpty())
            for (Run run : activeRuns)
                if (jobs.add(run.id().type())) {
                    runJob(run.id().type());
                    triggerJobs();
                }
                else
                    throw new AssertionError("Job '" + run.id() + "' was run twice");

        assertFalse("Change should have no targets, but was " + instance().change(), instance().change().hasTargets());
        return this;
    }

    /** Runs a deployment of the given package to the given dev/perf job, on the given version. */
    public DeploymentContext runJob(JobType type, ApplicationPackage applicationPackage, Version vespaVersion) {
        jobs.deploy(instanceId, type, Optional.ofNullable(vespaVersion), applicationPackage);
        return runJob(type);
    }

    /** Runs a deployment of the given package to the given dev/perf job. */
    public DeploymentContext runJob(JobType type, ApplicationPackage applicationPackage) {
        return runJob(type, applicationPackage, null);
    }

    /** Pulls the ready job trigger, and then runs the whole of the given job, successfully. */
    public DeploymentContext runJob(JobType type) {
        var job = jobId(type);
        triggerJobs();
        doDeploy(job);
        if (job.type().isDeployment()) {
            doUpgrade(job);
            doConverge(job);
            if (job.type().environment().isManuallyDeployed())
                return this;
        }
        if (job.type().isTest())
            doTests(job);
        return this;
    }

    /** Abort the running job of the given type. */
    public DeploymentContext abortJob(JobType type) {
        var job = jobId(type);
        assertNotSame(RunStatus.aborted, currentRun(job).status());
        jobs.abort(currentRun(job).id());
        jobAborted(type);
        return this;
    }

    /** Finish an already aborted run of the given type. */
    public DeploymentContext jobAborted(JobType type) {
        Run run = jobs.last(instanceId, type).get();
        assertSame(RunStatus.aborted, run.status());
        assertFalse(run.hasEnded());
        runner.advance(run);
        assertTrue(jobs.run(run.id()).get().hasEnded());
        return this;
    }

    /** Simulate upgrade time out in given job */
    public DeploymentContext timeOutUpgrade(JobType type) {
        var job = jobId(type);
        triggerJobs();
        RunId id = currentRun(job).id();
        doDeploy(job);
        tester.clock().advance(InternalStepRunner.Timeouts.of(tester.controller().system()).noNodesDown().plusSeconds(1));
        runner.advance(currentRun(job));
        assertTrue(jobs.run(id).get().hasFailed());
        assertTrue(jobs.run(id).get().hasEnded());
        return this;
    }

    /** Simulate convergence time out in given job */
    public DeploymentContext timeOutConvergence(JobType type) {
        var job = jobId(type);
        triggerJobs();
        RunId id = currentRun(job).id();
        doDeploy(job);
        doUpgrade(job);
        tester.clock().advance(InternalStepRunner.Timeouts.of(tester.controller().system()).noNodesDown().plusSeconds(1));
        runner.advance(currentRun(job));
        assertTrue(jobs.run(id).get().hasFailed());
        assertTrue(jobs.run(id).get().hasEnded());
        return this;
    }

    /** Deploy default application package, start a run for that change and return its ID */
    public RunId newRun(JobType type) {
        submit();
        tester.readyJobsTrigger().maintain();

        if (type.isProduction()) {
            runJob(JobType.systemTest);
            runJob(JobType.stagingTest);
            tester.readyJobsTrigger().maintain();
        }

        Run run = jobs.active().stream()
                      .filter(r -> r.id().type() == type)
                      .findAny()
                      .orElseThrow(() -> new AssertionError(type + " is not among the active: " + jobs.active()));
        return run.id();
    }

    /** Start tests in system test stage */
    public RunId startSystemTestTests() {
        var id = newRun(JobType.systemTest);
        var testZone = JobType.systemTest.zone(tester.controller().system());
        runner.run();
        if ( ! deferDnsUpdates)
            flushDnsUpdates();
        configServer().convergeServices(instanceId, testZone);
        configServer().convergeServices(testerId.id(), testZone);
        runner.run();
        assertEquals(unfinished, jobs.run(id).get().stepStatuses().get(Step.endTests));
        assertTrue(jobs.run(id).get().steps().get(Step.endTests).startTime().isPresent());
        return id;
    }

    public void assertRunning(JobType type) {
        assertTrue(jobId(type) + " should be among the active: " + jobs.active(),
                   jobs.active().stream().anyMatch(run -> run.id().application().equals(instanceId) && run.id().type() == type));
    }

    public void assertNotRunning(JobType type) {
        assertFalse(jobId(type) + " should not be among the active: " + jobs.active(),
                    jobs.active().stream().anyMatch(run -> run.id().application().equals(instanceId) && run.id().type() == type));
    }

    /** Deploys tester and real app, and completes tester and initial staging installation first if needed. */
    private void doDeploy(JobId job) {
        RunId id = currentRun(job).id();
        ZoneId zone = zone(job);
        DeploymentId deployment = new DeploymentId(job.application(), zone);

        // First step is always a deployment.
        runner.advance(currentRun(job));

        if ( ! deferDnsUpdates)
            flushDnsUpdates();

        if (job.type().isTest())
            doInstallTester(job);

        if (job.type() == JobType.stagingTest) { // Do the initial deployment and installation of the real application.
            assertEquals(unfinished, jobs.run(id).get().stepStatuses().get(Step.installInitialReal));
            tester.configServer().nodeRepository().doUpgrade(deployment, Optional.empty(), tester.configServer().application(job.application(), zone).get().version().get());
            configServer().convergeServices(id.application(), zone);
            runner.advance(currentRun(job));
            assertEquals(Step.Status.succeeded, jobs.run(id).get().stepStatuses().get(Step.installInitialReal));

            // All installation is complete and endpoints are ready, so setup may begin.
            assertEquals(Step.Status.succeeded, jobs.run(id).get().stepStatuses().get(Step.installInitialReal));
            assertEquals(Step.Status.succeeded, jobs.run(id).get().stepStatuses().get(Step.installTester));
            assertEquals(Step.Status.succeeded, jobs.run(id).get().stepStatuses().get(Step.startStagingSetup));

            assertEquals(unfinished, jobs.run(id).get().stepStatuses().get(Step.endStagingSetup));
            tester.cloud().set(TesterCloud.Status.SUCCESS);
            runner.advance(currentRun(job));
            assertEquals(succeeded, jobs.run(id).get().stepStatuses().get(Step.endStagingSetup));
        }
    }

    /** Upgrades nodes to target version. */
    private void doUpgrade(JobId job) {
        RunId id = currentRun(job).id();
        ZoneId zone = zone(job);
        DeploymentId deployment = new DeploymentId(job.application(), zone);

        assertEquals(unfinished, jobs.run(id).get().stepStatuses().get(Step.installReal));
        configServer().nodeRepository().doUpgrade(deployment, Optional.empty(), tester.configServer().application(job.application(), zone).get().version().get());
        runner.advance(currentRun(job));
    }

    /** Returns the current run for the given job type, and verifies it is still running normally. */
    private Run currentRun(JobId job) {
        Run run = jobs.last(job)
                      .filter(r -> r.id().type() == job.type())
                      .orElseThrow(() -> new AssertionError(job.type() + " is not among the active: " + jobs.active()));
        assertFalse(run.id() + " should not have failed yet", run.hasFailed());
        assertFalse(run.id() + " should not have ended yet", run.hasEnded());
        return run;
    }

    /** Lets nodes converge on new application version. */
    private void doConverge(JobId job) {
        RunId id = currentRun(job).id();
        ZoneId zone = zone(job);

        assertEquals(unfinished, jobs.run(id).get().stepStatuses().get(Step.installReal));
        configServer().convergeServices(id.application(), zone);
        runner.advance(currentRun(job));
        if (job.type().environment().isManuallyDeployed()) {
            assertEquals(Step.Status.succeeded, jobs.run(id).get().stepStatuses().get(Step.installReal));
            assertTrue(jobs.run(id).get().hasEnded());
            return;
        }
        assertEquals("Status of " + id, Step.Status.succeeded, jobs.run(id).get().stepStatuses().get(Step.installReal));
    }

    /** Installs tester and starts tests. */
    private void doInstallTester(JobId job) {
        RunId id = currentRun(job).id();
        ZoneId zone = zone(job);

        assertEquals(unfinished, jobs.run(id).get().stepStatuses().get(Step.installTester));
        configServer().nodeRepository().doUpgrade(new DeploymentId(TesterId.of(job.application()).id(), zone), Optional.empty(), tester.configServer().application(id.tester().id(), zone).get().version().get());
        runner.advance(currentRun(job));
        assertEquals(unfinished, jobs.run(id).get().stepStatuses().get(Step.installTester));
        configServer().convergeServices(TesterId.of(id.application()).id(), zone);
        runner.advance(currentRun(job));
        assertEquals(succeeded, jobs.run(id).get().stepStatuses().get(Step.installTester));
        runner.advance(currentRun(job));
    }

    /** Completes tests with success. */
    private void doTests(JobId job) {
        RunId id = currentRun(job).id();
        ZoneId zone = zone(job);

        // All installation is complete and endpoints are ready, so tests may begin.
        if (job.type().isDeployment())
            assertEquals(Step.Status.succeeded, jobs.run(id).get().stepStatuses().get(Step.installReal));
        assertEquals(Step.Status.succeeded, jobs.run(id).get().stepStatuses().get(Step.installTester));
        assertEquals(Step.Status.succeeded, jobs.run(id).get().stepStatuses().get(Step.startTests));

        assertEquals(unfinished, jobs.run(id).get().stepStatuses().get(Step.endTests));
        tester.cloud().set(TesterCloud.Status.SUCCESS);
        runner.advance(currentRun(job));
        assertTrue(jobs.run(id).get().hasEnded());
        assertFalse(jobs.run(id).get().hasFailed());
        assertEquals(job.type().isProduction(), instance().deployments().containsKey(zone));
        assertTrue(configServer().nodeRepository().list(zone, TesterId.of(id.application()).id()).isEmpty());
    }

    private JobId jobId(JobType type) {
        return new JobId(instanceId, type);
    }

    private ZoneId zone(JobId job) {
        return job.type().zone(tester.controller().system());
    }

    private ConfigServerMock configServer() {
        return tester.configServer();
    }

    private static X509Certificate generateCertificate() {
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

}
