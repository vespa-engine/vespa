// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.TenantName;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.ArtifactRepositoryMock;
import com.yahoo.vespa.hosted.controller.ConfigServerClientMock;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockBuildService;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.maintenance.JobControl;
import com.yahoo.vespa.hosted.controller.maintenance.ReadyJobsTrigger;
import com.yahoo.vespa.hosted.controller.maintenance.Upgrader;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final ReadyJobsTrigger readyJobTrigger;

    public DeploymentTester() {
        this(new ControllerTester());
    }

    public DeploymentTester(ControllerTester tester) {
        this.tester = tester;
        tester.curator().writeUpgradesPerMinute(100);
        this.upgrader = new Upgrader(tester.controller(), maintenanceInterval, new JobControl(tester.curator()),
                                     tester.curator());
        this.readyJobTrigger = new ReadyJobsTrigger(tester.controller(), maintenanceInterval,
                                                    new JobControl(tester.curator()));
    }

    public Upgrader upgrader() { return upgrader; }

    public ReadyJobsTrigger readyJobTrigger() { return readyJobTrigger; }

    public Controller controller() { return tester.controller(); }

    public ApplicationController applications() { return tester.controller().applications(); }

    public MockBuildService buildService() { return tester.buildService(); }

    public DeploymentTrigger deploymentTrigger() { return tester.controller().applications().deploymentTrigger(); }

    public ManualClock clock() { return tester.clock(); }

    public ControllerTester controllerTester() { return tester; }

    public ConfigServerClientMock configServer() { return tester.configServer(); }

    public ArtifactRepositoryMock artifactRepository() { return tester.artifactRepository(); }

    public Application application(String name) {
        return application(ApplicationId.from("tenant1", name, "default"));
    }

    public Application application(ApplicationId application) {
        return controller().applications().require(application);
    }

    public void updateVersionStatus() {
        updateVersionStatus(tester.controller().systemVersion());
    }

    public void updateVersionStatus(Version newVersion) {
        controller().curator().writeControllerVersion(controller().hostname(), newVersion);
        configServer().setDefaultVersion(newVersion);
        controller().updateVersionStatus(VersionStatus.compute(controller()));
    }

    public void upgradeSystem(Version version) {
        updateVersionStatus(version);
        upgrader().maintain();
        readyJobTrigger().maintain();
    }

    public Version defaultVespaVersion() {
        return configServer().getDefaultVersion();
    }

    public Application createApplication(String applicationName, String tenantName, long projectId, Long propertyId) {
        TenantName tenant = tester.createTenant(tenantName, UUID.randomUUID().toString(), propertyId);
        return tester.createApplication(tenant, applicationName, "default", projectId);
    }

    public void restartController() { tester.createNewController(); }

    /** Notify the controller about a job completing */
    public BuildJob jobCompletion(JobType job) {
        return new BuildJob(this::notifyJobCompletion, tester.artifactRepository()).type(job);
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
        return applications().require(application.id());
    }

    /** Simulate the full lifecycle of an application deployment to prod.us-west-1 with the given upgrade policy */
    public Application createAndDeploy(String applicationName, int projectId, String upgradePolicy) {
        return createAndDeploy(applicationName, projectId, applicationPackage(upgradePolicy));
    }

    /** Simulate the full lifecycle of an application deployment to prod.us-west-1 with the given upgrade policy */
    public Application createAndDeploy(TenantName tenant, String applicationName, int projectId, String upgradePolicy) {
        return createAndDeploy(tenant, applicationName, projectId, applicationPackage(upgradePolicy));
    }

    /** Complete an ongoing deployment */
    public void deployCompletely(String applicationName) {
        deployCompletely(applications().require(ApplicationId.from("tenant1", applicationName, "default")),
                         applicationPackage("default"));
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
        completeDeployment(application, applicationPackage, Optional.of(failOnJob), true);
        assertTrue(applications().require(application.id()).change().isPresent());
    }

    public void deployCompletely(Application application, ApplicationPackage applicationPackage, long buildNumber) {
        jobCompletion(JobType.component).application(application)
                                        .buildNumber(buildNumber)
                                        .uploadArtifact(applicationPackage)
                                        .submit();
        assertTrue(applications().require(application.id()).change().isPresent());
        completeDeployment(application, applicationPackage, Optional.empty(), true);
    }

    private void completeDeployment(Application application, ApplicationPackage applicationPackage,
                                    Optional<JobType> failOnJob, boolean includingProductionZones) {
        DeploymentOrder order = new DeploymentOrder(controller()::system);
        List<JobType> jobs = order.jobsFrom(applicationPackage.deploymentSpec());
        if ( ! includingProductionZones)
            jobs = jobs.stream().filter(job -> ! job.isProduction()).collect(Collectors.toList());
        for (JobType job : jobs) {
            boolean failJob = failOnJob.map(j -> j.equals(job)).orElse(false);
            deployAndNotify(application, applicationPackage, ! failJob, job);
            if (failJob) {
                break;
            }
        }
        if (failOnJob.isPresent()) {
            assertTrue(applications().require(application.id()).change().isPresent());
            assertTrue(applications().require(application.id()).deploymentJobs().hasFailures());
        } else if (includingProductionZones) {
            assertFalse(applications().require(application.id()).change().isPresent());
        }
        else {
            assertTrue(applications().require(application.id()).change().isPresent());
        }
    }

    public void completeUpgrade(Application application, Version version, String upgradePolicy) {
        completeUpgrade(application, version, applicationPackage(upgradePolicy));
    }

    public void completeUpgrade(Application application, Version version, ApplicationPackage applicationPackage) {
        assertTrue(application + " has a change", applications().require(application.id()).change().isPresent());
        assertEquals(Change.of(version), applications().require(application.id()).change());
        completeDeployment(application, applicationPackage, Optional.empty(), true);
    }

    public void completeUpgradeWithError(Application application, Version version, String upgradePolicy, JobType failOnJob) {
        completeUpgradeWithError(application, version, applicationPackage(upgradePolicy), Optional.of(failOnJob));
    }

    public void completeUpgradeWithError(Application application, Version version, ApplicationPackage applicationPackage, JobType failOnJob) {
        completeUpgradeWithError(application, version, applicationPackage, Optional.of(failOnJob));
    }

    private void completeUpgradeWithError(Application application, Version version, ApplicationPackage applicationPackage, Optional<JobType> failOnJob) {
        assertTrue(applications().require(application.id()).change().isPresent());
        assertEquals(Change.of(version), applications().require(application.id()).change());
        completeDeployment(application, applicationPackage, failOnJob, true);
    }

    public void deploy(JobType job, Application application, ApplicationPackage applicationPackage) {
        deploy(job, application, Optional.of(applicationPackage), false);
    }

    public void deploy(JobType job, Application application, ApplicationPackage applicationPackage,
                       boolean deployCurrentVersion) {
        deploy(job, application, Optional.of(applicationPackage), deployCurrentVersion);
    }

    public void deploy(JobType job, Application application, Optional<ApplicationPackage> applicationPackage,
                       boolean deployCurrentVersion) {
        job.zone(controller().system()).ifPresent(zone -> tester.deploy(application, zone, applicationPackage,
                                                                        deployCurrentVersion));
    }

    public void deployAndNotify(Application application, String upgradePolicy, boolean success, JobType job) {
        deployAndNotify(application, applicationPackage(upgradePolicy), success, job);
    }

    public void deployAndNotify(Application application, ApplicationPackage applicationPackage, boolean success, JobType job) {
        deployAndNotify(application, Optional.of(applicationPackage), success, job);
    }

    public void deployAndNotify(Application application, Optional<ApplicationPackage> applicationPackage, boolean success, JobType job) {
        if (success) {
            // Staging deploys twice, once with current version and once with new version
            if (job == JobType.stagingTest) {
                deploy(job, application, applicationPackage, true);
            }
            deploy(job, application, applicationPackage, false);
        }
        // Deactivate test deployments after deploy. This replicates the behaviour of the tenant pipeline
        if (job.isTest()) {
            controller().applications().deactivate(application, job.zone(controller().system()).get());
        }
        jobCompletion(job).application(application).success(success).submit();
    }

    private void notifyJobCompletion(DeploymentJobs.JobReport report) {
        if (report.jobType() != JobType.component && ! buildService().removeJob(report.projectId(), report.jobType().jobName()))
            throw new IllegalArgumentException(report.jobType() + " is not running for " + report.applicationId());
        assertFalse("Unexpected entry '" + report.jobType() + "@" + report.projectId() + " in: " + buildService().jobs(),
                    buildService().removeJob(report.projectId(), report.jobType().jobName()));

        clock().advance(Duration.ofMillis(1));
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

    public void assertRunning(ApplicationId id, JobType jobType) {
        assertRunning(application(id).deploymentJobs().projectId().getAsLong(), jobType);
    }

    public void assertRunning(long projectId, JobType jobType) {
        assertTrue(buildService().jobs().contains(new BuildService.BuildJob(projectId, jobType.jobName())));
    }

}
