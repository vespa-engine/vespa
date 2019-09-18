// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockBuildService;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.integration.ConfigServerMock;
import com.yahoo.vespa.hosted.controller.maintenance.JobControl;
import com.yahoo.vespa.hosted.controller.maintenance.NameServiceDispatcher;
import com.yahoo.vespa.hosted.controller.maintenance.OutstandingChangeDeployer;
import com.yahoo.vespa.hosted.controller.maintenance.ReadyJobsTrigger;
import com.yahoo.vespa.hosted.controller.maintenance.Upgrader;
import com.yahoo.vespa.hosted.controller.versions.ControllerVersion;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;

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

    public Instance application(String name) {
        return application(ApplicationId.from("tenant1", name, "default"));
    }

    public Instance application(ApplicationId application) {
        return controller().applications().require(application);
    }

    /** Re-compute and write version status */
    public void computeVersionStatus() {
        controller().updateVersionStatus(VersionStatus.compute(controller()));
    }

    public void upgradeController(Version version) {
        upgradeController(version, "badc0ffee", Instant.EPOCH);
    }

    /** Upgrade controller to given version */
    public void upgradeController(Version version, String commitSha, Instant commitDate) {
        controller().curator().writeControllerVersion(controller().hostname(), new ControllerVersion(version, commitSha, commitDate));
        computeVersionStatus();
    }

    /** Upgrade system applications in all zones to given version */
    public void upgradeSystemApplications(Version version) {
        for (ZoneApi zone : tester.zoneRegistry().zones().all().zones()) {
            for (SystemApplication application : SystemApplication.all()) {
                tester.configServer().setVersion(application.id(), zone.getId(), version);
                tester.configServer().convergeServices(application.id(), zone.getId());
            }
        }
        computeVersionStatus();
    }

    /** Upgrade entire system to given version */
    public void upgradeSystem(Version version) {
        upgradeController(version);
        upgradeSystemApplications(version);
        upgrader().maintain();
        readyJobTrigger().maintain();
    }

    /** Flush all pending name services requests */
    public void flushDnsRequests() {
        nameServiceDispatcher.run();
        assertTrue("All name service requests dispatched",
                   controller().curator().readNameServiceQueue().requests().isEmpty());
    }

    public void triggerUntilQuiescence() {
        while (deploymentTrigger().triggerReadyJobs() > 0);
    }

    public Version defaultPlatformVersion() {
        return configServer().initialVersion();
    }

    public Instance createApplication(String applicationName, String tenantName, long projectId, long propertyId) {
        return createApplication("default", applicationName, tenantName, projectId, propertyId);
    }

    public Instance createApplication(String instanceName, String applicationName, String tenantName, long projectId, long propertyId) {
        TenantName tenant = tester.createTenant(tenantName, UUID.randomUUID().toString(), propertyId);
        return tester.createApplication(tenant, applicationName, instanceName, projectId);
    }

    public void restartController() { tester.createNewController(); }

    public int hourOfDayAfter(Duration duration) {
        tester.clock().advance(duration);
        return tester.controller().clock().instant().atOffset(ZoneOffset.UTC).getHour();
    }

    /** Notify the controller about a job completing */
    public BuildJob jobCompletion(JobType job) {
        return new BuildJob(this::notifyJobCompletion, tester.serviceRegistry().artifactRepositoryMock()).type(job);
    }

    /** Simulate the full lifecycle of an application deployment as declared in given application package */
    public Instance createAndDeploy(String applicationName, int projectId, ApplicationPackage applicationPackage) {
        TenantName tenant = tester.createTenant("tenant1", "domain1", 1L);
        return createAndDeploy(tenant, applicationName, projectId, applicationPackage);
    }

    /** Simulate the full lifecycle of an application deployment as declared in given application package */
    public Instance createAndDeploy(TenantName tenant, String applicationName, int projectId, ApplicationPackage applicationPackage) {
        Instance instance = tester.createApplication(tenant, applicationName, "default", projectId);
        deployCompletely(instance, applicationPackage);
        return applications().require(instance.id());
    }

    /** Simulate the full lifecycle of an application deployment to prod.us-west-1 with the given upgrade policy */
    public Instance createAndDeploy(String applicationName, int projectId, String upgradePolicy) {
        return createAndDeploy(applicationName, projectId, applicationPackage(upgradePolicy));
    }

    /** Simulate the full lifecycle of an application deployment to prod.us-west-1 with the given upgrade policy */
    public Instance createAndDeploy(TenantName tenant, String applicationName, int projectId, String upgradePolicy) {
        return createAndDeploy(tenant, applicationName, projectId, applicationPackage(upgradePolicy));
    }

    /** Deploy application completely using the given application package */
    public void deployCompletely(Instance instance, ApplicationPackage applicationPackage) {
        deployCompletely(instance, applicationPackage, BuildJob.defaultBuildNumber);
    }

    public void completeDeploymentWithError(Instance instance, ApplicationPackage applicationPackage, long buildNumber, JobType failOnJob) {
        jobCompletion(JobType.component).application(instance)
                                        .buildNumber(buildNumber)
                                        .uploadArtifact(applicationPackage)
                                        .submit();
        completeDeployment(instance, applicationPackage, Optional.ofNullable(failOnJob));
    }

    public void deployCompletely(Instance instance, ApplicationPackage applicationPackage, long buildNumber) {
        completeDeploymentWithError(instance, applicationPackage, buildNumber, null);
    }

    private void completeDeployment(Instance instance, ApplicationPackage applicationPackage, Optional<JobType> failOnJob) {
        assertTrue(instance.id() + " has pending changes to deploy", applications().require(instance.id()).change().hasTargets());
        DeploymentSteps steps = controller().applications().deploymentTrigger().steps(applicationPackage.deploymentSpec());
        List<JobType> jobs = steps.jobs();
        for (JobType job : jobs) {
            boolean failJob = failOnJob.map(j -> j.equals(job)).orElse(false);
            deployAndNotify(instance, applicationPackage, ! failJob, job);
            if (failJob) {
                break;
            }
        }
        if (failOnJob.isPresent()) {
            assertTrue(applications().require(instance.id()).change().hasTargets());
            assertTrue(applications().require(instance.id()).deploymentJobs().hasFailures());
        } else {
            assertFalse(applications().require(instance.id()).change().hasTargets());
        }
        if (updateDnsAutomatically) {
            flushDnsRequests();
        }
    }

    public void completeUpgrade(Instance instance, Version version, String upgradePolicy) {
        completeUpgrade(instance, version, applicationPackage(upgradePolicy));
    }

    public void completeUpgrade(Instance instance, Version version, ApplicationPackage applicationPackage) {
        assertTrue(instance + " has a change", applications().require(instance.id()).change().hasTargets());
        assertEquals(Change.of(version), applications().require(instance.id()).change());
        completeDeployment(instance, applicationPackage, Optional.empty());
    }

    public void completeUpgradeWithError(Instance instance, Version version, String upgradePolicy, JobType failOnJob) {
        completeUpgradeWithError(instance, version, applicationPackage(upgradePolicy), Optional.of(failOnJob));
    }

    public void completeUpgradeWithError(Instance instance, Version version, ApplicationPackage applicationPackage, JobType failOnJob) {
        completeUpgradeWithError(instance, version, applicationPackage, Optional.of(failOnJob));
    }

    private void completeUpgradeWithError(Instance instance, Version version, ApplicationPackage applicationPackage, Optional<JobType> failOnJob) {
        assertTrue(applications().require(instance.id()).change().hasTargets());
        assertEquals(Change.of(version), applications().require(instance.id()).change());
        completeDeployment(instance, applicationPackage, failOnJob);
    }

    public void deploy(JobType job, Instance instance, ApplicationPackage applicationPackage) {
        deploy(job, instance, Optional.of(applicationPackage), false);
    }

    public void deploy(JobType job, Instance instance, ApplicationPackage applicationPackage,
                       boolean deployCurrentVersion) {
        deploy(job, instance, Optional.of(applicationPackage), deployCurrentVersion);
    }

    public void deploy(JobType job, Instance instance, Optional<ApplicationPackage> applicationPackage,
                       boolean deployCurrentVersion) {
        tester.deploy(instance, job.zone(controller().system()), applicationPackage, deployCurrentVersion);
    }

    public void deployAndNotify(Instance instance, String upgradePolicy, boolean success, JobType job) {
        deployAndNotify(instance, applicationPackage(upgradePolicy), success, job);
    }

    public void deployAndNotify(Instance instance, ApplicationPackage applicationPackage, boolean success, JobType job) {
        deployAndNotify(instance, Optional.of(applicationPackage), success, job);
    }

    public void deployAndNotify(Instance instance, boolean success, JobType job) {
        deployAndNotify(instance, Optional.empty(), success, job);
    }

    public void deployAndNotify(Instance instance, Optional<ApplicationPackage> applicationPackage, boolean success, JobType job) {
        if (success) {
            // Staging deploys twice, once with current version and once with new version
            if (job == JobType.stagingTest) {
                deploy(job, instance, applicationPackage, true);
            }
            deploy(job, instance, applicationPackage, false);
        }
        // Deactivate test deployments after deploy. This replicates the behaviour of the tenant pipeline
        if (job.isTest()) {
            controller().applications().deactivate(instance.id(), job.zone(controller().system()));
        }
        jobCompletion(job).application(instance).success(success).submit();
    }

    public Optional<JobStatus.JobRun> firstFailing(Instance instance, JobType job) {
        return tester.controller().applications().require(instance.id())
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
        return buildService().jobs().contains(ControllerTester.buildJob(application(application), job));
    }

}
