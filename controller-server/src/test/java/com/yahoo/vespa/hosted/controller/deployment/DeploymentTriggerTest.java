// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.SystemName;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.LockedApplication;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.application.SourceRevision;
import com.yahoo.vespa.hosted.controller.maintenance.JobControl;
import com.yahoo.vespa.hosted.controller.maintenance.ReadyJobsTrigger;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.component;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.productionEuWest1;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.productionUsCentral1;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.systemTest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 * @author mpolden
 */
public class DeploymentTriggerTest {

    @Test
    public void testTriggerFailing() {
        DeploymentTester tester = new DeploymentTester();
        Application app = tester.createApplication("app1", "tenant1", 1, 1L);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("default")
                .environment(Environment.prod)
                .region("us-west-1")
                .build();

        Version version = new Version(5, 1);
        tester.updateVersionStatus(version);
        tester.upgrader().maintain();

        // Deploy completely once
        tester.jobCompletion(component).application(app).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.systemTest);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.stagingTest);
        tester.deployAndNotify(app, applicationPackage, true, JobType.productionUsWest1);

        // New version is released
        version = new Version(5, 2);
        tester.updateVersionStatus(version);
        tester.upgrader().maintain();

        // system-test fails and is retried
        tester.deployAndNotify(app, applicationPackage, false, JobType.systemTest);
        assertEquals("Retried immediately", 1, tester.deploymentQueue().jobs().size());
        tester.clock().advance(Duration.ofHours(1));
        tester.deployAndNotify(app, applicationPackage, false, JobType.systemTest);
        tester.clock().advance(Duration.ofHours(1));
        assertEquals("Nothing scheduled", 0, tester.deploymentQueue().jobs().size());
        tester.readyJobTrigger().maintain(); // Causes retry of systemTests

        assertEquals("Scheduled retry", 1, tester.deploymentQueue().jobs().size());
        tester.deployAndNotify(app, applicationPackage, true, JobType.systemTest);

        // staging-test times out and is retried
        tester.deploymentQueue().takeJobsToRun();
        tester.clock().advance(Duration.ofHours(12).plus(Duration.ofSeconds(1)));
        tester.readyJobTrigger().maintain();
        assertEquals("Retried dead job", 1, tester.deploymentQueue().jobs().size());
        assertEquals(JobType.stagingTest.jobName(), tester.deploymentQueue().jobs().get(0).jobName());
    }

    @Test
    public void deploymentSpecDecidesTriggerOrder() {
        DeploymentTester tester = new DeploymentTester();
        DeploymentQueue deploymentQueue = tester.deploymentQueue();
        TenantId tenant = tester.controllerTester().createTenant("tenant1", "domain1", 1L);
        Application application = tester.controllerTester().createApplication(tenant, "app1", "default", 1L);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("corp-us-east-1")
                .region("us-central-1")
                .region("us-west-1")
                .build();

        // Component job finishes
        tester.jobCompletion(component).application(application).uploadArtifact(applicationPackage).submit();

        // Application is deployed to all test environments and declared zones
        tester.deployAndNotify(application, applicationPackage, true, JobType.systemTest);
        tester.deployAndNotify(application, applicationPackage, true, JobType.stagingTest);
        tester.deployAndNotify(application, applicationPackage, true, JobType.productionCorpUsEast1);
        tester.deployAndNotify(application, applicationPackage, true, JobType.productionUsCentral1);
        tester.deployAndNotify(application, applicationPackage, true, JobType.productionUsWest1);
        assertTrue("All jobs consumed", deploymentQueue.jobs().isEmpty());
    }

    @Test
    public void deploymentsSpecWithDelays() {
        DeploymentTester tester = new DeploymentTester();
        DeploymentQueue deploymentQueue = tester.deploymentQueue();
        Application application = tester.createApplication("app1", "tenant1", 1, 1L);

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .delay(Duration.ofSeconds(30))
                .region("us-west-1")
                .delay(Duration.ofMinutes(1))
                .delay(Duration.ofMinutes(2)) // Multiple delays are summed up
                .region("us-central-1")
                .delay(Duration.ofMinutes(10)) // Delays after last region are valid, but have no effect
                .build();

        // Component job finishes
        tester.jobCompletion(component).application(application).uploadArtifact(applicationPackage).submit();

        // Test jobs pass
        tester.deployAndNotify(application, applicationPackage, true, JobType.systemTest);
        tester.clock().advance(Duration.ofSeconds(1)); // Make staging test sort as the last successful job
        tester.deployAndNotify(application, applicationPackage, true, JobType.stagingTest);
        assertTrue("No more jobs triggered at this time", deploymentQueue.jobs().isEmpty());

        // 30 seconds pass, us-west-1 is triggered
        tester.clock().advance(Duration.ofSeconds(30));
        tester.deploymentTrigger().triggerReadyJobs();

        // Consume us-west-1 job without reporting completion
        assertEquals(1, deploymentQueue.jobs().size());
        assertEquals(JobType.productionUsWest1.jobName(), deploymentQueue.jobs().get(0).jobName());
        deploymentQueue.takeJobsToRun();

        // 3 minutes pass, delayed trigger does nothing as us-west-1 is still in progress
        tester.clock().advance(Duration.ofMinutes(3));
        tester.deploymentTrigger().triggerReadyJobs();
        assertTrue("No more jobs triggered at this time", deploymentQueue.jobs().isEmpty());

        // us-west-1 completes
        tester.deploy(JobType.productionUsWest1, application, applicationPackage);
        tester.jobCompletion(JobType.productionUsWest1).application(application).submit();

        // Delayed trigger does nothing as not enough time has passed after us-west-1 completion
        tester.deploymentTrigger().triggerReadyJobs();
        assertTrue("No more jobs triggered at this time", deploymentQueue.jobs().isEmpty());

        // 3 minutes pass, us-central-1 is triggered
        tester.clock().advance(Duration.ofMinutes(3));
        tester.deploymentTrigger().triggerReadyJobs();
        tester.deployAndNotify(application, applicationPackage, true, JobType.productionUsCentral1);
        assertTrue("All jobs consumed", deploymentQueue.jobs().isEmpty());

        // Delayed trigger job runs again, with nothing to trigger
        tester.clock().advance(Duration.ofMinutes(10));
        tester.deploymentTrigger().triggerReadyJobs();
        assertTrue("All jobs consumed", deploymentQueue.jobs().isEmpty());
    }

    @Test
    public void deploymentSpecWithParallelDeployments() {
        DeploymentTester tester = new DeploymentTester();
        Application application = tester.createApplication("app1", "tenant1", 1, 1L);

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-central-1")
                .parallel("us-west-1", "us-east-3")
                .region("eu-west-1")
                .build();

        // Component job finishes
        tester.jobCompletion(component).application(application).uploadArtifact(applicationPackage).submit();

        // Test jobs pass
        tester.deployAndNotify(application, applicationPackage, true, JobType.systemTest);
        tester.deployAndNotify(application, applicationPackage, true, JobType.stagingTest);

        // Deploys in first region
        assertEquals(1, tester.deploymentQueue().jobs().size());
        tester.deployAndNotify(application, applicationPackage, true, JobType.productionUsCentral1);

        // Deploys in two regions in parallel
        assertEquals(2, tester.deploymentQueue().jobs().size());
        assertEquals(JobType.productionUsEast3.jobName(), tester.deploymentQueue().jobs().get(0).jobName());
        assertEquals(JobType.productionUsWest1.jobName(), tester.deploymentQueue().jobs().get(1).jobName());
        tester.deploymentQueue().takeJobsToRun();

        tester.deploy(JobType.productionUsWest1, application, applicationPackage, false);
        tester.jobCompletion(JobType.productionUsWest1).application(application).submit();
        assertTrue("No more jobs triggered at this time", tester.deploymentQueue().jobs().isEmpty());

        tester.deploy(JobType.productionUsEast3, application, applicationPackage, false);
        tester.jobCompletion(JobType.productionUsEast3).application(application).submit();

        // Last region completes
        assertEquals(1, tester.deploymentQueue().jobs().size());
        tester.deployAndNotify(application, applicationPackage, true, JobType.productionEuWest1);
        assertTrue("All jobs consumed", tester.deploymentQueue().jobs().isEmpty());
    }

    @Test
    public void parallelDeploymentCompletesOutOfOrder() {
        DeploymentTester tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .parallel("us-east-3", "us-west-1")
                .build();

        Application app = tester.createApplication("app1", "tenant1", 1, 11L);
        tester.jobCompletion(component).application(app).uploadArtifact(applicationPackage).submit();

        // Test environments pass
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.systemTest);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.stagingTest);

        // Last declared job completes first
        tester.deploy(DeploymentJobs.JobType.productionUsWest1, app, applicationPackage);
        tester.jobCompletion(DeploymentJobs.JobType.productionUsWest1).application(app).submit();
        assertTrue("Change is present as not all jobs are complete",
                   tester.applications().require(app.id()).change().isPresent());

        // All jobs complete
        tester.deploy(DeploymentJobs.JobType.productionUsEast3, app, applicationPackage);
        tester.jobCompletion(JobType.productionUsEast3).application(app).submit();
        assertFalse("Change has been deployed",
                    tester.applications().require(app.id()).change().isPresent());
    }

    @Test
    public void testSuccessfulDeploymentApplicationPackageChanged() {
        DeploymentTester tester = new DeploymentTester();
        DeploymentQueue deploymentQueue = tester.deploymentQueue();
        TenantId tenant = tester.controllerTester().createTenant("tenant1", "domain1", 1L);
        Application application = tester.controllerTester().createApplication(tenant, "app1", "default", 1L);
        ApplicationPackage previousApplicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("corp-us-east-1")
                .region("us-central-1")
                .region("us-west-1")
                .build();
        ApplicationPackage newApplicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("corp-us-east-1")
                .region("us-central-1")
                .region("us-west-1")
                .region("eu-west-1")
                .build();

        // Component job finishes
        tester.jobCompletion(component).application(application).uploadArtifact(newApplicationPackage).submit();

        // Application is deployed to all test environments and declared zones
        tester.deployAndNotify(application, newApplicationPackage, true, JobType.systemTest);
        tester.deploy(JobType.stagingTest, application, previousApplicationPackage, true);
        tester.deployAndNotify(application, newApplicationPackage, true, JobType.stagingTest);
        tester.deployAndNotify(application, newApplicationPackage, true, JobType.productionCorpUsEast1);
        tester.deployAndNotify(application, newApplicationPackage, true, JobType.productionUsCentral1);
        tester.deployAndNotify(application, newApplicationPackage, true, JobType.productionUsWest1);
        tester.deployAndNotify(application, newApplicationPackage, true, JobType.productionEuWest1);
        assertTrue("All jobs consumed", deploymentQueue.jobs().isEmpty());
    }

    @Test
    public void testBlockRevisionChange() {
        ManualClock clock = new ManualClock(Instant.parse("2017-09-26T17:30:00.00Z")); // Tuesday, 17:30
        DeploymentTester tester = new DeploymentTester(new ControllerTester(clock));
        ReadyJobsTrigger readyJobsTrigger = new ReadyJobsTrigger(tester.controller(),
                                                                 Duration.ofHours(1),
                                                                 new JobControl(tester.controllerTester().curator()));

        Version version = Version.fromString("5.0");
        tester.updateVersionStatus(version);

        ApplicationPackageBuilder applicationPackageBuilder = new ApplicationPackageBuilder()
                .upgradePolicy("canary")
                // Block application version changes on tuesday in hours 18 and 19
                .blockChange(true, false, "tue", "18-19", "UTC")
                .region("us-west-1")
                .region("us-central-1")
                .region("us-east-3");

        Application app = tester.createAndDeploy("app1", 1, applicationPackageBuilder.build());



        tester.clock().advance(Duration.ofHours(1)); // --------------- Enter block window: 18:30

        readyJobsTrigger.run();
        assertEquals(0, tester.deploymentQueue().jobs().size());
        
        String searchDefinition =
                "search test {\n" +
                "  document test {\n" +
                "    field test type string {\n" +
                "    }\n" +
                "  }\n" +
                "}\n";
        ApplicationPackage changedApplication = applicationPackageBuilder.searchDefinition(searchDefinition).build();
        tester.jobCompletion(component)
              .application(app)
              .nextBuildNumber()
              .sourceRevision(new SourceRevision("repository1", "master", "cafed00d"))
              .uploadArtifact(changedApplication)
              .submit();
        assertTrue(tester.applications().require(app.id()).change().isPresent());
        tester.deployAndNotify(app, changedApplication, true, systemTest);
        tester.deployAndNotify(app, changedApplication, true, stagingTest);

        readyJobsTrigger.run();
        assertEquals(0, tester.deploymentQueue().jobs().size());

        tester.clock().advance(Duration.ofHours(2)); // ---------------- Exit block window: 20:30
        tester.deploymentTrigger().triggerReadyJobs(); // Schedules the blocked production job(s)
        assertEquals(1, tester.deploymentQueue().jobs().size());
        BuildService.BuildJob productionJob = tester.deploymentQueue().takeJobsToRun().get(0);
        assertEquals("production-us-west-1", productionJob.jobName());
    }

    @Test
    public void testUpgradingButNoJobStarted() {
        DeploymentTester tester = new DeploymentTester();
        ReadyJobsTrigger readyJobsTrigger = new ReadyJobsTrigger(tester.controller(),
                                                                 Duration.ofHours(1),
                                                                 new JobControl(tester.controllerTester().curator()));
        LockedApplication app = (LockedApplication)tester.createAndDeploy("default0", 3, "default");
        // Store that we are upgrading but don't start the system-tests job
        tester.controller().applications().store(app.withChange(Change.of(Version.fromString("6.2"))));
        assertEquals(0, tester.deploymentQueue().jobs().size());
        readyJobsTrigger.run();
        assertEquals(1, tester.deploymentQueue().jobs().size());
        assertEquals("system-test", tester.deploymentQueue().jobs().get(0).jobName());
    }

    @Test
    public void testHandleMultipleNotificationsFromLastJob() {
        DeploymentTester tester = new DeploymentTester();
        DeploymentQueue deploymentQueue = tester.deploymentQueue();
        TenantId tenant = tester.controllerTester().createTenant("tenant1", "domain1", 1L);
        Application application = tester.controllerTester().createApplication(tenant, "app1", "default", 1L);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("corp-us-east-1")
                .build();

        // Component job finishes
        tester.jobCompletion(component).application(application).uploadArtifact(applicationPackage).submit();

        // Application is deployed to all test environments and declared zones
        tester.deployAndNotify(application, applicationPackage, true, JobType.systemTest);
        tester.deployAndNotify(application, applicationPackage, true, JobType.stagingTest);
        tester.deployAndNotify(application, applicationPackage, true, JobType.productionCorpUsEast1);

        // Extra notification for last job
        tester.jobCompletion(JobType.productionCorpUsEast1).application(application).submit();
        assertFalse("Change has been deployed",
                    tester.applications().require(application.id()).change().isPresent());
        assertTrue("All jobs consumed", deploymentQueue.jobs().isEmpty());
    }

    @Test
    public void dualChangesAreNotSkippedWhenOnePartIsDeployedAlready() {
        DeploymentTester tester = new DeploymentTester();
        Application application = tester.createApplication("app1", "tenant1", 1, 1L);
        Supplier<Application> app = () -> tester.application(application.id());
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-central-1")
                .region("eu-west-1")
                .build();

        tester.deployCompletely(application, applicationPackage);

        // Platform upgrade which doesn't succeed, allowing a dual change.
        Version version1 = new Version("7.1");
        tester.upgradeSystem(version1);
        tester.completeUpgradeWithError(application, version1, applicationPackage, productionEuWest1);

        // Exhaust the retry, so productionEuWest1 is no longer running.
        tester.clock().advance(Duration.ofHours(1));
        tester.deployAndNotify(application, Optional.empty(), false, true, productionEuWest1);
        assertTrue(tester.deploymentQueue().jobs().isEmpty());

        // Deploy the new application version, even though the platform version is already deployed in us-central-1.
        tester.deployCompletely(application, applicationPackage, BuildJob.defaultBuildNumber + 1);
        assertEquals(ApplicationVersion.from(BuildJob.defaultSourceRevision, BuildJob.defaultBuildNumber + 1),
                     app.get().deployments().get(ZoneId.from("prod.us-central-1")).applicationVersion());
    }

    @Test
    public void applicationVersionIsNotDowngraded() {
        DeploymentTester tester = new DeploymentTester();
        Application application = tester.createApplication("app1", "tenant1", 1, 1L);
        Supplier<Application> app = () -> tester.application(application.id());
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-central-1")
                .region("eu-west-1")
                .build();

        tester.deployCompletely(application, applicationPackage);

        // productionUsCentral1 fails after deployment, causing a mismatch between deployed and successful state.
        tester.completeDeploymentWithError(application, applicationPackage, BuildJob.defaultBuildNumber + 1, productionUsCentral1);

        // deployAndNotify doesn't actually deploy if the job fails, so we need to do that manually.
        tester.deployAndNotify(application, Optional.empty(), false, true, productionUsCentral1);
        tester.deploy(productionUsCentral1, application, Optional.empty(), false);

        // Exhaust the automatic retry.
        tester.clock().advance(Duration.ofHours(1));
        tester.deployAndNotify(application, Optional.empty(), false, true, productionUsCentral1);
        assertTrue(tester.deploymentQueue().jobs().isEmpty());

        ApplicationVersion appVersion1 = ApplicationVersion.from(BuildJob.defaultSourceRevision, BuildJob.defaultBuildNumber + 1);
        assertEquals(appVersion1, app.get().deployments().get(ZoneId.from("prod.us-central-1")).applicationVersion());

        // Verify the application change is not removed when change is cancelled.
        tester.deploymentTrigger().cancelChange(application.id());
        assertEquals(Change.of(appVersion1), app.get().change());

        // Now cancel the change -- this should not normally happen.
        tester.applications().lockOrThrow(application.id(), lockedApplication -> {
            tester.applications().store(lockedApplication.withChange(Change.empty()));
        });

        // A new version is released, which should now deploy the currently deployed application version to avoid downgrades.
        Version version1 = new Version("6.2");
        tester.upgradeSystem(version1);
        tester.completeUpgrade(application, version1, applicationPackage);
        assertEquals(appVersion1, app.get().deployments().get(ZoneId.from("prod.us-central-1")).applicationVersion());
    }

}
