// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.TenantName;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockBuildService;
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
import java.util.Arrays;
import java.util.function.Supplier;

import static com.yahoo.config.provision.SystemName.main;
import static com.yahoo.vespa.hosted.controller.ControllerTester.buildJob;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.component;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.productionEuWest1;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.productionUsCentral1;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.productionUsEast3;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.productionUsWest1;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.systemTest;
import static java.util.Collections.singletonList;
import static java.util.Optional.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 * @author mpolden
 * @author jvenstad
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
        tester.upgradeSystem(version);

        // Deploy completely once
        tester.jobCompletion(component).application(app).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.systemTest);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.stagingTest);
        tester.deployAndNotify(app, applicationPackage, true, JobType.productionUsWest1);

        // New version is released
        version = new Version(5, 2);
        tester.upgradeSystem(version);

        // staging-test times out and is retried
        tester.buildService().remove(buildJob(app, stagingTest));
        tester.readyJobTrigger().maintain();
        assertEquals("Retried dead job", 2, tester.buildService().jobs().size());
        tester.assertRunning(app.id(), stagingTest);
        tester.deployAndNotify(app, applicationPackage, true, stagingTest);

        // system-test is now the only running job -- production jobs haven't started yet, since it is unfinished.
        tester.assertRunning(app.id(), systemTest);
        assertEquals(1, tester.buildService().jobs().size());

        // system-test fails and is retried
        tester.deployAndNotify(app, applicationPackage, false, JobType.systemTest);
        assertEquals("Job is retried on failure", 1, tester.buildService().jobs().size());
        tester.deployAndNotify(app, applicationPackage, true, JobType.systemTest);

        tester.assertRunning(app.id(), productionUsWest1);
    }

    @Test
    public void deploymentSpecDecidesTriggerOrder() {
        DeploymentTester tester = new DeploymentTester();
        TenantName tenant = tester.controllerTester().createTenant("tenant1", "domain1", 1L);
        MockBuildService mockBuildService = tester.buildService();
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
        assertTrue("All jobs consumed", mockBuildService.jobs().isEmpty());
    }

    @Test
    public void deploymentsSpecWithDelays() {
        DeploymentTester tester = new DeploymentTester();
        MockBuildService mockBuildService = tester.buildService();
        Application application = tester.createApplication("app1", "tenant1", 1, 1L);

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .delay(Duration.ofSeconds(30))
                .region("us-west-1")
                .delay(Duration.ofMinutes(2))
                .delay(Duration.ofMinutes(2)) // Multiple delays are summed up
                .region("us-central-1")
                .delay(Duration.ofMinutes(10)) // Delays after last region are valid, but have no effect
                .build();

        // Component job finishes
        tester.jobCompletion(component).application(application).uploadArtifact(applicationPackage).submit();

        // Test jobs pass
        tester.deployAndNotify(application, applicationPackage, true, JobType.systemTest);
        tester.deployAndNotify(application, applicationPackage, true, JobType.stagingTest);
        tester.deploymentTrigger().triggerReadyJobs();

        // No jobs have started yet, as 30 seconds have not yet passed.
        assertEquals(0, mockBuildService.jobs().size());
        tester.clock().advance(Duration.ofSeconds(30));
        tester.deploymentTrigger().triggerReadyJobs();

        // 30 seconds later, the first jobs may trigger.
        assertEquals(1, mockBuildService.jobs().size());
        tester.assertRunning(application.id(), productionUsWest1);

        // 3 minutes pass, delayed trigger does nothing as us-west-1 is still in progress
        tester.clock().advance(Duration.ofMinutes(3));
        tester.deploymentTrigger().triggerReadyJobs();
        assertEquals(1, mockBuildService.jobs().size());
        tester.assertRunning(application.id(), productionUsWest1);

        // us-west-1 completes
        tester.deployAndNotify(application, applicationPackage, true, productionUsWest1);

        // Delayed trigger does nothing as not enough time has passed after us-west-1 completion
        tester.deploymentTrigger().triggerReadyJobs();
        assertTrue("No more jobs triggered at this time", mockBuildService.jobs().isEmpty());

        // 3 minutes pass, us-central-1 is still not triggered
        tester.clock().advance(Duration.ofMinutes(3));
        tester.deploymentTrigger().triggerReadyJobs();
        assertTrue("No more jobs triggered at this time", mockBuildService.jobs().isEmpty());

        // 4 minutes pass, us-central-1 is triggered
        tester.clock().advance(Duration.ofMinutes(1));
        tester.deploymentTrigger().triggerReadyJobs();
        tester.deployAndNotify(application, applicationPackage, true, JobType.productionUsCentral1);
        assertTrue("All jobs consumed", mockBuildService.jobs().isEmpty());

        // Delayed trigger job runs again, with nothing to trigger
        tester.clock().advance(Duration.ofMinutes(10));
        tester.deploymentTrigger().triggerReadyJobs();
        assertTrue("All jobs consumed", mockBuildService.jobs().isEmpty());
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
        assertEquals(1, tester.buildService().jobs().size());
        tester.deployAndNotify(application, applicationPackage, true, JobType.productionUsCentral1);

        // Deploys in two regions in parallel
        assertEquals(2, tester.buildService().jobs().size());
        tester.assertRunning(application.id(), productionUsEast3);
        tester.assertRunning(application.id(), productionUsWest1);

        tester.deploy(JobType.productionUsWest1, application, applicationPackage, false);
        tester.jobCompletion(JobType.productionUsWest1).application(application).submit();
        assertEquals("One job still running.", JobType.productionUsEast3.jobName(), tester.buildService().jobs().get(0).jobName());

        tester.deploy(JobType.productionUsEast3, application, applicationPackage, false);
        tester.jobCompletion(JobType.productionUsEast3).application(application).submit();

        // Last region completes
        assertEquals(1, tester.buildService().jobs().size());
        tester.deployAndNotify(application, applicationPackage, true, JobType.productionEuWest1);
        assertTrue("All jobs consumed", tester.buildService().jobs().isEmpty());
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
        TenantName tenant = tester.controllerTester().createTenant("tenant1", "domain1", 1L);
        MockBuildService mockBuildService = tester.buildService();
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
        assertTrue("All jobs consumed", mockBuildService.jobs().isEmpty());
    }

    @Test
    public void testBlockRevisionChange() {
        ManualClock clock = new ManualClock(Instant.parse("2017-09-26T17:30:00.00Z")); // Tuesday, 17:30
        DeploymentTester tester = new DeploymentTester(new ControllerTester(clock));
        ReadyJobsTrigger readyJobsTrigger = new ReadyJobsTrigger(tester.controller(),
                                                                 Duration.ofHours(1),
                                                                 new JobControl(tester.controllerTester().curator()));

        Version version = Version.fromString("5.0");
        tester.upgradeSystem(version);

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
        assertEquals(0, tester.buildService().jobs().size());
        
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
        assertEquals(0, tester.buildService().jobs().size());

        tester.clock().advance(Duration.ofHours(2)); // ---------------- Exit block window: 20:30
        tester.deploymentTrigger().triggerReadyJobs(); // Schedules staging test for the blocked production job(s)
        // TODO jvenstad: Required now because changes during block window used empty source -- improvement to use first untested production job with change to test :)
        tester.deployAndNotify(app, changedApplication, true, stagingTest);
        assertEquals(singletonList(buildJob(app, productionUsWest1)), tester.buildService().jobs());
    }

    @Test
    public void testCompletionOfPartOfChangeDuringBlockWindow() {
        ManualClock clock = new ManualClock(Instant.parse("2017-09-26T17:30:00.00Z")); // Tuesday, 17:30
        DeploymentTester tester = new DeploymentTester(new ControllerTester(clock));
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .blockChange(false, true, "tue", "18", "UTC")
                .region("us-west-1")
                .region("us-east-3")
                .build();
        Application application = tester.createAndDeploy("app1", 1, applicationPackage);

        // Application on (6.1, 1.0.42)
        Version v1 = Version.fromString("6.1");

        // Application is mid-upgrade when block window begins, and has an outstanding change.
        Version v2 = Version.fromString("6.2");
        tester.upgradeSystem(v2);
        tester.jobCompletion(component).application(application).nextBuildNumber().uploadArtifact(applicationPackage).submit();

        tester.deployAndNotify(application, applicationPackage, true, stagingTest);
        tester.deployAndNotify(application, applicationPackage, true, systemTest);

        // Entering block window will keep the outstanding change in place, but a new component run triggers a new change.
        // This component completion should remove the older outstanding change, to avoid a later downgrade.
        clock.advance(Duration.ofHours(1));
        tester.deployAndNotify(application, applicationPackage, true, productionUsWest1);
        assertEquals((Long) BuildJob.defaultBuildNumber, tester.application(application.id()).deploymentJobs().jobStatus()
                                                               .get(productionUsWest1).lastSuccess().get().application().buildNumber().get());
        assertEquals((Long) (BuildJob.defaultBuildNumber + 1), tester.application(application.id()).outstandingChange().application().get().buildNumber().get());

        tester.readyJobTrigger().maintain();
        assertTrue(tester.buildService().jobs().isEmpty());

        // New component triggers a full deployment of new application version, leaving platform versions alone.
        tester.jobCompletion(component).application(application).nextBuildNumber().nextBuildNumber().uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(application, applicationPackage, true, stagingTest);
        tester.deployAndNotify(application, applicationPackage, true, systemTest);
        tester.deployAndNotify(application, applicationPackage, true, productionUsWest1);
        tester.deployAndNotify(application, applicationPackage, true, systemTest);
        tester.deployAndNotify(application, applicationPackage, true, stagingTest);
        tester.deployAndNotify(application, applicationPackage, true, productionUsEast3);
        tester.deployAndNotify(application, applicationPackage, true, systemTest);
        tester.deployAndNotify(application, applicationPackage, true, stagingTest);

        // All tests are done for now, and only the platform change remains.
        assertTrue(tester.buildService().jobs().isEmpty());
        assertEquals(Change.of(v2), tester.application(application.id()).change());

        // Exiting block window, staging test is re-run for the last prod zone, which has the old platform.
        clock.advance(Duration.ofHours(1));
        tester.readyJobTrigger().maintain();
        tester.deployAndNotify(application, applicationPackage, true, stagingTest);
        tester.deployAndNotify(application, applicationPackage, true, productionUsEast3);

        assertFalse(tester.application(application.id()).change().isPresent());
        assertFalse(tester.application(application.id()).outstandingChange().isPresent());
    }

    @Test
    public void testUpgradingButNoJobStarted() {
        DeploymentTester tester = new DeploymentTester();
        ReadyJobsTrigger readyJobsTrigger = new ReadyJobsTrigger(tester.controller(),
                                                                 Duration.ofHours(1),
                                                                 new JobControl(tester.controllerTester().curator()));
        Application app = tester.createAndDeploy("default0", 3, "default");
        // Store that we are upgrading but don't start the system-tests job
        tester.controller().applications().lockOrThrow(app.id(), locked -> {
            tester.controller().applications().store(locked.withChange(Change.of(Version.fromString("6.2"))));
        });
        assertEquals(0, tester.buildService().jobs().size());
        readyJobsTrigger.run();
        tester.assertRunning(app.id(), systemTest);
        tester.assertRunning(app.id(), stagingTest);
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
        tester.deployAndNotify(application, empty(), false, productionUsCentral1);
        tester.deploy(productionUsCentral1, application, empty(), false);

        ApplicationVersion appVersion1 = ApplicationVersion.from(BuildJob.defaultSourceRevision, BuildJob.defaultBuildNumber + 1);
        assertEquals(appVersion1, app.get().deployments().get(ZoneId.from("prod.us-central-1")).applicationVersion());

        // Verify the application change is not removed when change is cancelled.
        tester.deploymentTrigger().cancelChange(application.id(), true);
        assertEquals(Change.of(appVersion1), app.get().change());

        // Now cancel the change as is done through the web API.
        tester.deploymentTrigger().cancelChange(application.id(), false);
        assertEquals(Change.empty(), app.get().change());

        // A new version is released, which should now deploy the currently deployed application version to avoid downgrades.
        Version version1 = new Version("6.2");
        tester.upgradeSystem(version1);
        tester.jobCompletion(productionUsCentral1).application(application).unsuccessful().submit();
        tester.deployAndNotify(application, empty(), true, systemTest);
        tester.deployAndNotify(application, empty(), true, stagingTest);
        tester.deployAndNotify(application, empty(), false, productionUsCentral1);

        // The last job has a different target, and the tests need to run again.
        // These may now start, since the first job has been triggered once, and thus is verified already.
        tester.deployAndNotify(application, empty(), true, systemTest);
        tester.deployAndNotify(application, empty(), true, stagingTest);

        // Finally, the two production jobs complete, in order.
        tester.deployAndNotify(application, empty(), true, productionUsCentral1);
        tester.deployAndNotify(application, empty(), true, productionEuWest1);
        assertEquals(appVersion1, app.get().deployments().get(ZoneId.from("prod.us-central-1")).applicationVersion());
    }

    @Test
    public void stepIsCompletePreciselyWhenItShouldBe() {
        DeploymentTester tester = new DeploymentTester();
        Application application = tester.createApplication("app1", "tenant1", 1, 1L);
        Supplier<Application> app = () -> tester.application(application.id());
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-central-1")
                .region("eu-west-1")
                .upgradePolicy("canary")
                .build();
        tester.deployCompletely(application, applicationPackage);

        Version v2 = new Version("7.2");
        tester.upgradeSystem(v2);
        tester.completeUpgradeWithError(application, v2, applicationPackage, productionUsCentral1);
        tester.deploy(productionUsCentral1, application, applicationPackage);
        tester.deployAndNotify(application, applicationPackage, false, productionUsCentral1);
        assertEquals(v2, app.get().deployments().get(productionUsCentral1.zone(main).get()).version());
        tester.deploymentTrigger().cancelChange(application.id(), false);
        tester.deployAndNotify(application, applicationPackage, false, productionUsCentral1);
        Instant triggered = app.get().deploymentJobs().jobStatus().get(productionUsCentral1).lastTriggered().get().at();

        tester.clock().advance(Duration.ofHours(1));

        Version v1 = new Version("7.1");
        tester.upgradeSystem(v1); // Downgrade, but it works, since the app is a canary.
        assertEquals(Change.of(v1), app.get().change());

        // 7.1 proceeds 'til the last job, where it fails; us-central-1 is skipped, as current change is strictly dominated by what's deployed there.
        tester.deployAndNotify(application, applicationPackage, true, systemTest);
        tester.deployAndNotify(application, applicationPackage, true, stagingTest);
        assertEquals(triggered, app.get().deploymentJobs().jobStatus().get(productionUsCentral1).lastTriggered().get().at());
        tester.deployAndNotify(application, applicationPackage, false, productionEuWest1);

        // Roll out a new application version, which gives a dual change -- this should trigger us-central-1, but only as long as it hasn't yet deployed there.
        tester.jobCompletion(component).application(application).nextBuildNumber().uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(application, applicationPackage, false, productionEuWest1);
        tester.deployAndNotify(application, applicationPackage, true, systemTest);
        tester.deployAndNotify(application, applicationPackage, true, stagingTest);

        assertEquals(v2, app.get().deployments().get(productionUsCentral1.zone(main).get()).version());
        assertEquals((Long) 42L, app.get().deployments().get(productionUsCentral1.zone(main).get()).applicationVersion().buildNumber().get());
        assertNotEquals(triggered, app.get().deploymentJobs().jobStatus().get(productionUsCentral1).lastTriggered().get().at());

        // Change has a higher application version than what is deployed -- deployment should trigger.
        tester.deployAndNotify(application, applicationPackage, false, productionUsCentral1);
        tester.deploy(productionUsCentral1, application, applicationPackage);
        assertEquals(v2, app.get().deployments().get(productionUsCentral1.zone(main).get()).version());
        assertEquals((Long) 43L, app.get().deployments().get(productionUsCentral1.zone(main).get()).applicationVersion().buildNumber().get());

        // Change is again strictly dominated, and us-central-1 is skipped, even though it is still failing.
        tester.deployAndNotify(application, applicationPackage, false, productionUsCentral1);

        // Last job has a different deployment target, so tests need to run again.
        tester.deployAndNotify(application, empty(), true, systemTest);
        tester.deployAndNotify(application, empty(), true, stagingTest);
        tester.deployAndNotify(application, applicationPackage, true, productionEuWest1);
        assertFalse(app.get().change().isPresent());
        assertFalse(app.get().deploymentJobs().jobStatus().get(productionUsCentral1).isSuccess());
    }

    @Test
    public void eachDeployTargetIsTested() {
        DeploymentTester tester = new DeploymentTester();
        Application application = tester.createApplication("app1", "tenant1", 1, 1L);
        Supplier<Application> app = () -> tester.application(application.id());
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .parallel("eu-west-1", "us-east-3")
                .build();
        // Application version 42 and platform version 6.1.
        tester.deployCompletely(application, applicationPackage);

        // Success in first prod zone, change cancelled between triggering and deployment to two parallel zones.
        // One of the parallel zones get a deployment, but both fail their jobs.
        Version v1 = new Version("6.1");
        Version v2 = new Version("6.2");
        tester.upgradeSystem(v2);
        tester.deployAndNotify(application, empty(), true, systemTest);
        tester.deployAndNotify(application, empty(), true, stagingTest);
        tester.deploymentTrigger().cancelChange(application.id(), true);
        tester.deploy(productionEuWest1, application, applicationPackage);
        assertEquals(v2, app.get().deployments().get(productionEuWest1.zone(main).get()).version());
        assertEquals(v1, app.get().deployments().get(productionUsEast3.zone(main).get()).version());

        // New application version should run system and staging tests against both 6.1 and 6.2, in no particular order.
        tester.jobCompletion(component).application(application).nextBuildNumber().uploadArtifact(applicationPackage).submit();
        Version firstTested = app.get().deploymentJobs().jobStatus().get(systemTest).lastTriggered().get().platform();
        assertEquals(firstTested, app.get().deploymentJobs().jobStatus().get(stagingTest).lastTriggered().get().platform());

        tester.deployAndNotify(application, empty(), true, systemTest);
        tester.deployAndNotify(application, empty(), true, stagingTest);

        // Tests are not re-triggered, because the jobs they were run for has not yet been triggered with the tested versions.
        assertEquals(firstTested, app.get().deploymentJobs().jobStatus().get(systemTest).lastTriggered().get().platform());
        assertEquals(firstTested, app.get().deploymentJobs().jobStatus().get(stagingTest).lastTriggered().get().platform());

         // Finish old runs of the production jobs, which fail.
        tester.deployAndNotify(application, applicationPackage, false, productionEuWest1);
        tester.deployAndNotify(application, applicationPackage, false, productionUsEast3);

        // New upgrade is already tested for one of the jobs, which has now been triggered, and tests may run for the other job.
        assertNotEquals(firstTested, app.get().deploymentJobs().jobStatus().get(systemTest).lastTriggered().get().platform());
        assertNotEquals(firstTested, app.get().deploymentJobs().jobStatus().get(stagingTest).lastTriggered().get().platform());
        tester.deployAndNotify(application, empty(), true, systemTest);
        tester.deployAndNotify(application, empty(), true, stagingTest);

        // Both jobs fail again, and must be re-triggered -- this is ok, as they are both already triggered on their current targets.
        tester.deployAndNotify(application, empty(), false, productionEuWest1);
        tester.deployAndNotify(application, empty(), false, productionUsEast3);
        tester.deployAndNotify(application, empty(), true, productionUsEast3);
        tester.deployAndNotify(application, empty(), true, productionEuWest1);
        assertFalse(app.get().change().isPresent());
        assertEquals(43, app.get().deploymentJobs().jobStatus().get(productionEuWest1).lastSuccess().get().application().buildNumber().get().longValue());
        assertEquals(43, app.get().deploymentJobs().jobStatus().get(productionUsEast3).lastSuccess().get().application().buildNumber().get().longValue());
    }

    @Test
    public void eachDifferentUpgradeCombinationIsTested() {
        DeploymentTester tester = new DeploymentTester();
        Application application = tester.createApplication("app1", "tenant1", 1, 1L);
        Supplier<Application> app = () -> tester.application(application.id());
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-central-1")
                .parallel("eu-west-1", "us-east-3")
                .build();
        // Application version 42 and platform version 6.1.
        tester.deployCompletely(application, applicationPackage);

        // Application partially upgrades, then a new version is released.
        Version v1 = new Version("6.1");
        Version v2 = new Version("6.2");
        tester.upgradeSystem(v2);
        tester.deployAndNotify(application, empty(), true, systemTest);
        tester.deployAndNotify(application, empty(), true, stagingTest);
        tester.deployAndNotify(application, empty(), true, productionUsCentral1);
        tester.deployAndNotify(application, empty(), true, productionEuWest1);
        tester.deployAndNotify(application, empty(), false, productionUsEast3);
        assertEquals(v2, app.get().deployments().get(ZoneId.from("prod", "us-central-1")).version());
        assertEquals(v2, app.get().deployments().get(ZoneId.from("prod", "eu-west-1")).version());
        assertEquals(v1, app.get().deployments().get(ZoneId.from("prod", "us-east-3")).version());

        Version v3 = new Version("6.3");
        tester.upgradeSystem(v3);
        tester.deployAndNotify(application, empty(), false, productionUsEast3);

        // See that sources for staging are: first v2, then v1.
        tester.deployAndNotify(application, empty(), true, systemTest);
        tester.deployAndNotify(application, empty(), true, stagingTest);
        assertEquals(v2, app.get().deploymentJobs().jobStatus().get(stagingTest).lastSuccess().get().sourcePlatform().get());
        tester.deployAndNotify(application, empty(), true, productionUsCentral1);
        assertEquals(v1, app.get().deploymentJobs().jobStatus().get(stagingTest).lastTriggered().get().sourcePlatform().get());
        tester.deployAndNotify(application, empty(), true, stagingTest);
        tester.deployAndNotify(application, empty(), true, productionEuWest1);
        tester.deployAndNotify(application, empty(), true, productionUsEast3);
    }

}
