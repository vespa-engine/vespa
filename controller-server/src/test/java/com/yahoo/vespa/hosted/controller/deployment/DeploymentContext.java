// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.google.common.base.Suppliers;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.ClusterSpec.Id;
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
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException.ErrorCode;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeFilter;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.SourceRevision;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud.Status;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterId;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.InternalStepRunner.Timeouts;
import com.yahoo.vespa.hosted.controller.integration.ConfigServerMock;
import com.yahoo.vespa.hosted.controller.maintenance.JobRunner;
import com.yahoo.vespa.hosted.controller.maintenance.NameServiceDispatcher;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicy;
import com.yahoo.vespa.hosted.controller.routing.RoutingPolicyId;
import com.yahoo.vespa.hosted.controller.routing.RoutingStatus;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.failed;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.succeeded;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.unfinished;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    public static final JobType systemTest             = JobType.deploymentTo(ZoneId.from("test", "us-east-1"));
    public static final JobType stagingTest            = JobType.deploymentTo(ZoneId.from("staging", "us-east-3"));
    public static final JobType productionUsEast3      = JobType.prod("us-east-3");
    public static final JobType testUsEast3            = JobType.test("us-east-3");
    public static final JobType productionUsWest1      = JobType.prod("us-west-1");
    public static final JobType testUsWest1            = JobType.test("us-west-1");
    public static final JobType productionUsCentral1   = JobType.prod("us-central-1");
    public static final JobType testUsCentral1         = JobType.test("us-central-1");
    public static final JobType productionApNortheast1 = JobType.prod("ap-northeast-1");
    public static final JobType testApNortheast1       = JobType.test("ap-northeast-1");
    public static final JobType productionApNortheast2 = JobType.prod("ap-northeast-2");
    public static final JobType testApNortheast2       = JobType.test("ap-northeast-2");
    public static final JobType productionApSoutheast1 = JobType.prod("ap-southeast-1");
    public static final JobType testApSoutheast1       = JobType.test("ap-southeast-1");
    public static final JobType productionEuWest1      = JobType.prod("eu-west-1");
    public static final JobType testEuWest1            = JobType.test("eu-west-1");
    public static final JobType productionAwsUsEast1a  = JobType.prod("aws-us-east-1a");
    public static final JobType testAwsUsEast1a        = JobType.test("aws-us-east-1a");
    public static final JobType devUsEast1             = JobType.dev("us-east-1");
    public static final JobType devAwsUsEast2a         = JobType.dev("aws-us-east-2a");
    public static final JobType perfUsEast3            = JobType.perf("us-east-3");

    private final AtomicLong salt = new AtomicLong();

    // Application packages are expensive to construct, and a given test typically only needs to the test in the context
    // of a single system. Construct them lazily.
    private static final Supplier<ApplicationPackage> applicationPackage = Suppliers.memoize(() -> new ApplicationPackageBuilder()
            .athenzIdentity(AthenzDomain.from("domain"), AthenzService.from("service"))
            .upgradePolicy("default")
            .region("us-central-1")
            .parallel("us-west-1", "us-east-3")
            .emailRole("author")
            .emailAddress("b@a")
            .build())::get;

    private static final Supplier<ApplicationPackage> publicCdApplicationPackage = Suppliers.memoize(() -> new ApplicationPackageBuilder()
            .athenzIdentity(AthenzDomain.from("domain"), AthenzService.from("service"))
            .upgradePolicy("default")
            .region("aws-us-east-1c")
            .emailRole("author")
            .emailAddress("b@a")
            .trust(generateCertificate())
            .build())::get;

    public static final SourceRevision defaultSourceRevision = new SourceRevision("repository1", "master", "commit1");

    private final TenantAndApplicationId applicationId;
    private final ApplicationId instanceId;
    private final TesterId testerId;
    private final JobController jobs;
    private final JobRunner runner;
    private final DeploymentTester tester;

    private RevisionId lastSubmission = null;
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


    /** Completely deploy the current change */
    public DeploymentContext deploy() {
        Application application = application();
        assertTrue(application.revisions().last().isPresent(), "Application package submitted");
        assertFalse(application.instances().values().stream()
                               .anyMatch(instance -> instance.deployments().values().stream()
                                                     .anyMatch(deployment -> deployment.revision().equals(lastSubmission))),
                    "Submission is not already deployed");
        completeRollout(application.deploymentSpec().instances().size() > 1);
        for (var instance : application().instances().values()) {
            assertFalse(instance.change().hasTargets());
        }
        return this;
    }

    /** Upgrade platform of this to given version */
    public DeploymentContext deployPlatform(Version version) {
        assertEquals(instance().change().platform().get(), version);
        assertFalse(application().instances().values().stream()
                                 .anyMatch(instance -> instance.deployments().values().stream()
                                                               .anyMatch(deployment -> deployment.version().equals(version))));
        assertEquals(version, instance().change().platform().get());
        assertFalse(instance().change().revision().isPresent());

        completeRollout();

        assertTrue(application().productionDeployments().values().stream()
                                .allMatch(deployments -> deployments.stream()
                                                                    .allMatch(deployment -> deployment.version().equals(version))));

        for (JobId job : deploymentStatus().jobs().matching(job -> job.id().type().isProduction()).mapToList(JobStatus::id))
            assertTrue(tester.configServer().nodeRepository()
                             .list(job.type().zone(),
                                   NodeFilter.all().applications(job.application())).stream()
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
        assertEquals(List.of(),
                     tester.controller().curator().readNameServiceQueue().requests(),
                     "All name service requests dispatched");
        return this;
    }

    /** Flush count pending DNS updates */
    public DeploymentContext flushDnsUpdates(int count) {
        var dispatcher = new NameServiceDispatcher(tester.controller(), Duration.ofSeconds(count));
        try {
            dispatcher.run();
            return this;
        }
        finally {
            dispatcher.shutdown();
        }
    }

    /** Submit given application package for deployment */
    public DeploymentContext resubmit(ApplicationPackage applicationPackage) {
        return submit(applicationPackage, Optional.of(defaultSourceRevision), salt.get(), 0);
    }

    /** Submit given application package for deployment */
    public DeploymentContext submit(ApplicationPackage applicationPackage, int risk) {
        return submit(applicationPackage, Optional.of(defaultSourceRevision), salt.incrementAndGet(), risk);
    }

    /** Submit given application package for deployment */
    public DeploymentContext submit(ApplicationPackage applicationPackage) {
        return submit(applicationPackage, Optional.of(defaultSourceRevision));
    }

    /** Submit given application package for deployment */
    public DeploymentContext submit(ApplicationPackage applicationPackage, long salt, int risk) {
        return submit(applicationPackage, Optional.of(defaultSourceRevision), salt, risk);
    }

    /** Submit given application package for deployment */
    public DeploymentContext submit(ApplicationPackage applicationPackage, Optional<SourceRevision> sourceRevision) {
        return submit(applicationPackage, sourceRevision, salt.incrementAndGet(), 0);
    }

    /** Submit given application package for deployment */
    public DeploymentContext submit(ApplicationPackage applicationPackage, Optional<SourceRevision> sourceRevision, long salt, int risk) {
        var projectId = tester.controller().applications()
                              .requireApplication(applicationId)
                              .projectId()
                              .orElse(1000); // These are really set through submission, so just pick one if it hasn't been set.
        var testerpackage = new byte[]{ (byte) (salt >> 56), (byte) (salt >> 48), (byte) (salt >> 40), (byte) (salt >> 32), (byte) (salt >> 24), (byte) (salt >> 16), (byte) (salt >> 8), (byte) salt };
        lastSubmission = jobs.submit(applicationId, new Submission(applicationPackage, testerpackage, Optional.empty(), sourceRevision, Optional.of("a@b"), Optional.empty(), tester.clock().instant(), risk), projectId).id();
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
    public DeploymentContext nodeAllocationFailure(JobType type) {
        return failDeployment(type,
                              new ConfigServerException(ErrorCode.NODE_ALLOCATION_FAILURE,
                                                        "Node allocation failure",
                                                        "Failed to deploy application"));
    }

    /** Fail current deployment in given job */
    public DeploymentContext failDeployment(JobType type) {
        return failDeployment(type, new RuntimeException("Exception from test code"));
    }

    /** Fail current deployment in given job */
    private DeploymentContext failDeployment(JobType type, RuntimeException exception) {
        configServer().throwOnNextPrepare(exception);
        runJobExpectingFailure(type, null);
        return this;
    }

    /** Run given job and expect it to fail with given message, if any */
    public DeploymentContext runJobExpectingFailure(JobType type, String messagePart) {
        triggerJobs();
        var job = jobId(type);
        RunId id = currentRun(job).id();
        runner.advance(currentRun(job));
        Run run = jobs.run(id);
        assertTrue(run.hasFailed());
        assertTrue(run.hasEnded());
        if (messagePart != null) {
            Optional<Step> firstFailing = run.stepStatuses().entrySet().stream()
                                             .filter(kv -> kv.getValue() == failed)
                                             .map(Entry::getKey)
                                             .findFirst();
            assertTrue(firstFailing.isPresent(), "Found failing step");
            Optional<RunLog> details = jobs.details(id);
            assertTrue(details.isPresent(), "Found log entries for run " + id);
            assertTrue(details.get().get(firstFailing.get()).stream()
                              .anyMatch(entry -> entry.message().contains(messagePart)),
                       "Found log message containing '" + messagePart + "'");
        }
        return this;
    }

    /** Returns the last submitted application version */
    public Optional<RevisionId> lastSubmission() {
        return Optional.ofNullable(lastSubmission);
    }

    public DeploymentContext completeRollout() {
        return completeRollout(false);
    }

    /** Runs and returns all remaining jobs for the application, at most once, and asserts the current change is rolled out. */
    public DeploymentContext completeRollout(boolean multiInstance) {
        triggerJobs();
        Map<ApplicationId, Map<JobType, Run>> runsByInstance = new HashMap<>();
        List<Run> activeRuns;
        while ( ! (activeRuns = this.jobs.active(applicationId)).isEmpty())
            for (Run run : activeRuns) {
                Map<JobType, Run> runs = runsByInstance.computeIfAbsent(run.id().application(), k -> new HashMap<>());
                Run previous = runs.put(run.id().type(), run);
                if (previous != null && run.versions().equals(previous.versions()) && run.id().type().zone().equals(previous.id().type().zone())) {
                    throw new AssertionError("Job '" + run.id() + "' was run twice on same versions");
                }
                runJob(run.id().type(), run.id().application());
                if (multiInstance) {
                    tester.outstandingChangeDeployer().run();
                }
                triggerJobs();
            }

        assertFalse(instance().change().hasTargets(), "Change should have no targets, but was " + instance().change());
        return this;
    }

    /** Runs a deployment of the given package to the given dev/perf job, on the given version. */
    public DeploymentContext runJob(JobType type, ApplicationPackage applicationPackage, Version vespaVersion) {
        jobs.deploy(instanceId, type, Optional.ofNullable(vespaVersion), applicationPackage, false, true);
        return runJob(type);
    }

    /** Runs a deployment of the given package to the given manually deployable job. */
    public DeploymentContext runJob(JobType type, ApplicationPackage applicationPackage) {
        return runJob(type, applicationPackage, null);
    }

    /** Runs a deployment of the given package to the given manually deployable zone. */
    public DeploymentContext runJob(ZoneId zone, ApplicationPackage applicationPackage) {
        return runJob(JobType.deploymentTo(zone), applicationPackage, null);
    }

    /** Pulls the ready job trigger, and then runs the whole of the given job in the instance of this, successfully. */
    public DeploymentContext runJob(JobType type) {
        return runJob(type, instanceId);
    }

    /** Runs the job, failing tests with noTests status, or with regular testFailure. */
    public DeploymentContext failTests(JobType type, boolean noTests) {
        if ( ! type.isTest()) throw new IllegalArgumentException(type + " does not run tests");
        var job = new JobId(instanceId, type);
        triggerJobs();
        doDeploy(job);
        if (job.type().isDeployment()) {
            doUpgrade(job);
            doConverge(job);
            if (job.type().environment().isManuallyDeployed())
                return this;
        }

        RunId id = currentRun(job).id();

        assertEquals(unfinished, jobs.run(id).stepStatuses().get(Step.endTests));
        tester.cloud().set(noTests ? Status.NO_TESTS : Status.FAILURE);
        runner.advance(currentRun(job));
        assertTrue(jobs.run(id).hasEnded());
        assertTrue(configServer().nodeRepository().list(job.type().zone(), NodeFilter.all().applications(TesterId.of(instanceId).id())).isEmpty());

        return this;
    }

    /** Pulls the ready job trigger, and then runs the whole of job for the given instance, successfully. */
    private DeploymentContext runJob(JobType type, ApplicationId instance) {
        triggerJobs();
        Run run = currentRun(new JobId(instance, type));
        assertEquals(type.zone(), run.id().type().zone());
        JobId job = run.id().job();
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
        jobs.abort(currentRun(job).id(), "DeploymentContext.abortJob", false);
        jobAborted(type);
        return this;
    }

    /** Finish an already aborted run of the given type. */
    public DeploymentContext jobAborted(JobType type) {
        Run run = jobs.last(instanceId, type).get();
        assertSame(RunStatus.aborted, run.status());
        assertFalse(run.hasEnded());
        runner.advance(run);
        assertTrue(jobs.run(run.id()).hasEnded());
        return this;
    }

    /** Simulate upgrade time out in given job */
    public DeploymentContext timeOutUpgrade(JobType type) {
        var job = jobId(type);
        triggerJobs();
        RunId id = currentRun(job).id();
        doDeploy(job);
        tester.clock().advance(Timeouts.of(tester.controller().system()).noNodesDown().plusSeconds(1));
        runner.advance(currentRun(job));
        assertTrue(jobs.run(id).hasFailed());
        assertTrue(jobs.run(id).hasEnded());
        return this;
    }

    /** Simulate convergence time out in given job */
    public DeploymentContext timeOutConvergence(JobType type) {
        var job = jobId(type);
        triggerJobs();
        RunId id = currentRun(job).id();
        doDeploy(job);
        doUpgrade(job);
        tester.clock().advance(Timeouts.of(tester.controller().system()).noNodesDown().plusSeconds(1));
        runner.advance(currentRun(job));
        assertTrue(jobs.run(id).hasFailed());
        assertTrue(jobs.run(id).hasEnded());
        return this;
    }

    /** Deploy default application package, start a run for that change and return its ID */
    public RunId newRun(JobType type) {
        submit();
        tester.readyJobsTrigger().maintain();

        if (type.isProduction()) {
            runJob(systemTest);
            runJob(stagingTest);
            tester.readyJobsTrigger().maintain();
        }

        Run run = jobs.active().stream()
                      .filter(r -> r.id().type().equals(type))
                      .findAny()
                      .orElseThrow(() -> new AssertionError(type + " is not among the active: " + jobs.active()));
        return run.id();
    }

    /** Start tests in system test stage */
    public RunId startSystemTestTests() {
        var id = newRun(systemTest);
        var testZone = systemTest.zone();
        runner.run();
        if ( ! deferDnsUpdates)
            flushDnsUpdates();
        configServer().convergeServices(instanceId, testZone);
        configServer().convergeServices(testerId.id(), testZone);
        runner.run();
        assertEquals(unfinished, jobs.run(id).stepStatuses().get(Step.endTests));
        assertTrue(jobs.run(id).steps().get(Step.endTests).startTime().isPresent());
        return id;
    }

    public void assertRunning(JobType type) {
        assertTrue(jobs.active().stream().anyMatch(run -> run.id().application().equals(instanceId) && run.id().type().equals(type)),
                   jobId(type) + " should be among the active: " + jobs.active());
    }

    public void assertNotRunning(JobType type) {
        assertFalse(jobs.active().stream().anyMatch(run -> run.id().application().equals(instanceId) && run.id().type().equals(type)),
                    jobId(type) + " should not be among the active: " + jobs.active());
    }

    /** Deploys tester and real app, and completes tester and initial staging installation first if needed. */
    private void doDeploy(JobId job) {
        RunId id = currentRun(job).id();
        ZoneId zone = job.type().zone();
        DeploymentId deployment = new DeploymentId(job.application(), zone);

        // First step is always a deployment.
        runner.advance(currentRun(job));

        if ( ! deferDnsUpdates)
            flushDnsUpdates();

        if (job.type().isTest())
            doInstallTester(job);

        if (job.type().equals(stagingTest)) { // Do the initial deployment and installation of the real application.
            assertEquals(unfinished, jobs.run(id).stepStatuses().get(Step.installInitialReal));
            tester.configServer().nodeRepository().doUpgrade(deployment, Optional.empty(), tester.configServer().application(job.application(), zone).get().version().get());
            configServer().convergeServices(id.application(), zone);
            runner.advance(currentRun(job));
            assertEquals(succeeded, jobs.run(id).stepStatuses().get(Step.installInitialReal));

            // All installation is complete and endpoints are ready, so setup may begin.
            assertEquals(succeeded, jobs.run(id).stepStatuses().get(Step.installInitialReal));
            assertEquals(succeeded, jobs.run(id).stepStatuses().get(Step.installTester));
            assertEquals(succeeded, jobs.run(id).stepStatuses().get(Step.startStagingSetup));

            assertEquals(unfinished, jobs.run(id).stepStatuses().get(Step.endStagingSetup));
            tester.cloud().set(Status.SUCCESS);
            runner.advance(currentRun(job));
            assertEquals(succeeded, jobs.run(id).stepStatuses().get(Step.endStagingSetup));

            if ( ! deferDnsUpdates)
                flushDnsUpdates();
        }
    }

    /** Upgrades nodes to target version. */
    private void doUpgrade(JobId job) {
        RunId id = currentRun(job).id();
        ZoneId zone = job.type().zone();
        DeploymentId deployment = new DeploymentId(job.application(), zone);

        assertEquals(unfinished, jobs.run(id).stepStatuses().get(Step.installReal));
        configServer().nodeRepository().doUpgrade(deployment, Optional.empty(), tester.configServer().application(job.application(), zone).get().version().get());
        runner.advance(currentRun(job));
    }

    /** Returns the current run for the given job type, and verifies it is still running normally. */
    private Run currentRun(JobId job) {
        Run run = jobs.last(job)
                      .filter(r -> r.id().type().equals(job.type()))
                      .orElseThrow(() -> new AssertionError(job.type() + " is not among the active: " + jobs.active()));
        assertFalse(run.hasFailed(), run.id() + " should not have failed yet: " + run);
        assertFalse(run.hasEnded(), run.id() + " should not have ended yet: " + run);
        return run;
    }

    /** Lets nodes converge on new application version. */
    private void doConverge(JobId job) {
        RunId id = currentRun(job).id();
        ZoneId zone = job.type().zone();

        assertEquals(unfinished, jobs.run(id).stepStatuses().get(Step.installReal));
        configServer().convergeServices(id.application(), zone);
        runner.advance(currentRun(job));
        if (job.type().environment().isManuallyDeployed()) {
            assertEquals(succeeded, jobs.run(id).stepStatuses().get(Step.installReal));
            assertTrue(jobs.run(id).hasEnded());
            return;
        }
        assertEquals(succeeded, jobs.run(id).stepStatuses().get(Step.installReal), "Status of " + id);
    }

    /** Installs tester and starts tests. */
    private void doInstallTester(JobId job) {
        RunId id = currentRun(job).id();
        ZoneId zone = job.type().zone();

        assertEquals(unfinished, jobs.run(id).stepStatuses().get(Step.installTester));
        configServer().nodeRepository().doUpgrade(new DeploymentId(TesterId.of(job.application()).id(), zone), Optional.empty(), tester.configServer().application(id.tester().id(), zone).get().version().get());
        runner.advance(currentRun(job));
        assertEquals(unfinished, jobs.run(id).stepStatuses().get(Step.installTester));
        configServer().convergeServices(TesterId.of(id.application()).id(), zone);
        runner.advance(currentRun(job));
        assertEquals(succeeded, jobs.run(id).stepStatuses().get(Step.installTester));
        runner.advance(currentRun(job));
    }

    /** Completes tests with success. */
    private void doTests(JobId job) {
        RunId id = currentRun(job).id();
        ZoneId zone = job.type().zone();

        // All installation is complete and endpoints are ready, so tests may begin.
        if (job.type().isDeployment())
            assertEquals(succeeded, jobs.run(id).stepStatuses().get(Step.installReal));
        assertEquals(succeeded, jobs.run(id).stepStatuses().get(Step.installTester));
        assertEquals(succeeded, jobs.run(id).stepStatuses().get(Step.startTests));

        assertEquals(unfinished, jobs.run(id).stepStatuses().get(Step.endTests));
        tester.cloud().set(Status.SUCCESS);
        runner.advance(currentRun(job));
        assertTrue(jobs.run(id).hasEnded());
        assertFalse(jobs.run(id).hasFailed());
        Instance instance = tester.application(TenantAndApplicationId.from(instanceId)).require(id.application().instance());
        assertEquals(job.type().isProduction(), instance.deployments().containsKey(zone));
        assertTrue(configServer().nodeRepository().list(zone, NodeFilter.all().applications(TesterId.of(instance.id()).id())).isEmpty());
    }

    private JobId jobId(JobType type) {
        return new JobId(instanceId, type);
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
