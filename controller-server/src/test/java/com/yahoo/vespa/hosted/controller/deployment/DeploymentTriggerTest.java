// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import org.junit.Ignore;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Collectors;

import static com.yahoo.config.provision.SystemName.main;
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

    private final DeploymentTester tester = new DeploymentTester();

    @Test
    public void testTriggerFailing() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("default")
                .environment(Environment.prod)
                .region("us-west-1")
                .build();

        // Deploy completely once
        var app = tester.newDeploymentContext().submit(applicationPackage).deploy();

        // New version is released
        Version version = Version.fromString("6.3");
        tester.controllerTester().upgradeSystem(version);
        tester.upgrader().maintain();

        // staging-test fails deployment and is retried
        app.failDeployment(stagingTest);
        tester.triggerJobs();
        assertEquals("Retried dead job", 2, tester.jobs().active().size());
        app.assertRunning(stagingTest);
        app.runJob(stagingTest);

        // system-test is now the only running job -- production jobs haven't started yet, since it is unfinished.
        app.assertRunning(systemTest);
        assertEquals(1, tester.jobs().active().size());

        // system-test fails and is retried
        app.timeOutUpgrade(systemTest);
        tester.triggerJobs();
        assertEquals("Job is retried on failure", 1, tester.jobs().active().size());
        app.runJob(systemTest);

        tester.triggerJobs();
        app.assertRunning(productionUsWest1);

        // production-us-west-1 fails, but the app loses its projectId, and the job isn't retried.
        tester.applications().lockApplicationOrThrow(app.application().id(), locked ->
                tester.applications().store(locked.withProjectId(OptionalLong.empty())));
        app.timeOutConvergence(productionUsWest1);
        tester.triggerJobs();
        assertEquals("Job is not triggered when no projectId is present", 0, tester.jobs().active().size());
    }

    /*
    @Test
    @Ignore
    // TODO jonmv: Re-enable, but changed, when instances are orchestrated.
    public void testIndependentInstances() {
        var app1 = tester.tester().createApplication("instance1", "app", "tenant", 1, 1L);
        var app2 = tester.tester().createApplication("instance2", "app", "tenant", 2, 1L);
        Instance instance1 = tester.tester().instance(app1.id().instance(InstanceName.from("instance1")));
        Instance instance2 = tester.tester().instance(app2.id().instance(InstanceName.from("instance2")));
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                                                        .upgradePolicy("default")
                                                        .environment(Environment.prod)
                                                        .region("us-west-1")
                                                        .build();

        Version version = Version.fromString("6.2");
        tester.tester().upgradeSystem(version);

        // Deploy completely once
        tester.tester().jobCompletion(component).application(app1).application(instance1.id()).uploadArtifact(applicationPackage).submit();
        tester.tester().deployAndNotify(instance1.id(), Optional.of(applicationPackage), true, JobType.systemTest);
        tester.tester().deployAndNotify(instance1.id(), Optional.of(applicationPackage), true, JobType.stagingTest);
        tester.tester().deployAndNotify(instance1.id(), Optional.of(applicationPackage), true, JobType.productionUsWest1);

        tester.tester().jobCompletion(component).application(app2).application(instance2.id()).uploadArtifact(applicationPackage).submit();
        tester.tester().deployAndNotify(instance2.id(), Optional.of(applicationPackage), true, JobType.systemTest);
        tester.tester().deployAndNotify(instance2.id(), Optional.of(applicationPackage), true, JobType.stagingTest);
        tester.tester().deployAndNotify(instance2.id(), Optional.of(applicationPackage), true, JobType.productionUsWest1);

        // New version is released
        Version newVersion = Version.fromString("6.3");
        tester.tester().upgradeSystem(newVersion);

        // instance1 upgrades, but not instance 2
        tester.tester().deployAndNotify(instance1.id(), Optional.of(applicationPackage), true, JobType.systemTest);
        tester.tester().deployAndNotify(instance1.id(), Optional.of(applicationPackage), true, JobType.stagingTest);
        tester.tester().deployAndNotify(instance1.id(), Optional.of(applicationPackage), true, JobType.productionUsWest1);

        Version instance1Version = tester.tester().defaultInstance(app1.id()).deployments().get(JobType.productionUsWest1.zone(main)).version();
        Version instance2Version = tester.tester().defaultInstance(app2.id()).deployments().get(JobType.productionUsWest1.zone(main)).version();

        assertEquals(newVersion, instance1Version);
        assertEquals(version, instance2Version);
    }
    */

    @Test
    public void abortsJobsOnNewApplicationChange() {
        var app = tester.newDeploymentContext();
        app.submit()
           .runJob(systemTest)
           .runJob(stagingTest);

        tester.triggerJobs();
        RunId id = tester.jobs().last(app.instanceId(), productionUsCentral1).get().id();
        assertTrue(tester.jobs().active(id).isPresent());

        app.submit();
        assertTrue(tester.jobs().active(id).isPresent());

        tester.runner().run();
        assertFalse(tester.jobs().active(id).isPresent());

        tester.triggerJobs();
        assertEquals(EnumSet.of(systemTest, stagingTest), tester.jobs().active().stream()
                                                                .map(run -> run.id().type())
                                                                .collect(Collectors.toCollection(() -> EnumSet.noneOf(JobType.class))));

        app.deploy();
        assertEquals(Change.empty(), app.application().change());

        tester.controllerTester().upgradeSystem(new Version("8.9"));
        tester.upgrader().maintain();
        app.runJob(systemTest).runJob(stagingTest);
        tester.triggerJobs();

        // Jobs are not aborted when the new submission remains outstanding.
        app.submit();
        tester.runner().run();
        assertEquals(EnumSet.of(productionUsCentral1), tester.jobs().active().stream()
                                                             .map(run -> run.id().type())
                                                             .collect(Collectors.toCollection(() -> EnumSet.noneOf(JobType.class))));
    }

    @Test
    public void deploymentSpecWithDelays() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .delay(Duration.ofSeconds(30))
                .region("us-west-1")
                .delay(Duration.ofMinutes(2))
                .delay(Duration.ofMinutes(2)) // Multiple delays are summed up
                .region("us-central-1")
                .delay(Duration.ofMinutes(10)) // Delays after last region are valid, but have no effect
                .build();
        var app = tester.newDeploymentContext().submit(applicationPackage);

        // Test jobs pass
        app.runJob(systemTest).runJob(stagingTest);
        tester.triggerJobs();

        // No jobs have started yet, as 30 seconds have not yet passed.
        assertEquals(0, tester.jobs().active().size());
        tester.clock().advance(Duration.ofSeconds(30));
        tester.triggerJobs();

        // 30 seconds later, the first jobs may trigger.
        assertEquals(1, tester.jobs().active().size());
        app.assertRunning(productionUsWest1);

        // 3 minutes pass, delayed trigger does nothing as us-west-1 is still in progress
        tester.clock().advance(Duration.ofMinutes(3));
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size());
        app.assertRunning(productionUsWest1);

        // us-west-1 completes
        app.runJob(productionUsWest1);

        // Delayed trigger does nothing as not enough time has passed after us-west-1 completion
        tester.triggerJobs();
        assertTrue("No more jobs triggered at this time", tester.jobs().active().isEmpty());

        // 3 minutes pass, us-central-1 is still not triggered
        tester.clock().advance(Duration.ofMinutes(3));
        tester.triggerJobs();
        assertTrue("No more jobs triggered at this time", tester.jobs().active().isEmpty());

        // 4 minutes pass, us-central-1 is triggered
        tester.clock().advance(Duration.ofMinutes(1));
        tester.triggerJobs();
        app.runJob(productionUsCentral1);
        assertTrue("All jobs consumed", tester.jobs().active().isEmpty());

        // Delayed trigger job runs again, with nothing to trigger
        tester.clock().advance(Duration.ofMinutes(10));
        tester.triggerJobs();
        assertTrue("All jobs consumed", tester.jobs().active().isEmpty());
    }

    @Test
    public void deploymentSpecWithParallelDeployments() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-central-1")
                .parallel("us-west-1", "us-east-3")
                .region("eu-west-1")
                .build();

        var app = tester.newDeploymentContext().submit(applicationPackage);

        // Test jobs pass
        app.runJob(systemTest).runJob(stagingTest);

        // Deploys in first region
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size());
        app.runJob(productionUsCentral1);

        // Deploys in two regions in parallel
        tester.triggerJobs();
        assertEquals(2, tester.jobs().active().size());
        app.assertRunning(productionUsEast3);
        app.assertRunning(productionUsWest1);

        app.runJob(productionUsWest1);
        assertEquals(1, tester.jobs().active().size());
        app.assertRunning(productionUsEast3);

        app.runJob(productionUsEast3);

        // Last region completes
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size());
        app.runJob(productionEuWest1);
        assertTrue("All jobs consumed", tester.jobs().active().isEmpty());
    }

    @Test
    public void testNoOtherChangesDuringSuspension() {
        // Application is deployed in 3 regions:
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                                                        .environment(Environment.prod)
                                                        .region("us-central-1")
                                                        .parallel("us-west-1", "us-east-3")
                                                        .build();
        var application = tester.newDeploymentContext().submit().deploy();

        // The first production zone is suspended:
        tester.configServer().setSuspended(application.deploymentIdIn(ZoneId.from("prod", "us-central-1")), true);

        // A new change needs to be pushed out, but should not go beyond the suspended zone:
        application.submit()
                   .runJob(systemTest)
                   .runJob(stagingTest)
                   .runJob(productionUsCentral1);
        tester.triggerJobs();
        application.assertNotRunning(productionUsEast3);
        application.assertNotRunning(productionUsWest1);

        // The zone is unsuspended so jobs start:
        tester.configServer().setSuspended(application.deploymentIdIn(ZoneId.from("prod", "us-central-1")), false);
        tester.triggerJobs();
        application.runJob(productionUsWest1).runJob(productionUsEast3);
        assertEquals(Change.empty(), application.application().change());
    }

    @Test
    public void testBlockRevisionChange() {
        // Tuesday, 17:30
        tester.at(Instant.parse("2017-09-26T17:30:00.00Z"));

        Version version = Version.fromString("6.2");
        tester.controllerTester().upgradeSystem(version);

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("canary")
                // Block application version changes on tuesday in hours 18 and 19
                .blockChange(true, false, "tue", "18-19", "UTC")
                .region("us-west-1")
                .region("us-central-1")
                .region("us-east-3")
                .build();

        var app = tester.newDeploymentContext().submit(applicationPackage).deploy();

        tester.clock().advance(Duration.ofHours(1)); // --------------- Enter block window: 18:30

        tester.triggerJobs();
        assertEquals(0, tester.jobs().active().size());

        app.submit(applicationPackage);
        assertTrue(app.application().outstandingChange().hasTargets());
        app.runJob(systemTest).runJob(stagingTest);

        tester.outstandingChangeDeployer().run();
        assertTrue(app.application().outstandingChange().hasTargets());

        tester.triggerJobs();
        assertEquals(emptyList(), tester.jobs().active());

        tester.clock().advance(Duration.ofHours(2)); // ---------------- Exit block window: 20:30

        tester.outstandingChangeDeployer().run();
        assertFalse(app.application().outstandingChange().hasTargets());

        tester.triggerJobs(); // Tests already run for the blocked production job.
        app.assertRunning(productionUsWest1);
    }

    @Test
    public void testCompletionOfPartOfChangeDuringBlockWindow() {
        // Tuesday, 17:30
        tester.at(Instant.parse("2017-09-26T17:30:00.00Z"));
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .blockChange(true, true, "tue", "18", "UTC")
                .region("us-west-1")
                .region("us-east-3")
                .build();
        var app = tester.newDeploymentContext().submit(applicationPackage).deploy();

        // Application on (6.1, 1.0.1)
        Version v1 = Version.fromString("6.1");

        // Application is mid-upgrade when block window begins, and has an outstanding change.
        Version v2 = Version.fromString("6.2");
        tester.controllerTester().upgradeSystem(v2);
        tester.upgrader().maintain();
        app.submit(applicationPackage);

        app.runJob(stagingTest).runJob(systemTest);

        // Entering block window will keep the outstanding change in place.
        tester.clock().advance(Duration.ofHours(1));
        tester.outstandingChangeDeployer().run();
        app.runJob(productionUsWest1);
        assertEquals(1, app.instanceJobs().get(productionUsWest1).lastSuccess().get().versions().targetApplication().buildNumber().getAsLong());
        assertEquals(2, app.application().outstandingChange().application().get().buildNumber().getAsLong());

        tester.triggerJobs();
        // Platform upgrade keeps rolling, since it began before block window, and tests for the new revision have also started.
        assertEquals(3, tester.jobs().active().size());
        app.runJob(productionUsEast3);
        assertEquals(2, tester.jobs().active().size());

        // Upgrade is done, and outstanding change rolls out when block window ends.
        assertEquals(Change.empty(), app.application().change());
        assertTrue(app.application().outstandingChange().hasTargets());

        app.runJob(stagingTest).runJob(systemTest);
        tester.clock().advance(Duration.ofHours(1));
        tester.outstandingChangeDeployer().run();
        assertTrue(app.application().change().hasTargets());
        assertFalse(app.application().outstandingChange().hasTargets());

        app.runJob(productionUsWest1).runJob(productionUsEast3);

        assertFalse(app.application().change().hasTargets());
    }

    @Test
    public void testJobPause() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .region("us-west-1")
                .region("us-east-3")
                .build();
        var app = tester.newDeploymentContext().submit(applicationPackage).deploy();
        tester.controllerTester().upgradeSystem(new Version("9.8.7"));
        tester.upgrader().maintain();

        tester.deploymentTrigger().pauseJob(app.instanceId(), productionUsWest1,
                                                           tester.clock().instant().plus(Duration.ofSeconds(1)));
        tester.deploymentTrigger().pauseJob(app.instanceId(), productionUsEast3,
                                                           tester.clock().instant().plus(Duration.ofSeconds(3)));

        // us-west-1 does not trigger when paused.
        app.runJob(systemTest).runJob(stagingTest);
        tester.triggerJobs();
        app.assertNotRunning(productionUsWest1);

        // us-west-1 triggers when no longer paused, but does not retry when paused again.
        tester.clock().advance(Duration.ofMillis(1500));
        tester.triggerJobs();
        app.assertRunning(productionUsWest1);
        tester.deploymentTrigger().pauseJob(app.instanceId(), productionUsWest1, tester.clock().instant().plus(Duration.ofSeconds(1)));
        app.failDeployment(productionUsWest1);
        tester.triggerJobs();
        app.assertNotRunning(productionUsWest1);

        tester.clock().advance(Duration.ofMillis(1000));
        tester.triggerJobs();
        app.runJob(productionUsWest1);

        // us-east-3 does not automatically trigger when paused, but does when forced.
        tester.triggerJobs();
        app.assertNotRunning(productionUsEast3);
        tester.deploymentTrigger().forceTrigger(app.instanceId(), productionUsEast3, "mrTrigger", true);
        app.assertRunning(productionUsEast3);
        assertFalse(app.instance().jobPause(productionUsEast3).isPresent());
    }

    @Test
    public void applicationVersionIsNotDowngraded() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-central-1")
                .region("eu-west-1")
                .build();
        var app = tester.newDeploymentContext().submit(applicationPackage).deploy();

        // productionUsCentral1 fails after deployment, causing a mismatch between deployed and successful state.
        app.submit(applicationPackage)
           .runJob(systemTest)
           .runJob(stagingTest)
           .timeOutUpgrade(productionUsCentral1);

        ApplicationVersion appVersion1 = app.lastSubmission().get();
        assertEquals(appVersion1, app.deployment(ZoneId.from("prod.us-central-1")).applicationVersion());

        // Verify the application change is not removed when platform change is cancelled.
        tester.deploymentTrigger().cancelChange(app.application().id(), PLATFORM);
        assertEquals(Change.of(appVersion1), app.application().change());

        // Now cancel the change as is done through the web API.
        tester.deploymentTrigger().cancelChange(app.application().id(), ALL);
        assertEquals(Change.empty(), app.application().change());

        // A new version is released, which should now deploy the currently deployed application version to avoid downgrades.
        Version version1 = new Version("6.2");
        tester.controllerTester().upgradeSystem(version1);
        tester.upgrader().maintain();
        app.runJob(systemTest).runJob(stagingTest).failDeployment(productionUsCentral1);

        // The last job has a different target, and the tests need to run again.
        // These may now start, since the first job has been triggered once, and thus is verified already.
        app.runJob(systemTest).runJob(stagingTest);

        // Finally, the two production jobs complete, in order.
        app.runJob(productionUsCentral1).runJob(productionEuWest1);
        assertEquals(appVersion1, app.deployment(ZoneId.from("prod.us-central-1")).applicationVersion());
    }

    @Test
    public void stepIsCompletePreciselyWhenItShouldBe() {
        var app1 = tester.newDeploymentContext("tenant1", "app1", "default");
        var app2 = tester.newDeploymentContext("tenant1", "app2", "default");
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-central-1")
                .region("eu-west-1")
                .build();

        // System upgrades to version0 and applications deploy on that version
        Version version0 = Version.fromString("7.0");
        tester.controllerTester().upgradeSystem(version0);
        app1.submit(applicationPackage).deploy();
        app2.submit(applicationPackage).deploy();

        // version1 is released and application1 skips upgrading to that version
        Version version1 = Version.fromString("7.1");
        tester.controllerTester().upgradeSystem(version1);
        tester.upgrader().maintain();
        // Deploy application2 to keep this version present in the system
        app2.deployPlatform(version1);
        tester.deploymentTrigger().cancelChange(app1.application().id(), ALL);

        // version2 is released and application1 starts upgrading
        Version version2 = Version.fromString("7.2");
        tester.controllerTester().upgradeSystem(version2);
        tester.upgrader().maintain();
        app1.runJob(systemTest).runJob(stagingTest) // tests for previous version — these are "reused" later.
            .runJob(systemTest).runJob(stagingTest).timeOutConvergence(productionUsCentral1);
        assertEquals(version2, app1.deployment(productionUsCentral1.zone(main)).version());
        Instant triggered = app1.instanceJobs().get(productionUsCentral1).lastTriggered().get().start();
        tester.clock().advance(Duration.ofHours(1));

        // version2 becomes broken and upgrade targets latest non-broken
        tester.upgrader().overrideConfidence(version2, VespaVersion.Confidence.broken);
        tester.controllerTester().computeVersionStatus();
        tester.upgrader().maintain(); // Cancel upgrades to broken version
        assertEquals("Change becomes latest non-broken version", Change.of(version1), app1.application().change());

        // version1 proceeds 'til the last job, where it fails; us-central-1 is skipped, as current change is strictly dominated by what's deployed there.
        app1.failDeployment(productionEuWest1);
        assertEquals(triggered, app1.instanceJobs().get(productionUsCentral1).lastTriggered().get().start());

        // Roll out a new application version, which gives a dual change -- this should trigger us-central-1, but only as long as it hasn't yet deployed there.
        ApplicationVersion revision1 = app1.lastSubmission().get();
        app1.submit(applicationPackage);
        ApplicationVersion revision2 = app1.lastSubmission().get();
        app1.runJob(systemTest).runJob(stagingTest);
        assertEquals(Change.of(version1).with(revision2), app1.application().change());
        tester.triggerJobs();
        app1.assertRunning(productionUsCentral1);
        assertEquals(version2, app1.instance().deployments().get(productionUsCentral1.zone(main)).version());
        assertEquals(revision1, app1.deployment(productionUsCentral1.zone(main)).applicationVersion());
        assertTrue(triggered.isBefore(app1.instanceJobs().get(productionUsCentral1).lastTriggered().get().start()));

        // Change has a higher application version than what is deployed -- deployment should trigger.
        app1.timeOutUpgrade(productionUsCentral1);
        assertEquals(version2, app1.instance().deployments().get(productionUsCentral1.zone(main)).version());
        assertEquals(revision2, app1.deployment(productionUsCentral1.zone(main)).applicationVersion());

        // Change is again strictly dominated, and us-central-1 is skipped, even though it is still failing.
        tester.clock().advance(Duration.ofHours(2).plus(Duration.ofSeconds(1))); // Enough time for retry
        tester.triggerJobs();
        // Failing job is not retried as change has been deployed
        app1.assertNotRunning(productionUsCentral1);

        // Last job has a different deployment target, so tests need to run again.
        app1.runJob(systemTest).runJob(stagingTest).runJob(productionEuWest1);
        assertFalse(app1.application().change().hasTargets());
        assertFalse(app1.instanceJobs().get(productionUsCentral1).isSuccess());
    }

    @Test
    public void eachParallelDeployTargetIsTested() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .parallel("eu-west-1", "us-east-3")
                .build();
        // Application version 1 and platform version 6.1.
        var app = tester.newDeploymentContext().submit(applicationPackage).deploy();

        // Success in first prod zone, change cancelled between triggering and completion of eu west job.
        // One of the parallel zones get a deployment, but both fail their jobs.
        Version v1 = new Version("6.1");
        Version v2 = new Version("6.2");
        tester.controllerTester().upgradeSystem(v2);
        tester.upgrader().maintain();
        app.runJob(systemTest).runJob(stagingTest);
        app.timeOutConvergence(productionEuWest1);
        tester.deploymentTrigger().cancelChange(app.application().id(), PLATFORM);
        assertEquals(v2, app.deployment(productionEuWest1.zone(main)).version());
        assertEquals(v1, app.deployment(productionUsEast3.zone(main)).version());

        // New application version should run system and staging tests against both 6.1 and 6.2, in no particular order.
        app.submit(applicationPackage);
        tester.triggerJobs();
        Version firstTested = app.instanceJobs().get(systemTest).lastTriggered().get().versions().targetPlatform();
        assertEquals(firstTested, app.instanceJobs().get(stagingTest).lastTriggered().get().versions().targetPlatform());

        app.runJob(systemTest).runJob(stagingTest);

        // Test jobs for next production zone can start and run immediately.
        tester.triggerJobs();
        assertNotEquals(firstTested, app.instanceJobs().get(systemTest).lastTriggered().get().versions().targetPlatform());
        assertNotEquals(firstTested, app.instanceJobs().get(stagingTest).lastTriggered().get().versions().targetPlatform());
        app.runJob(systemTest).runJob(stagingTest);

         // Finish old run of the aborted production job.
        app.jobAborted(productionUsEast3);

        // New upgrade is already tested for both jobs.

        // Both jobs fail again, and must be re-triggered -- this is ok, as they are both already triggered on their current targets.
        app.failDeployment(productionEuWest1).failDeployment(productionUsEast3)
           .runJob(productionEuWest1).runJob(productionUsEast3);
        assertFalse(app.application().change().hasTargets());
        assertEquals(2, app.instanceJobs().get(productionEuWest1).lastSuccess().get().versions().targetApplication().buildNumber().getAsLong());
        assertEquals(2, app.instanceJobs().get(productionUsEast3).lastSuccess().get().versions().targetApplication().buildNumber().getAsLong());
    }

    @Test
    public void retriesFailingJobs() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-central-1")
                .build();

        // Deploy completely on default application and platform versions
        var app = tester.newDeploymentContext().submit(applicationPackage).deploy();

        // New application change is deployed and fails in system-test for a while
        app.submit(applicationPackage).runJob(stagingTest).failDeployment(systemTest);

        // Retries immediately in the first minute after failing
        tester.clock().advance(Duration.ofSeconds(59));
        app.failDeployment(systemTest);
        tester.triggerJobs();
        app.assertRunning(systemTest);

        // Stops immediate retry after failing for 1 minute
        tester.clock().advance(Duration.ofSeconds(1));
        app.failDeployment(systemTest);
        tester.triggerJobs();
        app.assertNotRunning(systemTest);

        // Retries after 10 minutes since previous completion as we failed within the last hour
        tester.clock().advance(Duration.ofMinutes(10).plus(Duration.ofSeconds(1)));
        tester.triggerJobs();
        app.assertRunning(systemTest);

        // Retries less frequently after 1 hour of failure
        tester.clock().advance(Duration.ofMinutes(50));
        app.failDeployment(systemTest);
        tester.triggerJobs();
        app.assertNotRunning(systemTest);

        // Retries after two hours pass since last completion
        tester.clock().advance(Duration.ofHours(2).plus(Duration.ofSeconds(1)));
        tester.triggerJobs();
        app.assertRunning(systemTest);

        // Still fails and is not retried
        app.failDeployment(systemTest);
        tester.triggerJobs();
        app.assertNotRunning(systemTest);

        // Another application change is deployed and fixes system-test. Change is triggered immediately as target changes
        app.submit(applicationPackage).deploy();
        assertTrue("Deployment completed", tester.jobs().active().isEmpty());
    }

    @Test
    public void testPlatformVersionSelection() {
        // Setup system
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .build();
        Version version1 = tester.controller().versionStatus().systemVersion().get().versionNumber();
        var app1 = tester.newDeploymentContext();

        // First deployment: An application change
        app1.submit(applicationPackage).deploy();

        assertEquals("First deployment gets system version", version1, app1.application().oldestDeployedPlatform().get());
        assertEquals(version1, tester.configServer().lastPrepareVersion().get());

        // Unexpected deployment is ignored
        Version version2 = new Version(version1.getMajor(), version1.getMinor() + 1);
        tester.applications().deploy(app1.instanceId(), ZoneId.from("prod", "us-west-1"),
                                     Optional.empty(), new DeployOptions(false, Optional.of(version2), false, false));
        assertEquals(version1, app1.deployment(ZoneId.from("prod", "us-west-1")).version());

        // Application change after a new system version, and a region added
        tester.controllerTester().upgradeSystem(version2);

        applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .region("us-east-3")
                .build();
        app1.submit(applicationPackage).deploy();
        assertEquals("Application change preserves version, and new region gets oldest version too",
                     version1, app1.application().oldestDeployedPlatform().get());
        assertEquals(version1, tester.configServer().lastPrepareVersion().get());
        assertFalse("Change deployed", app1.application().change().hasTargets());

        tester.upgrader().maintain();
        app1.deployPlatform(version2);

        assertEquals("Version upgrade changes version", version2, app1.application().oldestDeployedPlatform().get());
        assertEquals(version2, tester.configServer().lastPrepareVersion().get());
    }

    @Test
    public void requeueOutOfCapacityStagingJob() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-east-3")
                .build();

        var app1 = tester.newDeploymentContext("tenant1", "app1", "default").submit(applicationPackage);
        var app2 = tester.newDeploymentContext("tenant2", "app2", "default").submit(applicationPackage);
        var app3 = tester.newDeploymentContext("tenant3", "app3", "default").submit(applicationPackage);

        // all applications: system-test completes successfully with some time in between, to determine trigger order.
        app2.runJob(systemTest);
        tester.clock().advance(Duration.ofMinutes(1));

        app1.runJob(systemTest);
        tester.clock().advance(Duration.ofMinutes(1));

        app3.runJob(systemTest);

        // all applications: staging test jobs queued
        tester.triggerJobs();
        assertEquals(3, tester.jobs().active().size());

        // Abort all running jobs, so we have three candidate jobs, of which only one should be triggered at a time.
        tester.abortAll();

        assertEquals(List.of(), tester.jobs().active());

        tester.readyJobsTrigger().maintain();
        app2.assertRunning(stagingTest);
        assertEquals(1, tester.jobs().active().size());

        tester.readyJobsTrigger().maintain();
        app1.assertRunning(stagingTest);
        assertEquals(2, tester.jobs().active().size());

        tester.readyJobsTrigger().maintain();
        app3.assertRunning(stagingTest);
        assertEquals(3, tester.jobs().active().size());

        // Remove the jobs for app1 and app2, and then let app3 fail with outOfCapacity.
        // All three jobs are now eligible, but the one for app3 should trigger first as an outOfCapacity-retry.
        app3.outOfCapacity(stagingTest);
        app1.abortJob(stagingTest);
        app2.abortJob(stagingTest);

        tester.readyJobsTrigger().maintain();
        app3.assertRunning(stagingTest);
        assertEquals(1, tester.jobs().active().size());

        tester.readyJobsTrigger().maintain();
        app2.assertRunning(stagingTest);
        assertEquals(2, tester.jobs().active().size());

        tester.readyJobsTrigger().maintain();
        app1.assertRunning(stagingTest);
        assertEquals(3, tester.jobs().active().size());

        // Finish deployment for apps 2 and 3, then release a new version, leaving only app1 with an application upgrade.
        app2.deploy();
        app3.deploy();
        app1.assertRunning(stagingTest);
        assertEquals(1, tester.jobs().active().size());

        tester.controllerTester().upgradeSystem(new Version("6.2"));
        tester.upgrader().maintain();
        // app1 also gets a new application change, so its time of availability is after the version upgrade.
        tester.clock().advance(Duration.ofSeconds(1));
        app1.submit(applicationPackage);
        app1.jobAborted(stagingTest);

        // Tests for app1 trigger before the others since it carries an application upgrade.
        tester.readyJobsTrigger().maintain();
        app1.assertRunning(systemTest);
        app1.assertRunning(stagingTest);
        assertEquals(2, tester.jobs().active().size());

        // Let the test jobs start, remove everything except system test for app3, which fails with outOfCapacity again.
        tester.triggerJobs();
        app3.outOfCapacity(systemTest);
        app1.abortJob(systemTest);
        app1.abortJob(stagingTest);
        app2.abortJob(systemTest);
        app2.abortJob(stagingTest);
        app3.abortJob(stagingTest);
        assertEquals(0, tester.jobs().active().size());

        assertTrue(app1.application().change().application().isPresent());
        assertFalse(app2.application().change().application().isPresent());
        assertFalse(app3.application().change().application().isPresent());

        tester.readyJobsTrigger().maintain();
        app1.assertRunning(stagingTest);
        app3.assertRunning(systemTest);
        assertEquals(2, tester.jobs().active().size());

        tester.readyJobsTrigger().maintain();
        app2.assertRunning(stagingTest);
        app1.assertRunning(systemTest);
        assertEquals(4, tester.jobs().active().size());

        tester.readyJobsTrigger().maintain();
        app3.assertRunning(stagingTest);
        app2.assertRunning(systemTest);
        assertEquals(6, tester.jobs().active().size());
    }

    @Test
    public void testUserInstancesNotInDeploymentSpec() {
        var app = tester.newDeploymentContext();
        tester.controller().applications().createInstance(app.application().id().instance("user"));
        app.submit().deploy();
    }

    @Test
    public void testMultipleInstances() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .instances("instance1,instance2")
                .environment(Environment.prod)
                .region("us-east-3")
                .build();
        var app = tester.newDeploymentContext("tenant1", "application1", "instance1").submit(applicationPackage); // TODO jonmv: support instances in deployment context>
        var otherInstance = tester.newDeploymentContext("tenant1", "application1", "instance2");
        app.runJob(systemTest).runJob(stagingTest).runJob(productionUsEast3);
        otherInstance.runJob(systemTest).runJob(stagingTest).runJob(productionUsEast3);
        assertEquals(2, app.application().instances().size());
        assertEquals(2, app.application().productionDeployments().values().stream()
                              .mapToInt(Collection::size)
                              .sum());
    }

}
