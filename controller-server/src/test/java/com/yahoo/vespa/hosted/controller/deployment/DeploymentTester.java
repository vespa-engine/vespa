// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.ArtifactRepositoryMock;
import com.yahoo.vespa.hosted.controller.ConfigServerClientMock;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.application.SourceRevision;
import com.yahoo.vespa.hosted.controller.maintenance.JobControl;
import com.yahoo.vespa.hosted.controller.maintenance.ReadyJobsTrigger;
import com.yahoo.vespa.hosted.controller.maintenance.Upgrader;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobError.unknown;
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
    private static final int defaultBuildNumber = 42;

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

    // TODO: This thing simulates the wrong thing: the build system won't hold the jobs that are running,
    // and so these should be consumed immediately upon triggering, and be "somewhere else" while running.
    public BuildSystem buildSystem() { return tester.controller().applications().deploymentTrigger().buildSystem(); }

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

    public Optional<Change.VersionChange> versionChange(ApplicationId application) {
        Change change = application(application).deploying();
        if (change instanceof Change.VersionChange) return Optional.of((Change.VersionChange)change);
        return Optional.empty();
    }

    public void updateVersionStatus() {
        controller().updateVersionStatus(VersionStatus.compute(controller(), tester.controller().systemVersion()));
    }

    public void updateVersionStatus(Version currentVersion) {
        configServer().setDefaultVersion(currentVersion);
        controller().updateVersionStatus(VersionStatus.compute(controller(), currentVersion));
    }

    public void upgradeSystem(Version version) {
        controllerTester().configServer().setDefaultVersion(version);
        updateVersionStatus(version);
        upgrader().maintain();
    }

    public Application createApplication(String applicationName, String tenantName, long projectId, Long propertyId) {
        TenantId tenant = tester.createTenant(tenantName, UUID.randomUUID().toString(), propertyId);
        return tester.createApplication(tenant, applicationName, "default", projectId);
    }

    public void restartController() { tester.createNewController(); }

    /** Simulate the full lifecycle of an application deployment as declared in given application package */
    public Application createAndDeploy(String applicationName, int projectId, ApplicationPackage applicationPackage) {
        TenantId tenantId = tester.createTenant("tenant1", "domain1", 1L);
        return createAndDeploy(tenantId, applicationName, projectId, applicationPackage);
    }

    /** Simulate the full lifecycle of an application deployment as declared in given application package */
    public Application createAndDeploy(TenantId tenantId, String applicationName, int projectId, ApplicationPackage applicationPackage) {
        Application application = tester.createApplication(tenantId, applicationName, "default", projectId);
        deployCompletely(application, applicationPackage);
        return applications().require(application.id());
    }

    /** Simulate the full lifecycle of an application deployment to prod.us-west-1 with the given upgrade policy */
    public Application createAndDeploy(String applicationName, int projectId, String upgradePolicy) {
        return createAndDeploy(applicationName, projectId, applicationPackage(upgradePolicy));
    }

    /** Simulate the full lifecycle of an application deployment to prod.us-west-1 with the given upgrade policy */
    public Application createAndDeploy(TenantId tenantId, String applicationName, int projectId, String upgradePolicy) {
        return createAndDeploy(tenantId, applicationName, projectId, applicationPackage(upgradePolicy));
    }

    /** Complete an ongoing deployment */
    public void deployCompletely(String applicationName) {
        deployCompletely(applications().require(ApplicationId.from("tenant1", applicationName, "default")),
                         applicationPackage("default"));
    }

    /** Deploy application completely using the given application package */
    public void deployCompletely(Application application, ApplicationPackage applicationPackage) {
        notifyJobCompletion(JobType.component, application, true);
        assertTrue(applications().require(application.id()).deploying().isPresent());
        completeDeployment(application, applicationPackage, Optional.empty(), true);
    }

    public static DeploymentJobs.JobReport jobReport(Application application, JobType jobType, boolean success) {
        return jobReport(application, jobType, Optional.ofNullable(success ? null : unknown), Optional.empty(), defaultBuildNumber);
    }

    public static DeploymentJobs.JobReport jobReport(Application application, JobType jobType,
                                                     Optional<DeploymentJobs.JobError> jobError,
                                                     Optional<SourceRevision> sourceRevision, long buildNumber) {
        return new DeploymentJobs.JobReport(
                application.id(),
                jobType,
                application.deploymentJobs().projectId().get(),
                buildNumber,
                sourceRevision,
                jobError
        );
    }

    /** Deploy application using the given application package, but expecting to stop after test phases */
    public void deployTestOnly(Application application, ApplicationPackage applicationPackage) {
        notifyJobCompletion(JobType.component, application, true);
        assertTrue(applications().require(application.id()).deploying().isPresent());
        completeDeployment(application, applicationPackage, Optional.empty(), false);
    }

    private void completeDeployment(Application application, ApplicationPackage applicationPackage,
                                    Optional<JobType> failOnJob, boolean includingProductionZones) {
        DeploymentOrder order = new DeploymentOrder(controller());
        List<JobType> jobs = order.jobsFrom(applicationPackage.deploymentSpec());
        if ( ! includingProductionZones)
            jobs = jobs.stream().filter(job -> ! job.isProduction()).collect(Collectors.toList());
        for (JobType job : jobs) {
            boolean failJob = failOnJob.map(j -> j.equals(job)).orElse(false);
            deployAndNotify(application, applicationPackage, ! failJob, false, job);
            if (failJob) {
                break;
            }
        }
        if (failOnJob.isPresent()) {
            assertTrue(applications().require(application.id()).deploying().isPresent());
            assertTrue(applications().require(application.id()).deploymentJobs().hasFailures());
        } else if (includingProductionZones) {
            assertFalse(applications().require(application.id()).deploying().isPresent());
        }
        else {
            assertTrue(applications().require(application.id()).deploying().isPresent());
        }
    }

    public void notifyJobCompletion(JobType jobType, Application application, boolean success) {
        notifyJobCompletion(jobType, application, Optional.ofNullable(success ? null : unknown));
    }

    public void notifyJobCompletion(JobType jobType, Application application, Optional<DeploymentJobs.JobError> jobError) {
        notifyJobCompletion(jobType, application, jobError, Optional.empty(), defaultBuildNumber);
    }

    public void notifyJobCompletion(JobType jobType, Application application, Optional<DeploymentJobs.JobError> jobError,
                                    Optional<SourceRevision> source, long buildNumber) {
        clock().advance(Duration.ofMillis(1));
        applications().notifyJobCompletion(jobReport(application, jobType, jobError, source, buildNumber));
    }

    public void completeUpgrade(Application application, Version version, String upgradePolicy) {
        completeUpgrade(application, version, applicationPackage(upgradePolicy));
    }

    public void completeUpgrade(Application application, Version version, ApplicationPackage applicationPackage) {
        assertTrue(application + " has a deployment", applications().require(application.id()).deploying().isPresent());
        assertEquals(new Change.VersionChange(version), applications().require(application.id()).deploying());
        completeDeployment(application, applicationPackage, Optional.empty(), true);
    }

    public void completeUpgradeWithError(Application application, Version version, String upgradePolicy, JobType failOnJob) {
        completeUpgradeWithError(application, version, applicationPackage(upgradePolicy), Optional.of(failOnJob));
    }

    public void completeUpgradeWithError(Application application, Version version, ApplicationPackage applicationPackage, JobType failOnJob) {
        completeUpgradeWithError(application, version, applicationPackage, Optional.of(failOnJob));
    }

    private void completeUpgradeWithError(Application application, Version version, ApplicationPackage applicationPackage, Optional<JobType> failOnJob) {
        assertTrue(applications().require(application.id()).deploying().isPresent());
        assertEquals(new Change.VersionChange(version), applications().require(application.id()).deploying());
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

    public void deployAndNotify(Application application, boolean success, JobType... job) {
        deployAndNotify(application, Optional.empty(), success, true, job);
    }

    public void deployAndNotify(Application application, String upgradePolicy, boolean success, JobType... jobs) {
        deployAndNotify(application, applicationPackage(upgradePolicy), success, true, jobs);
    }

    public void deployAndNotify(Application application, ApplicationPackage applicationPackage, boolean success,
                                JobType... jobs) {
        deployAndNotify(application, applicationPackage, success, true, jobs);
    }

    public void deployAndNotify(Application application, ApplicationPackage applicationPackage, boolean success,
                                boolean expectOnlyTheseJobs, JobType... jobs) {
        deployAndNotify(application, Optional.of(applicationPackage), success, expectOnlyTheseJobs, jobs);
    }

    public void deployAndNotify(Application application, Optional<ApplicationPackage> applicationPackage,
                                boolean success, boolean expectOnlyTheseJobs, JobType... jobs) {
        consumeJobs(application, expectOnlyTheseJobs, jobs);
        for (JobType job : jobs) {
            if (success) {
                deploy(job, application, applicationPackage, false);
            }
            notifyJobCompletion(job, application, success);
        }
    }

    /** Assert that the sceduled jobs of this application are exactly those given, and take them */
    private void consumeJobs(Application application, boolean expectOnlyTheseJobs, JobType... jobs) {
        for (JobType job : jobs) {
            BuildService.BuildJob buildJob = findJob(application, job);
            assertEquals((long) application.deploymentJobs().projectId().get(), buildJob.projectId());
            assertEquals(job.jobName(), buildJob.jobName());
        }
        if (expectOnlyTheseJobs)
            assertEquals(jobs.length, countJobsOf(application));
        buildSystem().removeJobs(application.id());
    }

    private BuildService.BuildJob findJob(Application application, JobType jobType) {
        for (BuildService.BuildJob job : buildSystem().jobs()) {
            if (job.projectId() == application.deploymentJobs().projectId().get() && job.jobName().equals(jobType.jobName()))
                return job;
        }
        throw new NoSuchElementException(jobType + " is not scheduled for " + application);
    }

    private int countJobsOf(Application application) {
        return (int)buildSystem().jobs().stream()
                                        .filter(job -> job.projectId() == application.deploymentJobs().projectId().get())
                                        .count();
    }

    public static ApplicationPackage applicationPackage(String upgradePolicy) {
        return new ApplicationPackageBuilder()
                .upgradePolicy(upgradePolicy)
                .environment(Environment.prod)
                .region("us-west-1")
                .environment(Environment.prod)
                .region("us-east-3")
                .build();
    }

}
