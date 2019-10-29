// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockBuildService;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.integration.ConfigServerMock;
import com.yahoo.vespa.hosted.controller.maintenance.JobControl;
import com.yahoo.vespa.hosted.controller.maintenance.NameServiceDispatcher;
import com.yahoo.vespa.hosted.controller.maintenance.OutstandingChangeDeployer;
import com.yahoo.vespa.hosted.controller.maintenance.ReadyJobsTrigger;
import com.yahoo.vespa.hosted.controller.maintenance.Upgrader;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * This class provides convenience methods for testing deployments
 *
 * @author bratseth
 * @author mpolden
 */
public class DeploymentTester {

    // Set a long interval so that maintainers never do scheduled runs during tests
    private static final Duration maintenanceInterval = Duration.ofDays(1);

    private final ControllerTester tester;
    private final Upgrader upgrader;
    private final OutstandingChangeDeployer outstandingChangeDeployer;
    private final ReadyJobsTrigger readyJobTrigger;
    private final NameServiceDispatcher nameServiceDispatcher;
    private final boolean updateDnsAutomatically;

    public DeploymentTester() {
        this(new ControllerTester());
    }

    public DeploymentTester(ControllerTester tester) {
        this(tester, true);
    }

    public DeploymentTester(ControllerTester tester, boolean updateDnsAutomatically) {
        this.tester = tester;
        this.updateDnsAutomatically = updateDnsAutomatically;
        tester.curator().writeUpgradesPerMinute(100);

        JobControl jobControl = new JobControl(tester.curator());
        this.upgrader = new Upgrader(tester.controller(), maintenanceInterval, jobControl, tester.curator());
        this.upgrader.setUpgradesPerMinute(1); // Anything that makes it at least one for any maintenance period is fine.
        this.outstandingChangeDeployer = new OutstandingChangeDeployer(tester.controller(), maintenanceInterval, jobControl);
        this.readyJobTrigger = new ReadyJobsTrigger(tester.controller(), maintenanceInterval, jobControl);
        this.nameServiceDispatcher = new NameServiceDispatcher(tester.controller(), Duration.ofHours(12),
                                                               new JobControl(tester.controller().curator()),
                                                               Integer.MAX_VALUE);
        atHourOfDay(5); // Set hour of day which always allows confidence to change
    }

    public DeploymentTester atHourOfDay(int hour) {
        var dateTime = tester.clock().instant().atZone(ZoneOffset.UTC);
        return at(LocalDateTime.of(dateTime.getYear(), dateTime.getMonth(), dateTime.getDayOfMonth(), hour,
                                   dateTime.getMinute(), dateTime.getSecond())
                               .toInstant(ZoneOffset.UTC));
    }

    public DeploymentTester at(Instant instant) {
        tester.clock().setInstant(instant);
        return this;
    }

    public Upgrader upgrader() { return upgrader; }

    public OutstandingChangeDeployer outstandingChangeDeployer() { return outstandingChangeDeployer; }

    public ReadyJobsTrigger readyJobTrigger() { return readyJobTrigger; }

    public Controller controller() { return tester.controller(); }

    public ApplicationController applications() { return tester.controller().applications(); }

    public MockBuildService buildService() { return tester.serviceRegistry().buildServiceMock(); }

    public DeploymentTrigger deploymentTrigger() { return tester.controller().applications().deploymentTrigger(); }

    public ManualClock clock() { return tester.clock(); }

    public ControllerTester controllerTester() { return tester; }

    public ConfigServerMock configServer() { return tester.serviceRegistry().configServerMock(); }

    public Application application(TenantAndApplicationId id) {
        return controller().applications().requireApplication(id);
    }

    public Instance defaultInstance(String name) {
        return instance(ApplicationId.from("tenant1", name, "default"));
    }

    public Instance defaultInstance(TenantAndApplicationId application) {
        return controller().applications().requireApplication(application).require(InstanceName.defaultName());
    }

    public Instance instance(ApplicationId application) {
        return controller().applications().requireInstance(application);
    }

    // TODO(mpolden): Change callers to use ControllerTester#computeVersionStatus and remove this
    public void computeVersionStatus() {
        tester.computeVersionStatus();
    }

    // TODO(mpolden): Change callers to use ControllerTester#upgradeController and remove this
    public void upgradeController(Version version) {
        tester.upgradeController(version);
    }

    // TODO(mpolden): Change callers to use ControllerTester#upgradeController and remove this
    public void upgradeController(Version version, String commitSha, Instant commitDate) {
        tester.upgradeController(version, commitSha, commitDate);
    }

    // TODO(mpolden): Change callers to use ControllerTester#upgradeSystemApplications and remove this
    public void upgradeSystemApplications(Version version) {
        tester.upgradeSystemApplications(version);
    }

    // TODO(mpolden): Change callers to use ControllerTester#upgradeSystemApplications and remove this
    public void upgradeSystemApplications(Version version, List<SystemApplication> systemApplications) {
        tester.upgradeSystemApplications(version, systemApplications);
    }

    // TODO(mpolden): Change callers to use ControllerTester#upgradeSystem and remove this
    public void upgradeSystem(Version version) {
        tester.upgradeSystem(version);
        upgrader().maintain();
        readyJobTrigger().maintain();
    }

    // TODO(mpolden): Change callers to use InternalDeploymentTester#flushDnsRequests and remove this
    public void flushDnsRequests() {
        nameServiceDispatcher.run();
        assertTrue("All name service requests dispatched",
                   controller().curator().readNameServiceQueue().requests().isEmpty());
    }

    public void triggerUntilQuiescence() {
        while (deploymentTrigger().triggerReadyJobs() > 0);
    }

    public Application createApplication(String applicationName, String tenantName, long projectId, long propertyId) {
        return createApplication("default", applicationName, tenantName, projectId, propertyId);
    }

    public Application createApplication(String instanceName, String applicationName, String tenantName, long projectId, long propertyId) {
        TenantName tenant = tester.createTenant(tenantName, UUID.randomUUID().toString(), propertyId);
        return tester.createApplication(tenant, applicationName, instanceName, projectId);
    }

    public void restartController() { tester.createNewController(); }

    /** Notify the controller about a job completing */
    public BuildJob jobCompletion(JobType job) {
        return new BuildJob(this::notifyJobCompletion, tester.serviceRegistry().artifactRepositoryMock()).type(job);
    }

    /** Simulate the full lifecycle of an application deployment as declared in given application package */
    public Application createAndDeploy(String applicationName, int projectId, ApplicationPackage applicationPackage) {
        TenantName tenant = tester.createTenant("tenant1", "domain1", 1L);
        return createAndDeploy(tenant, applicationName, projectId, applicationPackage);
    }

    /** Simulate the full lifecycle of an application deployment as declared in given application package */
    public Application createAndDeploy(TenantName tenant, String applicationName, int projectId, ApplicationPackage applicationPackage) {
        Application application = tester.createApplication(tenant, applicationName, "default", projectId);
        deployCompletely(application, applicationPackage);
        return applications().requireApplication(application.id());
    }

    /** Simulate the full lifecycle of an application deployment to prod.us-west-1 with the given upgrade policy */
    public Application createAndDeploy(String applicationName, int projectId, String upgradePolicy) {
        return createAndDeploy(applicationName, projectId, applicationPackage(upgradePolicy));
    }

    /** Simulate the full lifecycle of an application deployment to prod.us-west-1 with the given upgrade policy */
    public void createAndDeploy(TenantName tenant, String applicationName, int projectId, String upgradePolicy) {
        createAndDeploy(tenant, applicationName, projectId, applicationPackage(upgradePolicy));
    }

    /** Deploy application completely using the given application package */
    public void deployCompletely(Application application, ApplicationPackage applicationPackage) {
        deployCompletely(application, applicationPackage, BuildJob.defaultBuildNumber);
    }

    public void completeDeploymentWithError(Application application, ApplicationPackage applicationPackage, long buildNumber, JobType failOnJob) {
        jobCompletion(JobType.component).application(application)
                                        .buildNumber(buildNumber)
                                        .uploadArtifact(applicationPackage)
                                        .submit();
        completeDeployment(application, applicationPackage, Optional.ofNullable(failOnJob));
    }

    public void deployCompletely(Application application, ApplicationPackage applicationPackage, long buildNumber) {
        completeDeploymentWithError(application, applicationPackage, buildNumber, null);
    }

    private void completeDeployment(Application application, ApplicationPackage applicationPackage, Optional<JobType> failOnJob) {
        assertTrue(application.id() + " has pending changes to deploy", applications().requireApplication(application.id()).change().hasTargets());
        DeploymentSteps steps = controller().applications().deploymentTrigger().steps(applicationPackage.deploymentSpec());
        List<JobType> jobs = steps.jobs();
        // TODO jonmv: Change to list instances here.
        for (JobType job : jobs) {
            boolean failJob = failOnJob.map(j -> j.equals(job)).orElse(false);
            deployAndNotify(application.id().defaultInstance(), applicationPackage, ! failJob, job);
            if (failJob) {
                break;
            }
        }
        if (failOnJob.isPresent()) {
            assertTrue(applications().requireApplication(application.id()).change().hasTargets());
            assertTrue(defaultInstance(application.id()).deploymentJobs().hasFailures());
        } else {
            assertFalse(applications().requireApplication(application.id()).change().hasTargets());
        }
        if (updateDnsAutomatically) {
            flushDnsRequests();
        }
    }

    public void completeUpgrade(Application application, Version version, String upgradePolicy) {
        completeUpgrade(application, version, applicationPackage(upgradePolicy));
    }

    public void completeUpgrade(Application application, Version version, ApplicationPackage applicationPackage) {
        assertTrue(application + " has a change", applications().requireApplication(application.id()).change().hasTargets());
        assertEquals(Change.of(version), applications().requireApplication(application.id()).change());
        completeDeployment(application, applicationPackage, Optional.empty());
    }

    public void completeUpgradeWithError(Application application, Version version, String upgradePolicy, JobType failOnJob) {
        completeUpgradeWithError(application, version, applicationPackage(upgradePolicy), Optional.of(failOnJob));
    }

    public void completeUpgradeWithError(Application application, Version version, ApplicationPackage applicationPackage, JobType failOnJob) {
        completeUpgradeWithError(application, version, applicationPackage, Optional.of(failOnJob));
    }

    private void completeUpgradeWithError(Application application, Version version, ApplicationPackage applicationPackage, Optional<JobType> failOnJob) {
        assertTrue(applications().requireApplication(application.id()).change().hasTargets());
        assertEquals(Change.of(version), applications().requireApplication(application.id()).change());
        completeDeployment(application, applicationPackage, failOnJob);
    }

    public void deploy(JobType job, ApplicationId id, ApplicationPackage applicationPackage) {
        deploy(job, id, Optional.of(applicationPackage), false);
    }

    public void deploy(JobType job, ApplicationId id, ApplicationPackage applicationPackage,
                       boolean deployCurrentVersion) {
        deploy(job, id, Optional.of(applicationPackage), deployCurrentVersion);
    }

    public void deploy(JobType job, ApplicationId id, Optional<ApplicationPackage> applicationPackage,
                       boolean deployCurrentVersion) {
        tester.deploy(id, job.zone(controller().system()), applicationPackage, deployCurrentVersion);
    }

    public void deployAndNotify(Instance i, String upgradePolicy, boolean success, JobType job) {
        deployAndNotify(i.id(), applicationPackage(upgradePolicy), success, job);
    }

    public void deployAndNotify(ApplicationId id, ApplicationPackage applicationPackage, boolean success, JobType job) {
        deployAndNotify(id, Optional.of(applicationPackage), success, job);
    }

    public void deployAndNotify(Instance i, boolean success, JobType job) {
        deployAndNotify(i.id(), Optional.empty(), success, job);
    }
    public void deployAndNotify(ApplicationId id, boolean success, JobType job) {
        deployAndNotify(id, Optional.empty(), success, job);
    }

    public void deployAndNotify(ApplicationId id, Optional<ApplicationPackage> applicationPackage, boolean success, JobType job) {
        if (success) {
            // Staging deploys twice, once with current version and once with new version
            if (job == JobType.stagingTest) {
                deploy(job, id, applicationPackage, true);
            }
            deploy(job, id, applicationPackage, false);
        }
        // Deactivate test deployments after deploy. This replicates the behaviour of the tenant pipeline
        if (job.isTest()) {
            controller().applications().deactivate(id, job.zone(controller().system()));
        }
        jobCompletion(job).application(id).success(success).submit();
    }

    public Optional<JobStatus.JobRun> firstFailing(Instance instance, JobType job) {
        return tester.controller().applications().requireInstance(instance.id())
                     .deploymentJobs().jobStatus().get(job).firstFailing();
    }

    private void notifyJobCompletion(DeploymentJobs.JobReport report) {
        if (report.jobType() != JobType.component && ! buildService().remove(report.buildJob()))
            throw new IllegalArgumentException(report.jobType() + " is not running for " + report.applicationId());
        assertFalse("Unexpected entry '" + report.jobType() + "@" + report.projectId() + " in: " + buildService().jobs(),
                    buildService().remove(report.buildJob()));

        applications().deploymentTrigger().notifyOfCompletion(report);
        applications().deploymentTrigger().triggerReadyJobs();
    }

    public static ApplicationPackage applicationPackage(String upgradePolicy) {
        return new ApplicationPackageBuilder()
                .upgradePolicy(upgradePolicy)
                .environment(Environment.prod)
                .region("us-west-1")
                .region("us-east-3")
                .build();
    }

    public void assertRunning(JobType job, ApplicationId application) {
        assertTrue(String.format("Job %s for %s is running", job, application), isRunning(job, application));
    }

    public void assertNotRunning(JobType job, ApplicationId application) {
        assertFalse(String.format("Job %s for %s is not running", job, application), isRunning(job, application));
    }

    private boolean isRunning(JobType job, ApplicationId application) {
        return buildService().jobs().contains(ControllerTester.buildJob(instance(application).id(), job));
    }

}
