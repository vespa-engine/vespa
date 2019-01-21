// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.TenantName;
import com.yahoo.slime.Slime;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.SourceRevision;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockBuildService;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.maintenance.JobControl;
import com.yahoo.vespa.hosted.controller.maintenance.ReadyJobsTrigger;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.yahoo.config.provision.SystemName.main;
import static com.yahoo.vespa.hosted.controller.ControllerTester.buildJob;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.component;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionEuWest1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionUsCentral1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionUsEast3;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionUsWest1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.systemTest;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger.ChangesToCancel.ALL;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger.ChangesToCancel.PLATFORM;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests a wide variety of deployment scenarios and configurations
 *
 * @author bratseth
 * @author mpolden
 * @author jonmv
 */
public class DeploymentTriggerTest {

    private DeploymentTester tester;

    @Before
    public void before() {
        tester = new DeploymentTester();
    }

    @Test
    public void testTriggerFailing() {
        Application app = tester.createApplication("app1", "tenant1", 1, 1L);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("default")
                .environment(Environment.prod)
                .region("us-west-1")
                .build();

        Version version = Version.fromString("6.2");
        tester.upgradeSystem(version);

        // Deploy completely once
        tester.jobCompletion(component).application(app).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app, applicationPackage, true, JobType.systemTest);
        tester.deployAndNotify(app, applicationPackage, true, JobType.stagingTest);
        tester.deployAndNotify(app, applicationPackage, true, JobType.productionUsWest1);

        // New version is released
        version = Version.fromString("6.3");
        tester.upgradeSystem(version);

        // staging-test times out and is retried
        tester.buildService().remove(buildJob(app, stagingTest));
        tester.readyJobTrigger().maintain();
        assertEquals("Retried dead job", 2, tester.buildService().jobs().size());
        tester.assertRunning(stagingTest, app.id());
        tester.deployAndNotify(app, applicationPackage, true, stagingTest);

        // system-test is now the only running job -- production jobs haven't started yet, since it is unfinished.
        tester.assertRunning(systemTest, app.id());
        assertEquals(1, tester.buildService().jobs().size());

        // system-test fails and is retried
        tester.deployAndNotify(app, applicationPackage, false, JobType.systemTest);
        assertEquals("Job is retried on failure", 1, tester.buildService().jobs().size());
        tester.deployAndNotify(app, applicationPackage, true, JobType.systemTest);

        tester.assertRunning(productionUsWest1, app.id());
    }

    @Test
    public void abortsInternalJobsOnNewApplicationChange() {
        InternalDeploymentTester iTester = new InternalDeploymentTester();
        tester = iTester.tester();

        Application app = iTester.app();
        ApplicationPackage applicationPackage = InternalDeploymentTester.applicationPackage;

        tester.jobCompletion(component).application(app).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app, true, systemTest);
        tester.deployAndNotify(app, true, stagingTest);
        tester.assertRunning(productionUsCentral1, app.id());

        // Jobs run externally are not affected.
        tester.jobCompletion(component).application(app).nextBuildNumber().uploadArtifact(applicationPackage).submit();
        tester.assertRunning(productionUsCentral1, app.id());

        tester.applications().deploymentTrigger().cancelChange(app.id(), ALL);
        tester.deployAndNotify(app, false, systemTest);
        tester.deployAndNotify(app, false, stagingTest);
        tester.deployAndNotify(app, false, productionUsCentral1);
        assertEquals(Change.empty(), tester.application(app.id()).change());
        tester.assertNotRunning(systemTest, app.id());
        tester.assertNotRunning(stagingTest, app.id());
        tester.assertNotRunning(productionUsCentral1, app.id());

        RunId id = iTester.newRun(productionUsCentral1);
        assertTrue(iTester.jobs().active(id).isPresent());

        // Jobs run internally are aborted.
        iTester.newSubmission();
        assertTrue(iTester.jobs().active(id).isPresent());
        iTester.runner().run();
        assertFalse(iTester.jobs().active(id).isPresent());

        tester.readyJobTrigger().maintain();
        assertEquals(EnumSet.of(systemTest, stagingTest), iTester.jobs().active().stream()
                                                                 .map(run -> run.id().type())
                                                                 .collect(Collectors.toCollection(() -> EnumSet.noneOf(JobType.class))));

        iTester.runJob(JobType.systemTest);
        iTester.runJob(JobType.stagingTest);
        tester.readyJobTrigger().maintain();
        iTester.runJob(JobType.productionUsCentral1);
        tester.readyJobTrigger().maintain();
        iTester.runJob(JobType.productionUsWest1);
        iTester.runJob(JobType.productionUsEast3);
        assertEquals(Change.empty(), iTester.app().change());

        tester.upgradeSystem(new Version("8.9"));
        iTester.runJob(JobType.systemTest);
        iTester.runJob(JobType.stagingTest);
        tester.readyJobTrigger().maintain();

        // Jobs run internally are not aborted when the new submission is delayed.
        iTester.newSubmission();
        iTester.runner().run();
        assertEquals(EnumSet.of(productionUsCentral1), iTester.jobs().active().stream()
                                                              .map(run -> run.id().type())
                                                              .collect(Collectors.toCollection(() -> EnumSet.noneOf(JobType.class))));
    }

    @Test
    public void deploymentSpecDecidesTriggerOrder() {
        TenantName tenant = tester.controllerTester().createTenant("tenant1", "domain1", 1L);
        MockBuildService mockBuildService = tester.buildService();
        Application application = tester.controllerTester().createApplication(tenant, "app1", "default", 1L);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-east-3")
                .region("us-central-1")
                .region("us-west-1")
                .build();

        // Component job finishes
        tester.jobCompletion(component).application(application).uploadArtifact(applicationPackage).submit();

        // Application is deployed to all test environments and declared zones
        tester.deployAndNotify(application, applicationPackage, true, JobType.systemTest);
        tester.deployAndNotify(application, applicationPackage, true, JobType.stagingTest);
        tester.deployAndNotify(application, applicationPackage, true, JobType.productionUsEast3);
        tester.deployAndNotify(application, applicationPackage, true, JobType.productionUsCentral1);
        tester.deployAndNotify(application, applicationPackage, true, JobType.productionUsWest1);
        assertTrue("All jobs consumed", mockBuildService.jobs().isEmpty());
    }

    @Test
    public void deploymentsSpecWithDelays() {
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
        tester.assertRunning(productionUsWest1, application.id());

        // 3 minutes pass, delayed trigger does nothing as us-west-1 is still in progress
        tester.clock().advance(Duration.ofMinutes(3));
        tester.deploymentTrigger().triggerReadyJobs();
        assertEquals(1, mockBuildService.jobs().size());
        tester.assertRunning(productionUsWest1, application.id());

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
        tester.assertRunning(productionUsEast3, application.id());
        tester.assertRunning(productionUsWest1, application.id());

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
    public void testNoOtherChangesDuringSuspension() {
        // Application is deployed in 3 regions:
        Application application = tester.createApplication("app1", "tenant1", 1, 1L);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                                                        .environment(Environment.prod)
                                                        .region("us-central-1")
                                                        .parallel("us-west-1", "us-east-3")
                                                        .build();
        tester.jobCompletion(component)
              .application(application)
              .uploadArtifact(applicationPackage)
              .submit();
        tester.deployAndNotify(application, applicationPackage, true, JobType.systemTest);
        tester.deployAndNotify(application, applicationPackage, true, JobType.stagingTest);
        tester.deployAndNotify(application, applicationPackage, true, JobType.productionUsCentral1);
        tester.deployAndNotify(application, applicationPackage, true, JobType.productionUsWest1);
        tester.deployAndNotify(application, applicationPackage, true, JobType.productionUsEast3);

        // The first production zone is suspended:
        tester.configServer().setSuspended(new DeploymentId(application.id(), JobType.productionUsCentral1.zone(tester.controller().system())), true);

        // A new change needs to be pushed out, but should not go beyond the suspended zone:
        tester.jobCompletion(component)
              .application(application)
              .nextBuildNumber()
              .sourceRevision(new SourceRevision("repository1", "master", "cafed00d"))
              .uploadArtifact(applicationPackage)
              .submit();
        tester.deployAndNotify(application, applicationPackage, true, JobType.systemTest);
        tester.deployAndNotify(application, applicationPackage, true, JobType.stagingTest);
        tester.deployAndNotify(application, applicationPackage, true, JobType.productionUsCentral1);
        tester.triggerUntilQuiescence();
        tester.assertNotRunning(JobType.productionUsEast3, application.id());
        tester.assertNotRunning(JobType.productionUsWest1, application.id());

        // The zone is unsuspended so jobs start:
        tester.configServer().setSuspended(new DeploymentId(application.id(), JobType.productionUsCentral1.zone(tester.controller().system())), false);
        tester.triggerUntilQuiescence();
        tester.deployAndNotify(application, applicationPackage, true, JobType.productionUsWest1);
        tester.deployAndNotify(application, applicationPackage, true, JobType.productionUsEast3);
    }

    @Test
    public void parallelDeploymentCompletesOutOfOrder() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .parallel("us-east-3", "us-west-1")
                .build();

        Application app = tester.createApplication("app1", "tenant1", 1, 11L);
        tester.jobCompletion(component).application(app).uploadArtifact(applicationPackage).submit();

        // Test environments pass
        tester.deployAndNotify(app, applicationPackage, true, JobType.systemTest);
        tester.deployAndNotify(app, applicationPackage, true, JobType.stagingTest);

        // Last declared job completes first
        tester.deploy(JobType.productionUsWest1, app, applicationPackage);
        tester.jobCompletion(JobType.productionUsWest1).application(app).submit();
        assertTrue("Change is present as not all jobs are complete",
                   tester.applications().require(app.id()).change().hasTargets());

        // All jobs complete
        tester.deploy(JobType.productionUsEast3, app, applicationPackage);
        tester.jobCompletion(JobType.productionUsEast3).application(app).submit();
        assertFalse("Change has been deployed",
                    tester.applications().require(app.id()).change().hasTargets());
    }

    @Test
    public void testSuccessfulDeploymentApplicationPackageChanged() {
        TenantName tenant = tester.controllerTester().createTenant("tenant1", "domain1", 1L);
        MockBuildService mockBuildService = tester.buildService();
        Application application = tester.controllerTester().createApplication(tenant, "app1", "default", 1L);
        ApplicationPackage previousApplicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-east-3")
                .region("us-central-1")
                .region("us-west-1")
                .build();
        ApplicationPackage newApplicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-east-3")
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
        tester.deployAndNotify(application, newApplicationPackage, true, JobType.productionUsEast3);
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

        Version version = Version.fromString("6.2");
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
        assertTrue(tester.applications().require(app.id()).outstandingChange().hasTargets());
        tester.deployAndNotify(app, changedApplication, true, systemTest);
        tester.deployAndNotify(app, changedApplication, true, stagingTest);

        tester.outstandingChangeDeployer().run();
        assertTrue(tester.applications().require(app.id()).outstandingChange().hasTargets());

        readyJobsTrigger.run();
        assertEquals(emptyList(), tester.buildService().jobs());

        tester.clock().advance(Duration.ofHours(2)); // ---------------- Exit block window: 20:30

        tester.outstandingChangeDeployer().run();
        assertFalse(tester.applications().require(app.id()).outstandingChange().hasTargets());

        tester.deploymentTrigger().triggerReadyJobs(); // Schedules staging test for the blocked production job(s)
        assertEquals(singletonList(buildJob(app, productionUsWest1)), tester.buildService().jobs());
    }

    @Test
    public void testCompletionOfPartOfChangeDuringBlockWindow() {
        ManualClock clock = new ManualClock(Instant.parse("2017-09-26T17:30:00.00Z")); // Tuesday, 17:30
        DeploymentTester tester = new DeploymentTester(new ControllerTester(clock));
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .blockChange(true, true, "tue", "18", "UTC")
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

        // Entering block window will keep the outstanding change in place.
        clock.advance(Duration.ofHours(1));
        tester.outstandingChangeDeployer().run();
        tester.deployAndNotify(application, applicationPackage, true, productionUsWest1);
        assertEquals(BuildJob.defaultBuildNumber, tester.application(application.id()).deploymentJobs().jobStatus()
                                                               .get(productionUsWest1).lastSuccess().get().application().buildNumber().getAsLong());
        assertEquals((BuildJob.defaultBuildNumber + 1), tester.application(application.id()).outstandingChange().application().get().buildNumber().getAsLong());

        tester.readyJobTrigger().maintain();
        // Platform upgrade keeps rolling, since it has already deployed in a production zone, and tests for the new revision have also started.
        assertEquals(3, tester.buildService().jobs().size());
        tester.deployAndNotify(application, applicationPackage, true, productionUsEast3);
        assertEquals(2, tester.buildService().jobs().size());

        // Upgrade is done, and oustanding change rolls out when block window ends.
        assertEquals(Change.empty(), tester.application(application.id()).change());
        assertFalse(tester.application(application.id()).change().hasTargets());
        assertTrue(tester.application(application.id()).outstandingChange().hasTargets());

        tester.deployAndNotify(application, applicationPackage, true, stagingTest);
        tester.deployAndNotify(application, applicationPackage, true, systemTest);
        clock.advance(Duration.ofHours(1));
        tester.outstandingChangeDeployer().run();
        assertTrue(tester.application(application.id()).change().hasTargets());
        assertFalse(tester.application(application.id()).outstandingChange().hasTargets());

        tester.readyJobTrigger().run();
        tester.deployAndNotify(application, applicationPackage, true, productionUsWest1);
        tester.deployAndNotify(application, applicationPackage, true, productionUsEast3);

        assertFalse(tester.application(application.id()).change().hasTargets());
        assertFalse(tester.application(application.id()).outstandingChange().hasTargets());
    }

    @Test
    public void testJobPause() {
        Application app = tester.createAndDeploy("app", 3, "default");
        tester.upgradeSystem(new Version("9.8.7"));

        tester.applications().deploymentTrigger().pauseJob(app.id(), productionUsWest1, tester.clock().instant().plus(Duration.ofSeconds(1)));
        tester.applications().deploymentTrigger().pauseJob(app.id(), productionUsEast3, tester.clock().instant().plus(Duration.ofSeconds(3)));

        // us-west-1 does not trigger when paused.
        tester.deployAndNotify(app, true, systemTest);
        tester.deployAndNotify(app, true, stagingTest);
        tester.assertNotRunning(productionUsWest1, app.id());

        // us-west-1 triggers when no longer paused, but does not retry when paused again.
        tester.clock().advance(Duration.ofMillis(1500));
        tester.readyJobTrigger().run();
        tester.assertRunning(productionUsWest1, app.id());
        tester.applications().deploymentTrigger().pauseJob(app.id(), productionUsWest1, tester.clock().instant().plus(Duration.ofSeconds(1)));
        tester.deployAndNotify(app, false, productionUsWest1);
        tester.assertNotRunning(productionUsWest1, app.id());
        tester.clock().advance(Duration.ofMillis(1000));
        tester.readyJobTrigger().run();
        tester.deployAndNotify(app, true, productionUsWest1);

        // us-east-3 does not automatically trigger when paused, but does when forced.
        tester.assertNotRunning(productionUsEast3, app.id());
        tester.deploymentTrigger().forceTrigger(app.id(), productionUsEast3, "mrTrigger");
        tester.assertRunning(productionUsEast3, app.id());
        assertFalse(tester.application(app.id()).deploymentJobs().jobStatus().get(productionUsEast3).pausedUntil().isPresent());
    }

    @Test
    public void testUpgradingButNoJobStarted() {
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
        tester.assertRunning(systemTest, app.id());
        tester.assertRunning(stagingTest, app.id());
    }

    @Test
    public void applicationVersionIsNotDowngraded() {
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
        tester.deployAndNotify(application, false, productionUsCentral1);
        tester.deploy(productionUsCentral1, application, Optional.empty(), false);

        ApplicationVersion appVersion1 = ApplicationVersion.from(BuildJob.defaultSourceRevision, BuildJob.defaultBuildNumber + 1);
        assertEquals(appVersion1, app.get().deployments().get(ZoneId.from("prod.us-central-1")).applicationVersion());

        // Verify the application change is not removed when change is cancelled.
        tester.deploymentTrigger().cancelChange(application.id(), PLATFORM);
        assertEquals(Change.of(appVersion1), app.get().change());

        // Now cancel the change as is done through the web API.
        tester.deploymentTrigger().cancelChange(application.id(), ALL);
        assertEquals(Change.empty(), app.get().change());

        // A new version is released, which should now deploy the currently deployed application version to avoid downgrades.
        Version version1 = new Version("6.2");
        tester.upgradeSystem(version1);
        tester.jobCompletion(productionUsCentral1).application(application).unsuccessful().submit();
        tester.deployAndNotify(application, true, systemTest);
        tester.deployAndNotify(application, true, stagingTest);
        tester.deployAndNotify(application, false, productionUsCentral1);

        // The last job has a different target, and the tests need to run again.
        // These may now start, since the first job has been triggered once, and thus is verified already.
        tester.deployAndNotify(application, true, systemTest);
        tester.deployAndNotify(application, true, stagingTest);

        // Finally, the two production jobs complete, in order.
        tester.deployAndNotify(application, true, productionUsCentral1);
        tester.deployAndNotify(application, true, productionEuWest1);
        assertEquals(appVersion1, app.get().deployments().get(ZoneId.from("prod.us-central-1")).applicationVersion());
    }

    @Test
    public void stepIsCompletePreciselyWhenItShouldBe() {
        Application application1 = tester.createApplication("app1", "tenant1", 1, 1L);
        Application application2 = tester.createApplication("app2", "tenant2", 2, 2L);
        Supplier<Application> app1 = () -> tester.application(application1.id());
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-central-1")
                .region("eu-west-1")
                .build();

        // System upgrades to version0 and applications deploy on that version
        Version version0 = Version.fromString("7.0");
        tester.upgradeSystem(version0);
        tester.deployCompletely(application1, applicationPackage);
        tester.deployCompletely(application2, applicationPackage);

        // version1 is released and application1 skips upgrading to that version
        Version version1 = Version.fromString("7.1");
        tester.upgradeSystem(version1);
        // Deploy application2 to keep this version present in the system
        tester.deployCompletely(application2, applicationPackage);
        tester.applications().deploymentTrigger().cancelChange(application1.id(), ALL);
        tester.buildService().clear(); // Clear stale build jobs for cancelled change

        // version2 is released and application1 starts upgrading
        Version version2 = Version.fromString("7.2");
        tester.upgradeSystem(version2);
        tester.completeUpgradeWithError(application1, version2, applicationPackage, productionUsCentral1);
        tester.deploy(productionUsCentral1, application1, applicationPackage);
        tester.deployAndNotify(application1, applicationPackage, false, productionUsCentral1);
        assertEquals(version2, app1.get().deployments().get(productionUsCentral1.zone(main)).version());

        // version2 becomes broken and upgrade targets latest non-broken
        tester.upgrader().overrideConfidence(version2, VespaVersion.Confidence.broken);
        tester.computeVersionStatus();
        tester.upgrader().maintain(); // Cancel upgrades to broken version
        assertEquals("Change becomes latest non-broken version", Change.of(version1), app1.get().change());
        tester.deployAndNotify(application1, applicationPackage, false, productionUsCentral1);
        Instant triggered = app1.get().deploymentJobs().jobStatus().get(productionUsCentral1).lastTriggered().get().at();
        tester.clock().advance(Duration.ofHours(1));

        // version1 proceeds 'til the last job, where it fails; us-central-1 is skipped, as current change is strictly dominated by what's deployed there.
        tester.deployAndNotify(application1, applicationPackage, true, systemTest);
        tester.deployAndNotify(application1, applicationPackage, true, stagingTest);
        assertEquals(triggered, app1.get().deploymentJobs().jobStatus().get(productionUsCentral1).lastTriggered().get().at());
        tester.deployAndNotify(application1, applicationPackage, false, productionEuWest1);

        //Eagerly triggered system and staging tests complete.
        tester.deployAndNotify(application1, applicationPackage, true, systemTest);
        tester.deployAndNotify(application1, applicationPackage, true, stagingTest);

        // Roll out a new application version, which gives a dual change -- this should trigger us-central-1, but only as long as it hasn't yet deployed there.
        tester.jobCompletion(component).application(application1).nextBuildNumber().uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(application1, applicationPackage, false, productionEuWest1);
        tester.deployAndNotify(application1, applicationPackage, true, systemTest);
        tester.deployAndNotify(application1, applicationPackage, true, stagingTest);

        tester.assertRunning(productionUsCentral1, application1.id());
        assertEquals(version2, app1.get().deployments().get(productionUsCentral1.zone(main)).version());
        assertEquals(42, app1.get().deployments().get(productionUsCentral1.zone(main)).applicationVersion().buildNumber().getAsLong());
        assertNotEquals(triggered, app1.get().deploymentJobs().jobStatus().get(productionUsCentral1).lastTriggered().get().at());

        // Change has a higher application version than what is deployed -- deployment should trigger.
        tester.deployAndNotify(application1, applicationPackage, false, productionUsCentral1);
        tester.deploy(productionUsCentral1, application1, applicationPackage);
        assertEquals(version2, app1.get().deployments().get(productionUsCentral1.zone(main)).version());
        assertEquals(43, app1.get().deployments().get(productionUsCentral1.zone(main)).applicationVersion().buildNumber().getAsLong());

        // Change is again strictly dominated, and us-central-1 is skipped, even though it is still failing.
        tester.clock().advance(Duration.ofHours(2).plus(Duration.ofSeconds(1))); // Enough time for retry
        tester.readyJobTrigger().maintain();
        // Failing job is not retried as change has been deployed
        tester.assertNotRunning(productionUsCentral1, application1.id());

        // Last job has a different deployment target, so tests need to run again.
        tester.deployAndNotify(application1, true, systemTest);
        tester.deployAndNotify(application1, true, stagingTest);
        tester.deployAndNotify(application1, applicationPackage, true, productionEuWest1);
        assertFalse(app1.get().change().hasTargets());
        assertFalse(app1.get().deploymentJobs().jobStatus().get(productionUsCentral1).isSuccess());
    }

    @Test
    public void eachDeployTargetIsTested() {
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
        tester.deployAndNotify(application, true, systemTest);
        tester.deployAndNotify(application, true, stagingTest);
        tester.deploymentTrigger().cancelChange(application.id(), PLATFORM);
        tester.deploy(productionEuWest1, application, applicationPackage);
        assertEquals(v2, app.get().deployments().get(productionEuWest1.zone(main)).version());
        assertEquals(v1, app.get().deployments().get(productionUsEast3.zone(main)).version());

        // New application version should run system and staging tests against both 6.1 and 6.2, in no particular order.
        tester.jobCompletion(component).application(application).nextBuildNumber().uploadArtifact(applicationPackage).submit();
        Version firstTested = app.get().deploymentJobs().jobStatus().get(systemTest).lastTriggered().get().platform();
        assertEquals(firstTested, app.get().deploymentJobs().jobStatus().get(stagingTest).lastTriggered().get().platform());

        tester.deployAndNotify(application, true, systemTest);
        tester.deployAndNotify(application, true, stagingTest);

        // Tests are not re-triggered, because the jobs they were run for has not yet been triggered with the tested versions.
        assertEquals(firstTested, app.get().deploymentJobs().jobStatus().get(systemTest).lastTriggered().get().platform());
        assertEquals(firstTested, app.get().deploymentJobs().jobStatus().get(stagingTest).lastTriggered().get().platform());

         // Finish old runs of the production jobs, which fail.
        tester.deployAndNotify(application, applicationPackage, false, productionEuWest1);
        tester.deployAndNotify(application, applicationPackage, false, productionUsEast3);
        tester.triggerUntilQuiescence();

        // New upgrade is already tested for one of the jobs, which has now been triggered, and tests may run for the other job.
        assertNotEquals(firstTested, app.get().deploymentJobs().jobStatus().get(systemTest).lastTriggered().get().platform());
        assertNotEquals(firstTested, app.get().deploymentJobs().jobStatus().get(stagingTest).lastTriggered().get().platform());
        tester.deployAndNotify(application, true, systemTest);
        tester.deployAndNotify(application, true, stagingTest);

        // Both jobs fail again, and must be re-triggered -- this is ok, as they are both already triggered on their current targets.
        tester.deployAndNotify(application, false, productionEuWest1);
        tester.deployAndNotify(application, false, productionUsEast3);
        tester.deployAndNotify(application, true, productionUsEast3);
        tester.deployAndNotify(application, true, productionEuWest1);
        assertFalse(app.get().change().hasTargets());
        assertEquals(43, app.get().deploymentJobs().jobStatus().get(productionEuWest1).lastSuccess().get().application().buildNumber().getAsLong());
        assertEquals(43, app.get().deploymentJobs().jobStatus().get(productionUsEast3).lastSuccess().get().application().buildNumber().getAsLong());
    }

    @Test
    public void eachDifferentUpgradeCombinationIsTested() {
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
        tester.deployAndNotify(application, true, systemTest);
        tester.deployAndNotify(application, true, stagingTest);
        tester.deployAndNotify(application, true, productionUsCentral1);
        tester.deployAndNotify(application, true, productionEuWest1);
        tester.deployAndNotify(application, false, productionUsEast3);
        assertEquals(v2, app.get().deployments().get(ZoneId.from("prod", "us-central-1")).version());
        assertEquals(v2, app.get().deployments().get(ZoneId.from("prod", "eu-west-1")).version());
        assertEquals(v1, app.get().deployments().get(ZoneId.from("prod", "us-east-3")).version());

        Version v3 = new Version("6.3");
        tester.upgradeSystem(v3);
        tester.deployAndNotify(application, false, productionUsEast3);

        // See that sources for staging are: first v2, then v1.
        tester.deployAndNotify(application, true, systemTest);
        tester.deployAndNotify(application, true, stagingTest);
        assertEquals(v2, app.get().deploymentJobs().jobStatus().get(stagingTest).lastSuccess().get().sourcePlatform().get());
        tester.deployAndNotify(application, true, productionUsCentral1);
        assertEquals(v1, app.get().deploymentJobs().jobStatus().get(stagingTest).lastTriggered().get().sourcePlatform().get());
        tester.deployAndNotify(application, true, stagingTest);
        tester.deployAndNotify(application, true, productionEuWest1);
        tester.deployAndNotify(application, true, productionUsEast3);
    }

    @Test
    public void retriesFailingJobs() {
        Application application = tester.createApplication("app1", "tenant1", 1, 1L);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-central-1")
                .build();

        // Deploy completely on default application and platform versions
        tester.deployCompletely(application, applicationPackage);

        // New application change is deployed and fails in system-test for a while
        tester.jobCompletion(component).application(application).nextBuildNumber().uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(application, false, systemTest);
        tester.deployAndNotify(application, true, stagingTest);

        // Retries immediately in the first minute after failing
        tester.clock().advance(Duration.ofSeconds(59));
        tester.jobCompletion(systemTest).application(application).unsuccessful().submit();
        tester.readyJobTrigger().maintain();
        tester.assertRunning(systemTest, application.id());

        // Stops immediate retry after failing for 1 minute
        tester.clock().advance(Duration.ofSeconds(1));
        tester.jobCompletion(systemTest).application(application).unsuccessful().submit();
        tester.readyJobTrigger().maintain();
        tester.assertNotRunning(systemTest, application.id());

        // Retries after 10 minutes since previous completion as we failed within the last hour
        tester.clock().advance(Duration.ofMinutes(10).plus(Duration.ofSeconds(1)));
        tester.readyJobTrigger().maintain();
        tester.assertRunning(systemTest, application.id());

        // Retries less frequently after 1 hour of failure
        tester.clock().advance(Duration.ofMinutes(50));
        tester.jobCompletion(systemTest).application(application).unsuccessful().submit();
        tester.readyJobTrigger().maintain();
        tester.assertNotRunning(systemTest, application.id());

        // Retries after two hours pass since last completion
        tester.clock().advance(Duration.ofHours(2).plus(Duration.ofSeconds(1)));
        tester.readyJobTrigger().maintain();
        tester.assertRunning(systemTest, application.id());

        // Still fails and is not retried
        tester.jobCompletion(systemTest).application(application).unsuccessful().submit();
        tester.readyJobTrigger().maintain();
        tester.assertNotRunning(systemTest, application.id());

        // Another application change is deployed and fixes system-test. Change is triggered immediately as target changes
        tester.jobCompletion(component).application(application).nextBuildNumber(2).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(application, true, systemTest);
        tester.deployAndNotify(application, true, stagingTest);
        tester.deployAndNotify(application, true, productionUsCentral1);
        assertTrue("Deployment completed", tester.buildService().jobs().isEmpty());
    }

    @Test
    public void testRetryingFailedJobsDuringDeployment() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("canary")
                .environment(Environment.prod)
                .region("us-east-3")
                .build();
        Version version = Version.fromString("6.2");
        tester.upgradeSystem(version);

        Application app = tester.createApplication("app1", "tenant1", 1, 11L);
        tester.jobCompletion(component).application(app).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app, applicationPackage, true, JobType.systemTest);
        tester.deployAndNotify(app, applicationPackage, true, JobType.stagingTest);
        tester.deployAndNotify(app, applicationPackage, true, JobType.productionUsEast3);

        // New version is released
        version = Version.fromString("6.3");
        tester.upgradeSystem(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        tester.readyJobTrigger().maintain();

        // Test environments pass
        tester.deployAndNotify(app, applicationPackage, true, JobType.systemTest);
        tester.deployAndNotify(app, applicationPackage, true, JobType.stagingTest);

        // Production job fails and is retried
        tester.clock().advance(Duration.ofSeconds(1)); // Advance time so that we can detect jobs in progress
        tester.deployAndNotify(app, applicationPackage, false, JobType.productionUsEast3);
        assertEquals("Production job is retried", 1, tester.buildService().jobs().size());
        assertEquals("Application has pending upgrade to " + version, version, tester.application(app.id()).change().platform().get());

        // Another version is released, which cancels any pending upgrades to lower versions
        version = Version.fromString("6.4");
        tester.upgradeSystem(version);
        tester.upgrader().maintain();
        tester.jobCompletion(JobType.productionUsEast3).application(app).unsuccessful().submit();
        assertEquals("Application starts upgrading to new version", 2, tester.buildService().jobs().size());
        assertEquals("Application has pending upgrade to " + version, version, tester.application(app.id()).change().platform().get());

        // Failure re-deployer did not retry failing job for prod.us-east-3, since it no longer had an available change
        assertFalse("Job is not retried", tester.buildService().jobs().stream()
                                                .anyMatch(j -> j.jobName().equals(JobType.productionUsEast3.jobName())));

        // Test environments pass
        tester.deployAndNotify(app, applicationPackage, true, JobType.systemTest);
        tester.deployAndNotify(app, applicationPackage, true, JobType.stagingTest);

        // Production job fails again, and is retried
        tester.deployAndNotify(app, applicationPackage, false, JobType.productionUsEast3);
        assertEquals("Job is retried", Collections.singletonList(ControllerTester.buildJob(app, productionUsEast3)), tester.buildService().jobs());

        // Production job finally succeeds
        tester.deployAndNotify(app, applicationPackage, true, JobType.productionUsEast3);
        assertTrue("All jobs consumed", tester.buildService().jobs().isEmpty());
        assertFalse("No failures", tester.application(app.id()).deploymentJobs().hasFailures());
    }

    @Test
    public void testRetriesJobsFailingForCurrentChange() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("canary")
                .environment(Environment.prod)
                .region("us-east-3")
                .build();
        Version version = Version.fromString("6.2");
        tester.upgradeSystem(version);

        Application app = tester.createApplication("app1", "tenant1", 1, 11L);
        tester.jobCompletion(component).application(app).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app, applicationPackage, true, JobType.systemTest);
        tester.deployAndNotify(app, applicationPackage, true, JobType.stagingTest);
        tester.deployAndNotify(app, applicationPackage, true, JobType.productionUsEast3);

        // New version is released
        version = Version.fromString("6.3");
        tester.upgradeSystem(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        tester.readyJobTrigger().maintain();
        assertEquals("Application has pending upgrade to " + version, version, tester.application(app.id()).change().platform().get());

        // system-test fails and is left with a retry
        tester.deployAndNotify(app, applicationPackage, false, JobType.systemTest);

        // Another version is released
        version = Version.fromString("6.4");
        tester.upgradeSystem(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());

        tester.buildService().remove(ControllerTester.buildJob(app, systemTest));
        tester.upgrader().maintain();
        tester.readyJobTrigger().maintain();
        assertEquals("Application has pending upgrade to " + version, version, tester.application(app.id()).change().platform().get());

        // Cancellation of outdated version and triggering on a new version is done by the upgrader.
        assertEquals(version, tester.application(app.id()).deploymentJobs().jobStatus().get(systemTest).lastTriggered().get().platform());
    }

    @Test
    public void testUpdatesFailingJobStatus() {
        // Setup application
        Application app = tester.createApplication("app1", "foo", 1, 1L);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .build();

        // Initial failure
        Instant initialFailure = tester.clock().instant().truncatedTo(MILLIS);
        tester.jobCompletion(component).application(app).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app, applicationPackage, false, systemTest);
        assertEquals("Failure age is right at initial failure",
                     initialFailure, tester.firstFailing(app, systemTest).get().at());

        // Failure again -- failingSince should remain the same
        tester.clock().advance(Duration.ofMillis(1000));
        tester.deployAndNotify(app, applicationPackage, false, systemTest);
        assertEquals("Failure age is right at second consecutive failure",
                     initialFailure, tester.firstFailing(app, systemTest).get().at());

        // Success resets failingSince
        tester.clock().advance(Duration.ofMillis(1000));
        tester.deployAndNotify(app, applicationPackage, true, systemTest);
        assertFalse(tester.firstFailing(app, systemTest).isPresent());

        // Complete deployment
        tester.deployAndNotify(app, applicationPackage, true, stagingTest);
        tester.deployAndNotify(app, applicationPackage, true, productionUsWest1);

        // Two repeated failures again.
        // Initial failure
        tester.clock().advance(Duration.ofMillis(1000));
        initialFailure = tester.clock().instant().truncatedTo(MILLIS);
        tester.jobCompletion(component).application(app).nextBuildNumber().uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app, applicationPackage, false, systemTest);
        assertEquals("Failure age is right at initial failure",
                     initialFailure, tester.firstFailing(app, systemTest).get().at());

        // Failure again -- failingSince should remain the same
        tester.clock().advance(Duration.ofMillis(1000));
        tester.deployAndNotify(app, applicationPackage, false, systemTest);
        assertEquals("Failure age is right at second consecutive failure",
                     initialFailure, tester.firstFailing(app, systemTest).get().at());
    }

    @Test
    public void applicationWithoutProjectIdIsNotTriggered() throws Exception {
        // Current system version, matches version in test data
        Version version = Version.fromString("6.42.1");
        tester.upgradeSystem(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());

        // Load test data data
        byte[] json = Files.readAllBytes(Paths.get("src/test/java/com/yahoo/vespa/hosted/controller/maintenance/testdata/application-without-project-id.json"));
        Slime slime = SlimeUtils.jsonToSlime(json);
        tester.controllerTester().createApplication(slime);

        // Failure redeployer does not restart deployment
        tester.readyJobTrigger().maintain();
        assertTrue("No jobs scheduled", tester.buildService().jobs().isEmpty());
    }

    @Test
    public void testPlatformVersionSelection() {
        // Setup system
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .build();
        Version version1 = tester.controller().versionStatus().systemVersion().get().versionNumber();

        Application app1 = tester.createApplication("application1", "tenant1", 1, 1L);

        // First deployment: An application change
        tester.jobCompletion(component).application(app1).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app1, applicationPackage, true, systemTest);
        tester.deployAndNotify(app1, applicationPackage, true, stagingTest);
        tester.deployAndNotify(app1, applicationPackage, true, productionUsWest1);

        app1 = tester.application(app1.id());
        assertEquals("First deployment gets system version", version1, app1.oldestDeployedPlatform().get());
        assertEquals(version1, tester.configServer().lastPrepareVersion().get());

        // Unexpected deployment
        tester.deploy(productionUsWest1, app1, applicationPackage);
        // applications are immutable, so any change to one, including deployment changes, would give rise to a new instance.
        assertEquals("Unexpected deployment is ignored", app1, tester.application(app1.id()));

        // Application change after a new system version, and a region added
        Version version2 = new Version(version1.getMajor(), version1.getMinor() + 1);
        tester.upgradeController(version2);
        tester.upgradeSystemApplications(version2);

        applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .region("us-east-3")
                .build();
        tester.jobCompletion(component).application(app1).nextBuildNumber().uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app1, applicationPackage, true, systemTest);
        tester.deployAndNotify(app1, applicationPackage, true, stagingTest);
        tester.deployAndNotify(app1, applicationPackage, true, productionUsWest1);

        app1 = tester.application(app1.id());
        assertEquals("Application change preserves version", version1, app1.oldestDeployedPlatform().get());
        assertEquals(version1, tester.configServer().lastPrepareVersion().get());

        // A deployment to the new region gets the same version
        tester.deployAndNotify(app1, applicationPackage, true, productionUsEast3);
        app1 = tester.application(app1.id());
        assertEquals("Application change preserves version", version1, app1.oldestDeployedPlatform().get());
        assertEquals(version1, tester.configServer().lastPrepareVersion().get());
        assertFalse("Change deployed", app1.change().hasTargets());

        // Version upgrade changes system version
        tester.deploymentTrigger().triggerChange(app1.id(), Change.of(version2));
        tester.deploymentTrigger().triggerReadyJobs();
        tester.deployAndNotify(app1, applicationPackage, true, systemTest);
        tester.deployAndNotify(app1, applicationPackage, true, stagingTest);
        tester.deployAndNotify(app1, applicationPackage, true, productionUsWest1);
        tester.deployAndNotify(app1, applicationPackage, true, productionUsEast3);

        app1 = tester.application(app1.id());
        assertEquals("Version upgrade changes version", version2, app1.oldestDeployedPlatform().get());
        assertEquals(version2, tester.configServer().lastPrepareVersion().get());
    }

    @Test
    public void requeueOutOfCapacityStagingJob() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-east-3")
                .build();

        long project1 = 1;
        long project2 = 2;
        long project3 = 3;
        Application app1 = tester.createApplication("app1", "tenant1", project1, 1L);
        Application app2 = tester.createApplication("app2", "tenant2", project2, 1L);
        Application app3 = tester.createApplication("app3", "tenant3", project3, 1L);
        MockBuildService mockBuildService = tester.buildService();

        // all applications: system-test completes successfully with some time in between, to determine trigger order.
        tester.jobCompletion(component).application(app2).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app2, applicationPackage, true, systemTest);
        tester.clock().advance(Duration.ofMinutes(1));

        tester.jobCompletion(component).application(app1).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app1, applicationPackage, true, systemTest);
        tester.clock().advance(Duration.ofMinutes(1));

        tester.jobCompletion(component).application(app3).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app3, applicationPackage, true, systemTest);

        // all applications: staging test jobs queued
        assertEquals(3, mockBuildService.jobs().size());

        // Abort all running jobs, so we have three candidate jobs, of which only one should be triggered at a time.
        tester.buildService().clear();

        List<BuildService.BuildJob> jobs = new ArrayList<>();
        assertJobsInOrder(jobs, tester.buildService().jobs());

        tester.triggerUntilQuiescence();
        jobs.add(buildJob(app2, stagingTest));
        jobs.add(buildJob(app1, stagingTest));
        jobs.add(buildJob(app3, stagingTest));
        assertJobsInOrder(jobs, tester.buildService().jobs());

        // Remove the jobs for app1 and app2, and then let app3 fail with outOfCapacity.
        // All three jobs are now eligible, but the one for app3 should trigger first as an outOfCapacity-retry.
        tester.buildService().remove(buildJob(app1, stagingTest));
        tester.buildService().remove(buildJob(app2, stagingTest));
        jobs.remove(buildJob(app1, stagingTest));
        jobs.remove(buildJob(app2, stagingTest));
        tester.jobCompletion(stagingTest).application(app3).error(DeploymentJobs.JobError.outOfCapacity).submit();
        assertJobsInOrder(jobs, tester.buildService().jobs());

        tester.triggerUntilQuiescence();
        jobs.add(buildJob(app2, stagingTest));
        jobs.add(buildJob(app1, stagingTest));
        assertJobsInOrder(jobs, tester.buildService().jobs());

        // Finish deployment for apps 2 and 3, then release a new version, leaving only app1 with an application upgrade.
        tester.deployAndNotify(app2, applicationPackage, true, stagingTest);
        tester.deployAndNotify(app2, applicationPackage, true, productionUsEast3);
        tester.deployAndNotify(app3, applicationPackage, true, stagingTest);
        tester.deployAndNotify(app3, applicationPackage, true, productionUsEast3);

        tester.upgradeSystem(new Version("6.2"));
        // app1 also gets a new application change, so its time of availability is after the version upgrade.
        tester.clock().advance(Duration.ofMinutes(1));
        tester.buildService().clear();
        tester.jobCompletion(component).application(app1).nextBuildNumber().uploadArtifact(applicationPackage).submit();
        jobs.clear();
        jobs.add(buildJob(app1, stagingTest));
        jobs.add(buildJob(app1, systemTest));
        // Tests for app1 trigger before the others since it carries an application upgrade.
        assertJobsInOrder(jobs, tester.buildService().jobs());

        // Let the test jobs start, remove everything expect system test for app3, which fails with outOfCapacity again.
        tester.triggerUntilQuiescence();
        tester.buildService().remove(buildJob(app1, systemTest));
        tester.buildService().remove(buildJob(app2, systemTest));
        tester.buildService().remove(buildJob(app1, stagingTest));
        tester.buildService().remove(buildJob(app2, stagingTest));
        tester.buildService().remove(buildJob(app3, stagingTest));
        tester.jobCompletion(systemTest).application(app3).error(DeploymentJobs.JobError.outOfCapacity).submit();
        jobs.clear();
        jobs.add(buildJob(app1, stagingTest));
        jobs.add(buildJob(app3, systemTest));
        assertJobsInOrder(jobs, tester.buildService().jobs());

        tester.triggerUntilQuiescence();
        jobs.add(buildJob(app2, stagingTest));
        jobs.add(buildJob(app1, systemTest));
        jobs.add(buildJob(app3, stagingTest));
        jobs.add(buildJob(app2, systemTest));
        assertJobsInOrder(jobs, tester.buildService().jobs());
    }

    /** Verifies that the given job lists have the same jobs, ignoring order of jobs that may have been triggered concurrently. */
    private static void assertJobsInOrder(List<BuildService.BuildJob> expected, List<BuildService.BuildJob> actual) {
        assertEquals(expected.stream().filter(job -> job.jobName().equals(systemTest.jobName())).collect(Collectors.toList()),
                     actual.stream().filter(job -> job.jobName().equals(systemTest.jobName())).collect(Collectors.toList()));
        assertEquals(expected.stream().filter(job -> job.jobName().equals(stagingTest.jobName())).collect(Collectors.toList()),
                     actual.stream().filter(job -> job.jobName().equals(stagingTest.jobName())).collect(Collectors.toList()));
        assertTrue(expected.containsAll(actual));
        assertTrue(actual.containsAll(expected));
    }

}
