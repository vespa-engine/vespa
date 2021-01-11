// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.integration.ServiceRegistryMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Collectors;

import static com.yahoo.config.provision.SystemName.main;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionApNortheast1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionApNortheast2;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionApSoutheast1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionAwsUsEast1a;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionCdAwsUsEast1a;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionCdUsCentral1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionEuWest1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionUsCentral1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionUsEast3;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionUsWest1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.systemTest;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.testApNortheast1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.testApNortheast2;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.testAwsUsEast1a;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.testEuWest1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.testUsCentral1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.testUsEast3;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.testUsWest1;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger.ChangesToCancel.ALL;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger.ChangesToCancel.PLATFORM;
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

    private DeploymentTester tester = new DeploymentTester();

    @Test
    public void testTriggerFailing() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("default")
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
        assertEquals(Change.empty(), app.instance().change());

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
                .systemTest()
                .delay(Duration.ofSeconds(30))
                .region("us-west-1")
                .delay(Duration.ofMinutes(2))
                .delay(Duration.ofMinutes(2)) // Multiple delays are summed up
                .region("us-central-1")
                .delay(Duration.ofMinutes(10)) // Delays after last region are valid, but have no effect
                .build();
        var app = tester.newDeploymentContext().submit(applicationPackage);

        // Test jobs pass
        app.runJob(systemTest);
        tester.clock().advance(Duration.ofSeconds(15));
        app.runJob(stagingTest);
        tester.triggerJobs();

        // No jobs have started yet, as 30 seconds have not yet passed.
        assertEquals(0, tester.jobs().active().size());
        tester.clock().advance(Duration.ofSeconds(15));
        tester.triggerJobs();

        // 30 seconds after the declared test, jobs may begin. The implicit test does not affect the delay.
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
                .region("us-central-1")
                .parallel("us-west-1", "us-east-3")
                .build();
        var application = tester.newDeploymentContext().submit().deploy();

        // The first production zone is suspended:
        tester.configServer().setSuspension(application.deploymentIdIn(ZoneId.from("prod", "us-central-1")), true);

        // A new change needs to be pushed out, but should not go beyond the suspended zone:
        application.submit()
                   .runJob(systemTest)
                   .runJob(stagingTest)
                   .runJob(productionUsCentral1);
        tester.triggerJobs();
        application.assertNotRunning(productionUsEast3);
        application.assertNotRunning(productionUsWest1);

        // The zone is unsuspended so jobs start:
        tester.configServer().setSuspension(application.deploymentIdIn(ZoneId.from("prod", "us-central-1")), false);
        tester.triggerJobs();
        application.runJob(productionUsWest1).runJob(productionUsEast3);
        assertEquals(Change.empty(), application.instance().change());
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
        assertTrue(app.deploymentStatus().outstandingChange(app.instance().name()).hasTargets());
        app.runJob(systemTest).runJob(stagingTest);

        tester.outstandingChangeDeployer().run();
        assertTrue(app.deploymentStatus().outstandingChange(app.instance().name()).hasTargets());

        tester.triggerJobs();
        assertEquals(emptyList(), tester.jobs().active());

        tester.clock().advance(Duration.ofHours(2)); // ---------------- Exit block window: 20:30

        tester.outstandingChangeDeployer().run();
        assertFalse(app.deploymentStatus().outstandingChange(app.instance().name()).hasTargets());

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
        assertEquals(2, app.deploymentStatus().outstandingChange(app.instance().name()).application().get().buildNumber().getAsLong());

        tester.triggerJobs();
        // Platform upgrade keeps rolling, since it began before block window, and tests for the new revision have also started.
        assertEquals(3, tester.jobs().active().size());
        app.runJob(productionUsEast3);
        assertEquals(2, tester.jobs().active().size());

        // Upgrade is done, and outstanding change rolls out when block window ends.
        assertEquals(Change.empty(), app.instance().change());
        assertTrue(app.deploymentStatus().outstandingChange(app.instance().name()).hasTargets());

        app.runJob(stagingTest).runJob(systemTest);
        tester.clock().advance(Duration.ofHours(1));
        tester.outstandingChangeDeployer().run();
        assertTrue(app.instance().change().hasTargets());
        assertFalse(app.deploymentStatus().outstandingChange(app.instance().name()).hasTargets());

        app.runJob(productionUsWest1).runJob(productionUsEast3);

        assertFalse(app.instance().change().hasTargets());
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
        tester.deploymentTrigger().cancelChange(app.instanceId(), PLATFORM);
        assertEquals(Change.of(appVersion1), app.instance().change());

        // Now cancel the change as is done through the web API.
        tester.deploymentTrigger().cancelChange(app.instanceId(), ALL);
        assertEquals(Change.empty(), app.instance().change());

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
        tester.deploymentTrigger().cancelChange(app1.instanceId(), ALL);

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
        assertEquals("Change becomes latest non-broken version", Change.of(version1), app1.instance().change());

        // version1 proceeds 'til the last job, where it fails; us-central-1 is skipped, as current change is strictly dominated by what's deployed there.
        app1.failDeployment(productionEuWest1);
        assertEquals(triggered, app1.instanceJobs().get(productionUsCentral1).lastTriggered().get().start());

        // Roll out a new application version, which gives a dual change -- this should trigger us-central-1, but only as long as it hasn't yet deployed there.
        ApplicationVersion revision1 = app1.lastSubmission().get();
        app1.submit(applicationPackage);
        ApplicationVersion revision2 = app1.lastSubmission().get();
        app1.runJob(systemTest).runJob(stagingTest);
        assertEquals(Change.of(version1).with(revision2), app1.instance().change());
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
        assertFalse(app1.instance().change().hasTargets());
        assertFalse(app1.instanceJobs().get(productionUsCentral1).isSuccess());
    }

    @Test
    public void eachParallelDeployTargetIsTested() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
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
        tester.deploymentTrigger().cancelChange(app.instanceId(), PLATFORM);
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
        assertFalse(app.instance().change().hasTargets());
        assertEquals(2, app.instanceJobs().get(productionEuWest1).lastSuccess().get().versions().targetApplication().buildNumber().getAsLong());
        assertEquals(2, app.instanceJobs().get(productionUsEast3).lastSuccess().get().versions().targetApplication().buildNumber().getAsLong());
    }

    @Test
    public void retriesFailingJobs() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .region("us-central-1")
                .build();

        // Deploy completely on default application and platform versions
        var app = tester.newDeploymentContext().submit(applicationPackage).deploy();

        // New application change is deployed and fails in system-test for a while
        app.submit(applicationPackage).runJob(stagingTest).failDeployment(systemTest);

        // Retries immediately once
        app.failDeployment(systemTest);
        tester.triggerJobs();
        app.assertRunning(systemTest);

        // Stops immediate retry when next triggering is considered after first failure
        tester.clock().advance(Duration.ofSeconds(1));
        app.failDeployment(systemTest);
        tester.triggerJobs();
        app.assertNotRunning(systemTest);

        // Retries after 10 minutes since previous completion, plus half the time since the first failure
        tester.clock().advance(Duration.ofMinutes(10).plus(Duration.ofSeconds(1)));
        tester.triggerJobs();
        app.assertRunning(systemTest);

        // Retries less frequently as more time passes
        app.failDeployment(systemTest);
        tester.clock().advance(Duration.ofMinutes(15));
        tester.triggerJobs();
        app.assertNotRunning(systemTest);

        // Retries again when sufficient time has passed
        tester.clock().advance(Duration.ofSeconds(2));
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
                .region("us-west-1")
                .build();
        Version version1 = tester.controller().readSystemVersion();
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
                .region("us-west-1")
                .region("us-east-3")
                .build();
        app1.submit(applicationPackage).deploy();
        assertEquals("Application change preserves version, and new region gets oldest version too",
                     version1, app1.application().oldestDeployedPlatform().get());
        assertEquals(version1, tester.configServer().lastPrepareVersion().get());
        assertFalse("Change deployed", app1.instance().change().hasTargets());

        tester.upgrader().maintain();
        app1.deployPlatform(version2);

        assertEquals("Version upgrade changes version", version2, app1.application().oldestDeployedPlatform().get());
        assertEquals(version2, tester.configServer().lastPrepareVersion().get());
    }

    @Test
    public void requeueOutOfCapacityStagingJob() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
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
        assertEquals(1, tester.jobs().active().size());

        tester.readyJobsTrigger().maintain();
        assertEquals(2, tester.jobs().active().size());

        tester.readyJobsTrigger().maintain();
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
        assertEquals(2, tester.jobs().active().size());

        tester.readyJobsTrigger().maintain();
        assertEquals(3, tester.jobs().active().size());

        // Finish deployment for apps 2 and 3, then release a new version, leaving only app1 with an application upgrade.
        app2.deploy();
        app3.deploy();
        app1.assertRunning(stagingTest);
        assertEquals(1, tester.jobs().active().size());

        tester.controllerTester().upgradeSystem(new Version("6.2"));
        tester.upgrader().maintain();
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

        assertTrue(app1.instance().change().application().isPresent());
        assertFalse(app2.instance().change().application().isPresent());
        assertFalse(app3.instance().change().application().isPresent());

        tester.readyJobsTrigger().maintain();
        app1.assertRunning(stagingTest);
        app3.assertRunning(systemTest);
        assertEquals(2, tester.jobs().active().size());

        tester.readyJobsTrigger().maintain();
        app1.assertRunning(systemTest);
        assertEquals(4, tester.jobs().active().size());

        tester.readyJobsTrigger().maintain();
        app3.assertRunning(stagingTest);
        app2.assertRunning(stagingTest);
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
                .region("us-east-3")
                .build();
        var app = tester.newDeploymentContext("tenant1", "application1", "instance1").submit(applicationPackage); // TODO jonmv: support instances in deployment context>
        var otherInstance = tester.newDeploymentContext("tenant1", "application1", "instance2");
        app.runJob(systemTest).runJob(stagingTest).runJob(productionUsEast3);
        otherInstance.runJob(productionUsEast3);
        assertEquals(2, app.application().instances().size());
        assertEquals(2, app.application().productionDeployments().values().stream()
                           .mapToInt(Collection::size)
                           .sum());
    }

    @Test
    public void testDeclaredProductionTests() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .region("us-east-3")
                .delay(Duration.ofMinutes(1))
                .test("us-east-3")
                .region("us-west-1")
                .region("us-central-1")
                .test("us-central-1")
                .test("us-west-1")
                .build();
        var app = tester.newDeploymentContext().submit(applicationPackage);

        app.runJob(systemTest).runJob(stagingTest).runJob(productionUsEast3);
        app.assertNotRunning(productionUsWest1);

        tester.clock().advance(Duration.ofMinutes(1));
        app.runJob(testUsEast3)
           .runJob(productionUsWest1).runJob(productionUsCentral1)
           .runJob(testUsCentral1).runJob(testUsWest1);
        assertEquals(Change.empty(), app.instance().change());

        // Application starts upgrade, but is confidence is broken cancelled after first zone. Tests won't run.
        Version version0 = app.application().oldestDeployedPlatform().get();
        Version version1 = Version.fromString("7.7");
        tester.controllerTester().upgradeSystem(version1);
        tester.upgrader().maintain();

        app.runJob(systemTest).runJob(stagingTest).runJob(productionUsEast3);
        tester.clock().advance(Duration.ofMinutes(1));
        app.failDeployment(testUsEast3);
        tester.triggerJobs();
        app.assertRunning(testUsEast3);

        tester.upgrader().overrideConfidence(version1, VespaVersion.Confidence.broken);
        tester.controllerTester().computeVersionStatus();
        tester.upgrader().maintain();
        app.failDeployment(testUsEast3);
        app.assertNotRunning(testUsEast3);
        assertEquals(Change.empty(), app.instance().change());

        // Application is pinned to previous version, and downgrades to that. Tests are re-run.
        tester.deploymentTrigger().triggerChange(app.instanceId(), Change.of(version0).withPin());
        app.runJob(stagingTest).runJob(productionUsEast3);
        tester.clock().advance(Duration.ofMinutes(1));
        app.failDeployment(testUsEast3);
        tester.clock().advance(Duration.ofMinutes(11)); // Job is cooling down after consecutive failures.
        app.runJob(testUsEast3);
        assertEquals(Change.empty().withPin(), app.instance().change());
    }

    @Test
    public void testDeployComplicatedDeploymentSpec() {
        String complicatedDeploymentSpec =
                "<deployment version='1.0' athenz-domain='domain' athenz-service='service'>\n" +
                "    <parallel>\n" +
                "        <instance id='instance' athenz-service='in-service'>\n" +
                "            <staging />\n" +
                "            <prod>\n" +
                "                <parallel>\n" +
                "                    <region active='true'>us-west-1</region>\n" +
                "                    <steps>\n" +
                "                        <region active='true'>us-east-3</region>\n" +
                "                        <delay hours='2' />\n" +
                "                        <region active='true'>eu-west-1</region>\n" +
                "                        <delay hours='2' />\n" +
                "                    </steps>\n" +
                "                    <steps>\n" +
                "                        <delay hours='3' />\n" +
                "                        <region active='true'>aws-us-east-1a</region>\n" +
                "                        <parallel>\n" +
                "                            <region active='true' athenz-service='no-service'>ap-northeast-1</region>\n" +
                "                            <region active='true'>ap-northeast-2</region>\n" +
                "                            <test>aws-us-east-1a</test>\n" +
                "                        </parallel>\n" +
                "                    </steps>\n" +
                "                    <delay hours='3' minutes='30' />\n" +
                "                </parallel>\n" +
                "                <parallel>\n" +
                "                   <test>ap-northeast-2</test>\n" +
                "                   <test>ap-northeast-1</test>\n" +
                "                </parallel>\n" +
                "                <test>us-east-3</test>\n" +
                "                <region active='true'>ap-southeast-1</region>\n" +
                "            </prod>\n" +
                "            <endpoints>\n" +
                "                <endpoint id='foo' container-id='bar'>\n" +
                "                    <region>us-east-3</region>\n" +
                "                </endpoint>\n" +
                "                <endpoint id='nalle' container-id='frosk' />\n" +
                "                <endpoint container-id='quux' />\n" +
                "            </endpoints>\n" +
                "        </instance>\n" +
                "        <instance id='other'>\n" +
                "            <upgrade policy='conservative' />\n" +
                "            <test />\n" +
                "            <block-change revision='true' version='false' days='sat' hours='0-23' time-zone='CET' />\n" +
                "            <prod>\n" +
                "                <region active='true'>eu-west-1</region>\n" +
                "                <test>eu-west-1</test>\n" +
                "            </prod>\n" +
                "            <notifications when='failing'>\n" +
                "                <email role='author' />\n" +
                "                <email address='john@dev' when='failing-commit' />\n" +
                "                <email address='jane@dev' />\n" +
                "            </notifications>\n" +
                "        </instance>\n" +
                "    </parallel>\n" +
                "    <instance id='last'>\n" +
                "        <upgrade policy='conservative' />\n" +
                "        <prod>\n" +
                "            <region active='true'>eu-west-1</region>\n" +
                "        </prod>\n" +
                "    </instance>\n" +
                "</deployment>\n";

        ApplicationPackage applicationPackage = ApplicationPackageBuilder.fromDeploymentXml(complicatedDeploymentSpec);
        var app1 = tester.newDeploymentContext("t", "a", "instance").submit(applicationPackage);
        var app2 = tester.newDeploymentContext("t", "a", "other");
        var app3 = tester.newDeploymentContext("t", "a", "last");

        // Verify that the first submission rolls out as per the spec.
        tester.triggerJobs();
        assertEquals(2, tester.jobs().active().size());
        app1.runJob(stagingTest);
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size());
        app2.runJob(systemTest);

        app1.runJob(productionUsWest1);
        tester.triggerJobs();
        assertEquals(2, tester.jobs().active().size());
        app1.runJob(productionUsEast3);
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size());

        tester.clock().advance(Duration.ofHours(2));

        app1.runJob(productionEuWest1);
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size());
        app2.assertNotRunning(testEuWest1);
        app2.runJob(productionEuWest1);
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size());
        app2.runJob(testEuWest1);
        tester.triggerJobs();
        assertEquals(List.of(), tester.jobs().active());

        tester.clock().advance(Duration.ofHours(1));
        app1.runJob(productionAwsUsEast1a);
        tester.triggerJobs();
        assertEquals(3, tester.jobs().active().size());
        app1.runJob(testAwsUsEast1a);
        tester.triggerJobs();
        assertEquals(2, tester.jobs().active().size());
        app1.runJob(productionApNortheast2);
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size());
        app1.runJob(productionApNortheast1);
        tester.triggerJobs();
        assertEquals(List.of(), tester.jobs().active());

        tester.clock().advance(Duration.ofMinutes(30));
        tester.triggerJobs();
        assertEquals(List.of(), tester.jobs().active());

        tester.clock().advance(Duration.ofMinutes(30));
        app1.runJob(testApNortheast1);
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size());
        app1.runJob(testApNortheast2);
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size());
        app1.runJob(testUsEast3);
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size());
        app1.runJob(productionApSoutheast1);
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size());
        app3.runJob(productionEuWest1);
        tester.triggerJobs();
        assertEquals(List.of(), tester.jobs().active());

        tester.atMondayMorning().clock().advance(Duration.ofDays(5)); // Inside revision block window for second, conservative instance.
        Version version = Version.fromString("8.1");
        tester.controllerTester().upgradeSystem(version);
        tester.upgrader().maintain();
        assertEquals(Change.of(version), app1.instance().change());
        assertEquals(Change.empty(), app2.instance().change());
        assertEquals(Change.empty(), app3.instance().change());

        // Upgrade instance 1; a failure in any instance allows an application change to accompany the upgrade.
        // The new platform won't roll out to the conservative instance until the normal one is upgraded.
        app2.failDeployment(systemTest);
        app1.submit(applicationPackage);
        assertEquals(Change.of(version).with(app1.application().latestVersion().get()), app1.instance().change());
        app2.runJob(systemTest);
        app1.jobAborted(stagingTest)
            .runJob(stagingTest)
            .runJob(productionUsWest1)
            .runJob(productionUsEast3);
        app1.runJob(stagingTest);   // Tests with only the outstanding application change.
        app2.runJob(systemTest);    // Tests with only the outstanding application change.
        tester.clock().advance(Duration.ofHours(2));
        app1.runJob(productionEuWest1);
        tester.clock().advance(Duration.ofHours(1));
        app1.runJob(productionAwsUsEast1a);
        tester.triggerJobs();
        app1.runJob(testAwsUsEast1a);
        app1.runJob(productionApNortheast2);
        app1.runJob(productionApNortheast1);
        tester.clock().advance(Duration.ofHours(1));
        app1.runJob(testApNortheast1);
        app1.runJob(testApNortheast2);
        app1.runJob(testUsEast3);
        app1.runJob(productionApSoutheast1);

        // Confidence rises to high, for the new version, and instance 2 starts to upgrade.
        tester.controllerTester().computeVersionStatus();
        tester.upgrader().maintain();
        tester.outstandingChangeDeployer().run();
        tester.triggerJobs();
        assertEquals(2, tester.jobs().active().size());
        assertEquals(Change.empty(), app1.instance().change());
        assertEquals(Change.of(version), app2.instance().change());
        assertEquals(Change.empty(), app3.instance().change());

        app1.runJob(stagingTest);   // Never completed successfully with just the upgrade.
        app2.runJob(systemTest)     // Never completed successfully with just the upgrade.
            .runJob(productionEuWest1)
            .failDeployment(testEuWest1);

        // Instance 2 failed the last job, and now exist block window, letting application change roll out with the upgrade.
        tester.clock().advance(Duration.ofDays(1)); // Leave block window for revisions.
        tester.upgrader().maintain();
        tester.outstandingChangeDeployer().run();
        assertEquals(0, tester.jobs().active().size());
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size());
        assertEquals(Change.empty(), app1.instance().change());
        assertEquals(Change.of(version).with(app1.application().latestVersion().get()), app2.instance().change());

        app2.runJob(productionEuWest1)
            .runJob(testEuWest1);
        assertEquals(Change.empty(), app2.instance().change());
        assertEquals(Change.empty(), app3.instance().change());

        // Two first instances upgraded and with new revision — last instance gets change from whatever maintainer runs first.
        tester.upgrader().maintain();
        tester.outstandingChangeDeployer().run();
        assertEquals(Change.of(version), app3.instance().change());

        tester.deploymentTrigger().cancelChange(app3.instanceId(), ALL);
        tester.outstandingChangeDeployer().run();
        tester.upgrader().maintain();
        assertEquals(Change.of(app1.application().latestVersion().get()), app3.instance().change());

        app3.runJob(productionEuWest1);
        tester.upgrader().maintain();
        app3.runJob(productionEuWest1);
        tester.triggerJobs();
        assertEquals(List.of(), tester.jobs().active());
        assertEquals(Change.empty(), app3.instance().change());
    }

    @Test
    public void testChangeCompletion() {
        var app = tester.newDeploymentContext().submit().deploy();
        var version = new Version("7.1");
        tester.controllerTester().upgradeSystem(version);
        tester.upgrader().maintain();
        app.runJob(systemTest).runJob(stagingTest).runJob(productionUsCentral1);

        app.submit();
        tester.triggerJobs();
        tester.outstandingChangeDeployer().run();
        assertEquals(Change.of(version), app.instance().change());

        app.runJob(productionUsEast3).runJob(productionUsWest1);
        tester.triggerJobs();
        tester.outstandingChangeDeployer().run();
        assertEquals(Change.of(app.lastSubmission().get()), app.instance().change());
    }

    @Test
    public void mixedDirectAndPipelineJobsInProduction() {
        ApplicationPackage cdPackage = new ApplicationPackageBuilder().region("cd-us-central-1")
                                                                      .region("cd-aws-us-east-1a")
                                                                      .build();
        ServiceRegistryMock services = new ServiceRegistryMock();
        var zones = List.of(ZoneApiMock.fromId("test.cd-us-central-1"),
                            ZoneApiMock.fromId("staging.cd-us-central-1"),
                            ZoneApiMock.fromId("prod.cd-us-central-1"),
                            ZoneApiMock.fromId("prod.cd-aws-us-east-1a"));
        services.zoneRegistry()
                .setSystemName(SystemName.cd)
                .setZones(zones)
                .setRoutingMethod(zones, RoutingMethod.shared);
        tester = new DeploymentTester(new ControllerTester(services));
        tester.configServer().bootstrap(services.zoneRegistry().zones().all().ids(), SystemApplication.values());
        tester.controllerTester().upgradeSystem(Version.fromString("6.1"));
        tester.controllerTester().computeVersionStatus();
        var app = tester.newDeploymentContext();

        app.runJob(productionCdUsCentral1, cdPackage);
        app.submit(cdPackage);
        app.runJob(systemTest);
        // Staging test requires unknown initial version, and is broken.
        tester.controller().applications().deploymentTrigger().forceTrigger(app.instanceId(), productionCdUsCentral1, "user", false);
        app.runJob(productionCdUsCentral1)
           .abortJob(stagingTest) // Complete failing run.
           .runJob(stagingTest)
           .runJob(productionCdAwsUsEast1a);

        app.runJob(productionCdUsCentral1, cdPackage);
        var version = new Version("7.1");
        tester.controllerTester().upgradeSystem(version);
        tester.upgrader().maintain();
        // System and staging tests both require unknown versions, and are broken.
        tester.controller().applications().deploymentTrigger().forceTrigger(app.instanceId(), productionCdUsCentral1, "user", false);
        app.runJob(productionCdUsCentral1)
           .abortJob(systemTest)
           .abortJob(stagingTest)
           .runJob(systemTest)
           .runJob(stagingTest)
           .runJob(productionCdAwsUsEast1a);

        app.runJob(productionCdUsCentral1, cdPackage);
        app.submit(cdPackage);
        app.runJob(systemTest);
        // Staging test requires unknown initial version, and is broken.
        tester.controller().applications().deploymentTrigger().forceTrigger(app.instanceId(), productionCdUsCentral1, "user", false);
        app.runJob(productionCdUsCentral1)
           .jobAborted(stagingTest)
           .runJob(stagingTest)
           .runJob(productionCdAwsUsEast1a);
    }

    @Test
    public void testsInSeparateInstance() {
        String deploymentSpec =
                "<deployment version='1.0'>\n" +
                "    <instance id='canary'>\n" +
                "        <upgrade policy='canary' />\n" +
                "        <test />\n" +
                "        <staging />\n" +
                "    </instance>\n" +
                "    <instance id='default'>\n" +
                "        <prod>\n" +
                "            <region active='true'>eu-west-1</region>\n" +
                "            <test>eu-west-1</test>\n" +
                "        </prod>\n" +
                "    </instance>\n" +
                "</deployment>\n";

        ApplicationPackage applicationPackage = ApplicationPackageBuilder.fromDeploymentXml(deploymentSpec);
        var canary = tester.newDeploymentContext("t", "a", "canary").submit(applicationPackage);
        var conservative = tester.newDeploymentContext("t", "a", "default");

        canary.runJob(systemTest)
              .runJob(stagingTest);
        conservative.runJob(productionEuWest1)
                    .runJob(testEuWest1);

        canary.submit(applicationPackage)
              .runJob(systemTest)
              .runJob(stagingTest);
        tester.outstandingChangeDeployer().run();
        conservative.runJob(productionEuWest1)
                    .runJob(testEuWest1);

        tester.controllerTester().upgradeSystem(new Version("7.7.7"));
        tester.upgrader().maintain();

        canary.runJob(systemTest)
              .runJob(stagingTest);
        tester.upgrader().maintain();
        conservative.runJob(productionEuWest1)
                    .runJob(testEuWest1);

    }

    @Test
    public void testEagerTests() {
        var app = tester.newDeploymentContext().submit().deploy();

        // Start upgrade, then receive new submission.
        Version version1 = new Version("7.8.9");
        ApplicationVersion build1 = app.lastSubmission().get();
        tester.controllerTester().upgradeSystem(version1);
        tester.upgrader().maintain();
        app.runJob(stagingTest);
        app.submit();
        ApplicationVersion build2 = app.lastSubmission().get();
        assertNotEquals(build1, build2);

        // App now free to start system tests eagerly, for new submission. These should run assuming upgrade succeeds.
        tester.triggerJobs();
        app.assertRunning(stagingTest);
        assertEquals(version1,
                     app.instanceJobs().get(stagingTest).lastCompleted().get().versions().targetPlatform());
        assertEquals(build1,
                     app.instanceJobs().get(stagingTest).lastCompleted().get().versions().targetApplication());

        assertEquals(version1,
                     app.instanceJobs().get(stagingTest).lastTriggered().get().versions().sourcePlatform().get());
        assertEquals(build1,
                     app.instanceJobs().get(stagingTest).lastTriggered().get().versions().sourceApplication().get());
        assertEquals(version1,
                     app.instanceJobs().get(stagingTest).lastTriggered().get().versions().targetPlatform());
        assertEquals(build2,
                     app.instanceJobs().get(stagingTest).lastTriggered().get().versions().targetApplication());

        // App completes upgrade, and outstanding change is triggered. This should let relevant, running jobs finish.
        app.runJob(systemTest)
           .runJob(productionUsCentral1)
           .runJob(productionUsEast3)
           .runJob(productionUsWest1);
        tester.outstandingChangeDeployer().run();

        assertEquals(RunStatus.running, tester.jobs().last(app.instanceId(), stagingTest).get().status());
        app.runJob(stagingTest);
        tester.triggerJobs();
        app.assertNotRunning(stagingTest);
    }

    @Test
    public void testTriggeringOfIdleTestJobsWhenFirstDeploymentIsOnNewerVersionThanChange() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder().systemTest()
                                                                               .stagingTest()
                                                                               .region("us-east-3")
                                                                               .region("us-west-1")
                                                                               .build();
        var app = tester.newDeploymentContext().submit(applicationPackage).deploy();
        var appToAvoidVersionGC = tester.newDeploymentContext("g", "c", "default").submit().deploy();

        Version version2 = new Version("7.8.9");
        Version version3 = new Version("8.9.10");
        tester.controllerTester().upgradeSystem(version2);
        tester.deploymentTrigger().triggerChange(appToAvoidVersionGC.instanceId(), Change.of(version2));
        appToAvoidVersionGC.deployPlatform(version2);

        // app upgrades first zone to version3, and then the other two to version2.
        tester.controllerTester().upgradeSystem(version3);
        tester.deploymentTrigger().triggerChange(app.instanceId(), Change.of(version3));
        app.runJob(systemTest).runJob(stagingTest);
        tester.triggerJobs();
        tester.upgrader().overrideConfidence(version3, VespaVersion.Confidence.broken);
        tester.controllerTester().computeVersionStatus();
        tester.upgrader().run();
        assertEquals(Optional.of(version2), app.instance().change().platform());

        app.runJob(systemTest)
           .runJob(productionUsEast3)
           .runJob(stagingTest)
           .runJob(productionUsWest1);

        assertEquals(version3, app.instanceJobs().get(productionUsEast3).lastSuccess().get().versions().targetPlatform());
        assertEquals(version2, app.instanceJobs().get(productionUsWest1).lastSuccess().get().versions().targetPlatform());
        assertEquals(Map.of(), app.deploymentStatus().jobsToRun());
        assertEquals(Change.empty(), app.instance().change());
        assertEquals(List.of(), tester.jobs().active());
    }

}
