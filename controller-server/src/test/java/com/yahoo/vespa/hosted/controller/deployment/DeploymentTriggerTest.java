// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException.ErrorCode;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneRegistryMock;
import com.yahoo.vespa.hosted.controller.maintenance.DeploymentUpgrader;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion.Confidence;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;

import static ai.vespa.validation.Validation.require;
import static com.yahoo.config.provision.Environment.prod;
import static com.yahoo.config.provision.SystemName.cd;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.applicationPackage;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.productionApNortheast1;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.productionApNortheast2;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.productionApSoutheast1;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.productionAwsUsEast1a;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.productionEuWest1;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.productionUsCentral1;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.productionUsEast3;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.productionUsWest1;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.stagingTest;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.systemTest;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.testApNortheast1;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.testApNortheast2;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.testEuWest1;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.testUsCentral1;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.testUsEast3;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.testUsWest1;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger.ChangesToCancel.ALL;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger.ChangesToCancel.PLATFORM;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
    void testTriggerFailing() {
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
        assertEquals(2, tester.jobs().active().size(), "Retried dead job");
        app.assertRunning(stagingTest);
        app.runJob(stagingTest);

        // system-test is now the only running job -- production jobs haven't started yet, since it is unfinished.
        app.assertRunning(systemTest);
        assertEquals(1, tester.jobs().active().size());

        // system-test fails and is retried
        app.timeOutUpgrade(systemTest);
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size(), "Job is retried on failure");
        app.runJob(systemTest);

        tester.triggerJobs();
        app.assertRunning(productionUsWest1);

        tester.jobs().abort(tester.jobs().last(app.instanceId(), productionUsWest1).get().id(), "cancelled", true);
        tester.runner().run();
        assertEquals(RunStatus.cancelled, tester.jobs().last(app.instanceId(), productionUsWest1).get().status());
        tester.triggerJobs();
        app.assertNotRunning(productionUsWest1);
        tester.deploymentTrigger().reTrigger(app.instanceId(), productionUsWest1, "retry");
        app.assertRunning(productionUsWest1);

        // invalid application is not retried
        tester.configServer().throwOnNextPrepare(new ConfigServerException(ErrorCode.INVALID_APPLICATION_PACKAGE, "nope", "bah"));
        tester.runner().run();
        assertEquals(RunStatus.invalidApplication, tester.jobs().last(app.instanceId(), productionUsWest1).get().status());
        tester.triggerJobs();
        app.assertNotRunning(productionUsWest1);

        // production-us-west-1 fails, but the app loses its projectId, and the job isn't retried.
        app.submit(applicationPackage).runJob(systemTest).runJob(stagingTest).triggerJobs();
        tester.applications().lockApplicationOrThrow(app.application().id(), locked ->
                tester.applications().store(locked.withProjectId(OptionalLong.empty())));

        app.timeOutConvergence(productionUsWest1);
        tester.triggerJobs();
        assertEquals(0, tester.jobs().active().size(), "Job is not triggered when no projectId is present");
    }

    @Test
    void revisionChangeWhenFailingMakesApplicationChangeWaitForPreviousToComplete() {
        DeploymentContext app = tester.newDeploymentContext();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .revisionChange(null) // separate by default, but we override this in test builder
                .region("us-east-3")
                .test("us-east-3")
                .build();

        app.submit(applicationPackage).runJob(systemTest).runJob(stagingTest).runJob(productionUsEast3);
        Optional<RevisionId> v0 = app.lastSubmission();

        app.submit(applicationPackage);
        Optional<RevisionId> v1 = app.lastSubmission();
        assertEquals(v0, app.instance().change().revision());

        // Eager tests still run before new revision rolls out.
        app.runJob(systemTest).runJob(stagingTest);

        // v0 rolls out completely.
        app.runJob(testUsEast3);
        assertEquals(Optional.empty(), app.instance().change().revision());

        // v1 starts rolling when v0 is done.
        tester.outstandingChangeDeployer().run();
        assertEquals(v1, app.instance().change().revision());

        // v1 fails, so v2 starts immediately.
        app.runJob(productionUsEast3).failDeployment(testUsEast3);
        app.submit(applicationPackage);
        Optional<RevisionId> v2 = app.lastSubmission();
        assertEquals(v2, app.instance().change().revision());
    }

    @Test
    void leadingUpgradeAllowsApplicationChangeWhileUpgrading() {
        var applicationPackage = new ApplicationPackageBuilder().region("us-east-3")
                .upgradeRollout("leading")
                .build();
        var app = tester.newDeploymentContext();

        app.submit(applicationPackage).deploy();

        Change upgrade = Change.of(new Version("6.8.9"));
        tester.controllerTester().upgradeSystem(upgrade.platform().get());
        tester.upgrader().maintain();
        app.runJob(systemTest).runJob(stagingTest);
        tester.triggerJobs();
        app.assertRunning(productionUsEast3);
        assertEquals(upgrade, app.instance().change());

        app.submit(applicationPackage);
        assertEquals(upgrade.with(app.lastSubmission().get()), app.instance().change());
    }

    @Test
    void abortsJobsOnNewApplicationChange() {
        var app = tester.newDeploymentContext();
        app.submit()
                .runJob(systemTest)
                .runJob(stagingTest);

        tester.triggerJobs();
        RunId id = tester.jobs().last(app.instanceId(), productionUsCentral1).get().id();
        assertTrue(tester.jobs().active(id).isPresent());

        app.submit();
        assertTrue(tester.jobs().active(id).isPresent());

        tester.triggerJobs();
        tester.runner().run();
        assertTrue(tester.jobs().active(id).isPresent()); // old run

        app.runJob(systemTest).runJob(stagingTest).runJob(stagingTest); // outdated run is aborted when otherwise blocking a new run
        tester.triggerJobs();
        app.jobAborted(productionUsCentral1);
        Versions outdated = tester.jobs().last(app.instanceId(), productionUsCentral1).get().versions();

        // Flesh bag re-triggers job, and _that_ is not aborted
        tester.deploymentTrigger().reTrigger(app.instanceId(), productionUsCentral1, "flesh bag");
        tester.triggerJobs();
        app.runJob(productionUsCentral1);
        Versions reTriggered = tester.jobs().last(app.instanceId(), productionUsCentral1).get().versions();
        assertEquals(outdated, reTriggered);

        app.runJob(productionUsCentral1).runJob(productionUsWest1).runJob(productionUsEast3);
        assertEquals(Change.empty(), app.instance().change());

        tester.controllerTester().upgradeSystem(new Version("6.9"));
        tester.upgrader().maintain();
        app.runJob(systemTest).runJob(stagingTest);
        tester.clock().advance(Duration.ofMinutes(1));
        tester.triggerJobs();

        // Upgrade is allowed to proceed ahead of revision change, and is not aborted.
        app.submit();
        app.runJob(systemTest).runJob(stagingTest);
        tester.triggerJobs();
        tester.runner().run();
        assertEquals(Set.of(productionUsCentral1), tester.jobs().active().stream()
                .map(run -> run.id().type())
                .collect(Collectors.toCollection(HashSet::new)));
    }

    @Test
    void similarDeploymentSpecsAreNotRolledOut() {
        ApplicationPackage firstPackage = new ApplicationPackageBuilder()
                .region("us-east-3")
                .build();

        DeploymentContext app = tester.newDeploymentContext().submit(firstPackage, 5417, 0);
        var version = app.lastSubmission();
        assertEquals(version, app.instance().change().revision());
        app.runJob(systemTest)
                .runJob(stagingTest)
                .runJob(productionUsEast3);
        assertEquals(Change.empty(), app.instance().change());

        // A similar application package is submitted. Since a new job is added, the original revision is again a target.
        ApplicationPackage secondPackage = new ApplicationPackageBuilder()
                .systemTest()
                .stagingTest()
                .region("us-east-3")
                .delay(Duration.ofHours(1))
                .test("us-east-3")
                .build();

        app.submit(secondPackage, 5417, 0);
        app.triggerJobs();
        assertEquals(List.of(), tester.jobs().active());
        assertEquals(version, app.instance().change().revision());

        tester.clock().advance(Duration.ofHours(1));
        app.runJob(testUsEast3);
        assertEquals(List.of(), tester.jobs().active());
        assertEquals(Change.empty(), app.instance().change());

        // The original application package is submitted again. No new jobs are added, so no change needs to roll out now.
        app.submit(firstPackage, 5417, 0);
        app.triggerJobs();
        assertEquals(List.of(), tester.jobs().active());
        assertEquals(Change.empty(), app.instance().change());
    }

    @Test
    void testOutstandingChangeWithNextRevisionTarget() {
        ApplicationPackage appPackage = new ApplicationPackageBuilder().revisionTarget("next")
                .revisionChange("when-failing")
                .region("us-east-3")
                .build();
        DeploymentContext app = tester.newDeploymentContext()
                .submit(appPackage);
        Optional<RevisionId> revision1 = app.lastSubmission();

        app.submit(appPackage);
        Optional<RevisionId> revision2 = app.lastSubmission();

        app.submit(appPackage);
        Optional<RevisionId> revision3 = app.lastSubmission();

        app.submit(appPackage);
        Optional<RevisionId> revision4 = app.lastSubmission();

        app.submit(appPackage);
        Optional<RevisionId> revision5 = app.lastSubmission();

        // 5 revisions submitted; the first is rolling out, and the others are queued.
        tester.outstandingChangeDeployer().run();
        assertEquals(revision1, app.instance().change().revision());
        assertEquals(revision2, app.deploymentStatus().outstandingChange(InstanceName.defaultName()).revision());

        // The second revision is set as the target by user interaction.
        tester.deploymentTrigger().forceChange(app.instanceId(), Change.of(revision2.get()));
        tester.outstandingChangeDeployer().run();
        assertEquals(revision2, app.instance().change().revision());
        assertEquals(revision3, app.deploymentStatus().outstandingChange(InstanceName.defaultName()).revision());

        // The second revision deploys completely, and the third starts rolling out.
        app.runJob(systemTest).runJob(stagingTest)
                .runJob(productionUsEast3);
        tester.outstandingChangeDeployer().run();
        tester.outstandingChangeDeployer().run();
        assertEquals(revision3, app.instance().change().revision());
        assertEquals(revision4, app.deploymentStatus().outstandingChange(InstanceName.defaultName()).revision());

        // The third revision fails, and the fourth is chosen to replace it.
        app.triggerJobs().timeOutConvergence(systemTest);
        tester.outstandingChangeDeployer().run();
        tester.outstandingChangeDeployer().run();
        assertEquals(revision4, app.instance().change().revision());
        assertEquals(revision5, app.deploymentStatus().outstandingChange(InstanceName.defaultName()).revision());

        // Tests for outstanding change are relevant when current revision completes.
        app.runJob(systemTest).runJob(systemTest)
                .jobAborted(stagingTest).runJob(stagingTest).runJob(stagingTest)
                .runJob(productionUsEast3);
        tester.outstandingChangeDeployer().run();
        tester.outstandingChangeDeployer().run();
        assertEquals(revision5, app.instance().change().revision());
        assertEquals(Change.empty(), app.deploymentStatus().outstandingChange(InstanceName.defaultName()));
        app.runJob(productionUsEast3);
    }

    @Test
    void deploymentSpecWithDelays() {
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
        assertTrue(tester.jobs().active().isEmpty(), "No more jobs triggered at this time");

        // 3 minutes pass, us-central-1 is still not triggered
        tester.clock().advance(Duration.ofMinutes(3));
        tester.triggerJobs();
        assertTrue(tester.jobs().active().isEmpty(), "No more jobs triggered at this time");

        // 4 minutes pass, us-central-1 is triggered
        tester.clock().advance(Duration.ofMinutes(1));
        tester.triggerJobs();
        app.runJob(productionUsCentral1);
        assertTrue(tester.jobs().active().isEmpty(), "All jobs consumed");

        // Delayed trigger job runs again, with nothing to trigger
        tester.clock().advance(Duration.ofMinutes(10));
        tester.triggerJobs();
        assertTrue(tester.jobs().active().isEmpty(), "All jobs consumed");
    }

    @Test
    void deploymentSpecWithParallelDeployments() {
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
        assertTrue(tester.jobs().active().isEmpty(), "All jobs consumed");
    }

    @Test
    void testNoOtherChangesDuringSuspension() {
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
    void testBlockRevisionChange() {
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
    void testCompletionOfPartOfChangeDuringBlockWindow() {
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

        // Application is mid-upgrade when block window begins, and gets an outstanding change.
        Version v2 = Version.fromString("6.2");
        tester.controllerTester().upgradeSystem(v2);
        tester.upgrader().maintain();
        app.runJob(stagingTest).runJob(systemTest);

        // Entering block window will keep the outstanding change in place.
        tester.clock().advance(Duration.ofHours(1));
        app.submit(applicationPackage);
        app.runJob(productionUsWest1);
        assertEquals(1, app.instanceJobs().get(productionUsWest1).lastSuccess().get().versions().targetRevision().number());
        assertEquals(2, app.deploymentStatus().outstandingChange(app.instance().name()).revision().get().number());

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
    void testJobPause() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .region("us-west-1")
                .region("us-east-3")
                .build();
        var app = tester.newDeploymentContext().submit(applicationPackage).deploy();
        tester.controllerTester().upgradeSystem(new Version("6.8.7"));
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
        tester.deploymentTrigger().forceTrigger(app.instanceId(), productionUsEast3, "mrTrigger", true, true, false);
        app.assertRunning(productionUsEast3);
        assertFalse(app.instance().jobPause(productionUsEast3).isPresent());
        assertEquals(app.deployment(productionUsEast3.zone()).version(),
                tester.jobs().last(app.instanceId(), productionUsEast3).get().versions().targetPlatform());
    }

    @Test
    void applicationVersionIsNotDowngraded() {
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

        RevisionId appVersion1 = app.lastSubmission().get();
        assertEquals(appVersion1, app.deployment(ZoneId.from("prod.us-central-1")).revision());

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
        assertEquals(appVersion1, app.deployment(ZoneId.from("prod.us-central-1")).revision());
    }

    RevisionId latestDeployed(Instance instance) {
        return instance.productionDeployments().values().stream()
                       .map(Deployment::revision)
                       .reduce((o, n) -> require(o.equals(n), n, "all versions should be equal, but got " + o + " and " + n))
                       .orElseThrow(() -> new AssertionError("no versions deployed"));
    }

    @Test
    void downgradingApplicationVersionWorks() {
        var app = tester.newDeploymentContext().submit().deploy();
        RevisionId appVersion0 = app.lastSubmission().get();
        assertEquals(appVersion0, latestDeployed(app.instance()));

        app.submit().deploy();
        RevisionId appVersion1 = app.lastSubmission().get();
        assertEquals(appVersion1, latestDeployed(app.instance()));

        // Downgrading application version.
        tester.deploymentTrigger().forceChange(app.instanceId(), Change.of(appVersion0).withRevisionPin());
        assertEquals(Change.of(appVersion0).withRevisionPin(), app.instance().change());
        app.runJob(stagingTest)
           .runJob(productionUsCentral1)
           .runJob(productionUsEast3)
           .runJob(productionUsWest1);
        assertEquals(Change.empty().withRevisionPin(), app.instance().change());
        assertEquals(appVersion0, app.instance().deployments().get(productionUsEast3.zone()).revision());
        assertEquals(appVersion0, latestDeployed(app.instance()));

        tester.outstandingChangeDeployer().run();
        assertEquals(Change.empty().withRevisionPin(), app.instance().change());
        tester.deploymentTrigger().cancelChange(app.instanceId(), ALL);
        tester.outstandingChangeDeployer().run();
        assertEquals(Change.of(appVersion1), app.instance().change());
    }

    @Test
    void settingANoOpChangeIsANoOp() {
        var app = tester.newDeploymentContext().submit();

        app.deploy();
        RevisionId appVersion0 = app.lastSubmission().get();
        assertEquals(appVersion0, latestDeployed(app.instance()));

        app.submit().deploy();
        RevisionId appVersion1 = app.lastSubmission().get();
        assertEquals(appVersion1, latestDeployed(app.instance()));

        // Triggering a roll-out of an already deployed application is a no-op.
        assertEquals(Change.empty(), app.instance().change());
        tester.deploymentTrigger().forceChange(app.instanceId(), Change.of(appVersion1));
        assertEquals(Change.empty(), app.instance().change());
        assertEquals(appVersion1, latestDeployed(app.instance()));
    }

    @Test
    void stepIsCompletePreciselyWhenItShouldBe() {
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
        tester.triggerJobs();
        app1.jobAborted(systemTest).jobAborted(stagingTest);
        app1.runJob(systemTest).runJob(stagingTest).timeOutConvergence(productionUsCentral1);
        assertEquals(version2, app1.deployment(productionUsCentral1.zone()).version());
        Instant triggered = app1.instanceJobs().get(productionUsCentral1).lastTriggered().get().start();
        tester.clock().advance(Duration.ofHours(1));

        // version2 becomes broken and upgrade targets latest non-broken
        tester.upgrader().overrideConfidence(version2, VespaVersion.Confidence.broken);
        tester.controllerTester().computeVersionStatus();
        tester.upgrader().maintain(); // Cancel upgrades to broken version
        assertEquals(Change.of(version1), app1.instance().change(), "Change becomes latest non-broken version");

        // version1 proceeds 'til the last job, where it fails; us-central-1 is skipped, as current change is strictly dominated by what's deployed there.
        app1.runJob(systemTest).runJob(stagingTest)
                .failDeployment(productionEuWest1);
        assertEquals(triggered, app1.instanceJobs().get(productionUsCentral1).lastTriggered().get().start());

        // Roll out a new application version, which gives a dual change -- this should trigger us-central-1, but only as long as it hasn't yet deployed there.
        RevisionId revision1 = app1.lastSubmission().get();
        app1.submit(applicationPackage);
        RevisionId revision2 = app1.lastSubmission().get();
        app1.runJob(systemTest)   // Tests for new revision on version2
                .runJob(stagingTest)
                .runJob(systemTest)   // Tests for new revision on version1
                .runJob(stagingTest);
        assertEquals(Change.of(version1).with(revision2), app1.instance().change());
        tester.triggerJobs();
        app1.assertRunning(productionUsCentral1);
        assertEquals(version2, app1.instance().deployments().get(productionUsCentral1.zone()).version());
        assertEquals(revision1, app1.deployment(productionUsCentral1.zone()).revision());
        assertTrue(triggered.isBefore(app1.instanceJobs().get(productionUsCentral1).lastTriggered().get().start()));

        // Change has a higher application version than what is deployed -- deployment should trigger.
        app1.timeOutUpgrade(productionUsCentral1);
        assertEquals(version2, app1.deployment(productionUsCentral1.zone()).version());
        assertEquals(revision2, app1.deployment(productionUsCentral1.zone()).revision());

        // Change is again strictly dominated, and us-central-1 is skipped, even though it is still failing.
        tester.clock().advance(Duration.ofHours(3)); // Enough time for retry
        tester.triggerJobs();
        // Failing job is not retried as change has been deployed
        app1.assertNotRunning(productionUsCentral1);

        // Last job has a different deployment target, so tests need to run again.
        app1.runJob(productionEuWest1)      // Upgrade completes, and revision is the only change.
            .runJob(productionUsCentral1)   // With only revision change, central should run to cover a previous failure.
            .runJob(productionEuWest1);     // Finally, west changes revision.
        assertEquals(Change.empty(), app1.instance().change());
        assertEquals(Optional.of(RunStatus.success), app1.instanceJobs().get(productionUsCentral1).lastStatus());
    }

    @Test
    void eachParallelDeployTargetIsTested() {
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
        assertEquals(v2, app.deployment(productionEuWest1.zone()).version());
        assertEquals(v1, app.deployment(productionUsEast3.zone()).version());

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
        app.triggerJobs().jobAborted(productionUsEast3);

        // New upgrade is already tested for both jobs.

        // Both jobs fail again, and must be re-triggered -- this is ok, as they are both already triggered on their current targets.
        app.failDeployment(productionEuWest1).failDeployment(productionUsEast3)
                .runJob(productionEuWest1).runJob(productionUsEast3);
        assertFalse(app.instance().change().hasTargets());
        assertEquals(2, app.instanceJobs().get(productionEuWest1).lastSuccess().get().versions().targetRevision().number());
        assertEquals(2, app.instanceJobs().get(productionUsEast3).lastSuccess().get().versions().targetRevision().number());
    }

    @Test
    void retriesFailingJobs() {
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
        assertTrue(tester.jobs().active().isEmpty(), "Deployment completed");
    }

    @Test
    void testPlatformVersionSelection() {
        // Setup system
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .region("us-west-1")
                .build();
        Version version1 = tester.controller().readSystemVersion();
        var app1 = tester.newDeploymentContext();

        // First deployment: An application change
        app1.submit(applicationPackage).deploy();

        assertEquals(version1, app1.application().oldestDeployedPlatform().get(), "First deployment gets system version");
        assertEquals(version1, tester.configServer().lastPrepareVersion().get());

        // Application change after a new system version, and a region added
        Version version2 = new Version(version1.getMajor(), version1.getMinor() + 1);
        tester.controllerTester().upgradeSystem(version2);

        applicationPackage = new ApplicationPackageBuilder()
                .region("us-west-1")
                .region("us-east-3")
                .build();
        app1.submit(applicationPackage).deploy();
        assertEquals(version1, app1.application().oldestDeployedPlatform().get(), "Application change preserves version, and new region gets oldest version too");
        assertEquals(version1, tester.configServer().lastPrepareVersion().get());
        assertFalse(app1.instance().change().hasTargets(), "Change deployed");

        tester.upgrader().maintain();
        app1.deployPlatform(version2);

        assertEquals(version2, app1.application().oldestDeployedPlatform().get(), "Version upgrade changes version");
        assertEquals(version2, tester.configServer().lastPrepareVersion().get());
    }

    @Test
    void requeueNodeAllocationFailureStagingJob() {
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

        // Remove the jobs for app1 and app2, and then let app3 fail node allocation.
        // All three jobs are now eligible, but the one for app3 should trigger first as a nodeAllocationFailure-retry.
        app3.nodeAllocationFailure(stagingTest);
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
        app1.runJob(stagingTest);
        assertEquals(0, tester.jobs().active().size());

        tester.controllerTester().upgradeSystem(new Version("6.2"));
        tester.upgrader().maintain();
        app1.submit(applicationPackage);

        // Tests for app1 trigger before the others since it carries an application upgrade.
        tester.readyJobsTrigger().run();
        app1.assertRunning(systemTest);
        app1.assertRunning(stagingTest);
        assertEquals(2, tester.jobs().active().size());

        // Let the test jobs start, remove everything except system test for app3, which fails node allocation again.
        tester.triggerJobs();
        app3.nodeAllocationFailure(systemTest);
        app1.abortJob(systemTest);
        app1.abortJob(stagingTest);
        app2.abortJob(systemTest);
        app2.abortJob(stagingTest);
        app3.abortJob(stagingTest);
        assertEquals(0, tester.jobs().active().size());

        assertTrue(app1.instance().change().revision().isPresent());
        assertFalse(app2.instance().change().revision().isPresent());
        assertFalse(app3.instance().change().revision().isPresent());

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
    void testUserInstancesNotInDeploymentSpec() {
        var app = tester.newDeploymentContext();
        tester.controller().applications().createInstance(app.application().id().instance("user"));
        app.submit().deploy();
    }

    @Test
    void testMultipleInstancesWithDifferentChanges() {
        DeploymentContext i1 = tester.newDeploymentContext("t", "a", "i1");
        DeploymentContext i2 = tester.newDeploymentContext("t", "a", "i2");
        DeploymentContext i3 = tester.newDeploymentContext("t", "a", "i3");
        DeploymentContext i4 = tester.newDeploymentContext("t", "a", "i4");
        ApplicationPackage applicationPackage = ApplicationPackageBuilder
                .fromDeploymentXml("""
                                   <deployment version='1'>
                                     <upgrade revision-change='when-failing' />
                                     <parallel>
                                       <instance id='i1'>
                                         <prod>
                                           <region>us-east-3</region>
                                           <delay hours='6' />
                                         </prod>
                                       </instance>
                                       <instance id='i2'>
                                         <prod>
                                           <region>us-east-3</region>
                                         </prod>
                                       </instance>
                                     </parallel>
                                     <instance id='i3'>
                                       <prod>
                                         <region>us-east-3</region>
                                           <delay hours='18' />
                                         <test>us-east-3</test>
                                       </prod>
                                     </instance>
                                     <instance id='i4'>
                                       <test />
                                       <staging />
                                       <prod>
                                         <region>us-east-3</region>
                                       </prod>
                                     </instance>
                                   </deployment>
                                   """);

        // Package is submitted, and change propagated to the two first instances.
        i1.submit(applicationPackage);
        Optional<RevisionId> v0 = i1.lastSubmission();
        tester.outstandingChangeDeployer().run();
        assertEquals(v0, i1.instance().change().revision());
        assertEquals(v0, i2.instance().change().revision());
        assertEquals(Optional.empty(), i3.instance().change().revision());
        assertEquals(Optional.empty(), i4.instance().change().revision());

        // Tests run in i4, as they're declared there, and i1 and i2 get to work
        i4.runJob(systemTest).runJob(stagingTest);
        i1.runJob(productionUsEast3);
        i2.runJob(productionUsEast3);

        // Since the post-deployment delay of i1 is incomplete, i3 doesn't yet get the change.
        tester.outstandingChangeDeployer().run();
        assertEquals(v0, Optional.of(latestDeployed(i1.instance())));
        assertEquals(v0, Optional.of(latestDeployed(i2.instance())));
        assertEquals(Optional.empty(), i1.instance().change().revision());
        assertEquals(Optional.empty(), i2.instance().change().revision());
        assertEquals(Optional.empty(), i3.instance().change().revision());
        assertEquals(Optional.empty(), i4.instance().change().revision());

        // When the delay is done, i3 gets the change.
        tester.clock().advance(Duration.ofHours(6));
        tester.outstandingChangeDeployer().run();
        assertEquals(Optional.empty(), i1.instance().change().revision());
        assertEquals(Optional.empty(), i2.instance().change().revision());
        assertEquals(v0, i3.instance().change().revision());
        assertEquals(Optional.empty(), i4.instance().change().revision());

        // v0 begins roll-out in i3, and v1 is submitted and rolls out in i1 and i2 some time later
        i3.runJob(productionUsEast3); // v0
        tester.clock().advance(Duration.ofHours(12));
        i1.submit(applicationPackage);
        Optional<RevisionId> v1 = i1.lastSubmission();
        i4.runJob(systemTest).runJob(stagingTest);
        i1.runJob(productionUsEast3); // v1
        i2.runJob(productionUsEast3); // v1
        assertEquals(v1, Optional.of(latestDeployed(i1.instance())));
        assertEquals(v1, Optional.of(latestDeployed(i2.instance())));
        assertEquals(Optional.empty(), i1.instance().change().revision());
        assertEquals(Optional.empty(), i2.instance().change().revision());
        assertEquals(v0, i3.instance().change().revision());
        assertEquals(Optional.empty(), i4.instance().change().revision());

        // After some time, v2 also starts rolling out to i1 and i2, but does not complete in i2
        tester.clock().advance(Duration.ofHours(3));
        i1.submit(applicationPackage);
        Optional<RevisionId> v2 = i1.lastSubmission();
        i4.runJob(systemTest).runJob(stagingTest);
        i1.runJob(productionUsEast3); // v2
        tester.clock().advance(Duration.ofHours(3));

        // v1 is all done in i1 and i2, but does not yet roll out in i3; v2 is not completely rolled out there yet.
        tester.outstandingChangeDeployer().run();
        assertEquals(v0, i3.instance().change().revision());

        // i3 completes v0, which rolls out to i4; v1 is ready for i3, but v2 is not.
        i3.runJob(testUsEast3);
        assertEquals(Optional.empty(), i3.instance().change().revision());
        tester.outstandingChangeDeployer().run();
        assertEquals(v2, Optional.of(latestDeployed(i1.instance())));
        assertEquals(v1, Optional.of(latestDeployed(i2.instance())));
        assertEquals(v0, Optional.of(latestDeployed(i3.instance())));
        assertEquals(Optional.empty(), i1.instance().change().revision());
        assertEquals(v2, i2.instance().change().revision());
        assertEquals(v1, i3.instance().change().revision());
        assertEquals(v0, i4.instance().change().revision());
    }

    @Test
    void testMultipleInstancesWithRevisionCatchingUpToUpgrade() {
        String spec = """
                      <deployment>
                          <instance id='alpha'>
                              <upgrade rollout="simultaneous" revision-target="next" />
                              <test />
                              <staging />
                          </instance>
                          <instance id='beta'>
                              <upgrade rollout="simultaneous" revision-change="when-clear" revision-target="next" />
                              <prod>
                                  <region>us-east-3</region>
                                  <test>us-east-3</test>
                              </prod>
                          </instance>
                      </deployment>
                      """;
        ApplicationPackage applicationPackage = ApplicationPackageBuilder.fromDeploymentXml(spec);
        DeploymentContext alpha = tester.newDeploymentContext("t", "a", "alpha");
        DeploymentContext beta = tester.newDeploymentContext("t", "a", "beta");
        alpha.submit(applicationPackage).deploy();

        Version version1 = new Version("6.2");
        tester.controllerTester().upgradeSystem(version1);
        tester.upgrader().run();
        alpha.runJob(systemTest).runJob(stagingTest);
        assertEquals(Change.empty(), alpha.instance().change());
        assertEquals(Change.empty(), beta.instance().change());

        tester.upgrader().run();
        assertEquals(Change.empty(), alpha.instance().change());
        assertEquals(Change.of(version1), beta.instance().change());

        tester.outstandingChangeDeployer().run();
        beta.triggerJobs();
        tester.runner().run();
        tester.outstandingChangeDeployer().run();
        beta.triggerJobs();
        tester.outstandingChangeDeployer().run();
        beta.assertRunning(productionUsEast3);
        beta.assertNotRunning(testUsEast3);

        alpha.submit(applicationPackage);
        Optional<RevisionId> revision2 = alpha.lastSubmission();
        assertEquals(Change.of(revision2.get()), alpha.instance().change());
        assertEquals(Change.of(version1), beta.instance().change());

        alpha.runJob(systemTest).runJob(stagingTest);
        assertEquals(Change.empty(), alpha.instance().change());
        assertEquals(Change.of(version1), beta.instance().change());

        tester.outstandingChangeDeployer().run();
        assertEquals(Change.of(version1).with(revision2.get()), beta.instance().change());

        beta.triggerJobs();
        tester.runner().run();
        beta.triggerJobs();

        beta.assertRunning(productionUsEast3);
        beta.assertNotRunning(testUsEast3);

        beta.runJob(productionUsEast3)
                .runJob(testUsEast3);

        assertEquals(Change.empty(), beta.instance().change());
    }

    @Test
    void testMultipleInstances() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .instances("instance1,instance2")
                .region("us-east-3")
                .build();
        var app = tester.newDeploymentContext("tenant1", "application1", "instance1")
                .submit(applicationPackage)
                .completeRollout();
        assertEquals(2, app.application().instances().size());
        assertEquals(2, app.application().productionDeployments().values().stream()
                .mapToInt(Collection::size)
                .sum());
    }

    @Test
    void testDeclaredProductionTests() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .region("us-east-3")
                .delay(Duration.ofMinutes(1))
                .test("us-east-3")
                .region("us-west-1")
                .region("us-central-1")
                .test("us-central-1")
                .test("us-west-1")
                .region("eu-west-1")
                .build();
        var app = tester.newDeploymentContext().submit(applicationPackage);

        app.runJob(systemTest).runJob(stagingTest).runJob(productionUsEast3);
        app.assertNotRunning(productionUsWest1);

        tester.clock().advance(Duration.ofMinutes(1));
        app.runJob(testUsEast3)
           .runJob(productionUsWest1).runJob(productionUsCentral1)
           .runJob(testUsCentral1).runJob(testUsWest1)
           .runJob(productionEuWest1);
        assertEquals(Change.empty(), app.instance().change());

        // Application starts upgrade, but confidence is broken after first zone. Tests won't run.
        Version version0 = app.application().oldestDeployedPlatform().get();
        Version version1 = Version.fromString("6.7");
        Version version2 = Version.fromString("6.8");
        tester.controllerTester().upgradeSystem(version1);
        tester.upgrader().maintain();
        tester.newDeploymentContext("keep", "version1", "alive").submit().deploy();

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
        tester.deploymentTrigger().forceChange(app.instanceId(), Change.of(version0).withPlatformPin());
        app.runJob(stagingTest).runJob(productionUsEast3);
        tester.clock().advance(Duration.ofMinutes(1));
        app.failDeployment(testUsEast3);
        tester.clock().advance(Duration.ofMinutes(11)); // Job is cooling down after consecutive failures.
        app.runJob(testUsEast3);
        assertEquals(Change.empty().withPlatformPin(), app.instance().change());

        // A new upgrade is attempted, and production tests wait for redeployment.
        tester.controllerTester().upgradeSystem(version2);
        tester.deploymentTrigger().cancelChange(app.instanceId(), ALL);

        tester.upgrader().overrideConfidence(version1, VespaVersion.Confidence.high);
        tester.controllerTester().computeVersionStatus();
        tester.upgrader().maintain(); // App should target version2.
        assertEquals(Change.of(version2), app.instance().change());

        // App partially upgrades to version2.
        app.runJob(systemTest).runJob(stagingTest);
        app.triggerJobs();
        app.assertRunning(productionUsEast3);
        app.assertNotRunning(testUsEast3);
        app.runJob(productionUsEast3);
        tester.clock().advance(Duration.ofMinutes(1));
        app.runJob(testUsEast3).runJob(productionUsWest1).triggerJobs();
        app.assertRunning(productionUsCentral1);
        tester.runner().run();
        app.triggerJobs();
        app.assertNotRunning(testUsCentral1);
        app.assertNotRunning(testUsWest1);

        // Version2 gets broken, but Version1 has high confidence now, and is the new target.
        // Since us-east-3 is already on Version2, both deployment and tests to it should be skipped.
        tester.upgrader().overrideConfidence(version2, VespaVersion.Confidence.broken);
        tester.controllerTester().computeVersionStatus();
        tester.upgrader().maintain(); // App should target version2.
        assertEquals(Change.of(version1), app.instance().change());
        app.triggerJobs();

        // Deployment to 6.8 already happened, so a downgrade to 6.7 won't, but production tests will still run.
        app.timeOutConvergence(productionUsCentral1);
        app.runJob(testUsCentral1).runJob(testUsWest1).runJob(productionEuWest1);
        assertEquals(version1, app.instance().deployments().get(ZoneId.from("prod.eu-west-1")).version());
    }

    @Test
    void testDeployComplicatedDeploymentSpec() {
        String complicatedDeploymentSpec =
                """
                <deployment version='1.0' athenz-domain='domain' athenz-service='service'>
                    <instance id='dev'>
                      <dev />
                    </instance>
                    <parallel>
                        <instance id='instance' athenz-service='in-service'>
                            <staging />
                            <prod>
                                <parallel>
                                    <region active='true'>us-west-1</region>
                                    <steps>
                                        <region active='true'>us-east-3</region>
                                        <delay hours='2' />
                                        <region active='true'>eu-west-1</region>
                                        <delay hours='2' />
                                    </steps>
                                    <steps>
                                        <delay hours='3' />
                                        <region active='true'>us-central-1</region>
                                        <parallel>
                                            <region active='true' athenz-service='no-service'>ap-northeast-1</region>
                                            <region active='true'>ap-northeast-2</region>
                                            <test>us-central-1</test>
                                        </parallel>
                                    </steps>
                                    <delay hours='3' minutes='30' />
                                </parallel>
                                <parallel>
                                   <test>ap-northeast-2</test>
                                   <test>ap-northeast-1</test>
                                </parallel>
                                <test>us-east-3</test>
                                <region active='true'>ap-southeast-1</region>
                            </prod>
                            <endpoints>
                                <endpoint id='foo' container-id='bar'>
                                    <region>us-east-3</region>
                                </endpoint>
                                <endpoint id='nalle' container-id='frosk' />
                                <endpoint container-id='quux' />
                            </endpoints>
                        </instance>
                        <instance id='other'>
                            <upgrade policy='conservative' />
                            <test />
                            <block-change revision='true' version='false' days='sat' hours='0-23' time-zone='CET' />
                            <prod>
                                <region active='true'>eu-west-1</region>
                                <test>eu-west-1</test>
                            </prod>
                            <notifications when='failing'>
                                <email role='author' />
                                <email address='john@dev' when='failing-commit' />
                                <email address='jane@dev' />
                            </notifications>
                        </instance>
                    </parallel>
                    <instance id='last'>
                        <upgrade policy='conservative' />
                        <prod>
                            <region active='true'>eu-west-1</region>
                        </prod>
                    </instance>
                </deployment>
                """;

        tester.atMondayMorning();
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
        assertEquals(3, tester.jobs().active().size());
        app1.runJob(productionUsEast3);
        tester.triggerJobs();
        assertEquals(2, tester.jobs().active().size());

        tester.clock().advance(Duration.ofHours(2));

        app1.runJob(productionEuWest1);
        tester.triggerJobs();
        assertEquals(2, tester.jobs().active().size());
        app2.assertNotRunning(testEuWest1);
        app2.runJob(productionEuWest1);
        tester.triggerJobs();
        assertEquals(2, tester.jobs().active().size());
        app2.runJob(testEuWest1);
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size());

        tester.clock().advance(Duration.ofHours(1));
        app1.runJob(productionUsCentral1);
        tester.triggerJobs();
        assertEquals(4, tester.jobs().active().size());
        app1.runJob(testUsCentral1);
        tester.triggerJobs();
        assertEquals(3, tester.jobs().active().size());
        app1.runJob(productionApNortheast2);
        tester.triggerJobs();
        assertEquals(2, tester.jobs().active().size());
        app1.runJob(productionApNortheast1);
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size());

        tester.clock().advance(Duration.ofMinutes(30));
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size());

        tester.clock().advance(Duration.ofMinutes(30));
        app1.runJob(testApNortheast1);
        tester.triggerJobs();
        assertEquals(2, tester.jobs().active().size());
        app1.runJob(testApNortheast2);
        tester.triggerJobs();
        assertEquals(2, tester.jobs().active().size());
        app1.runJob(testUsEast3);
        tester.triggerJobs();
        assertEquals(2, tester.jobs().active().size());
        app1.runJob(productionApSoutheast1);
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size());
        app3.runJob(productionEuWest1);
        tester.triggerJobs();
        assertEquals(List.of(), tester.jobs().active());

        tester.atMondayMorning().clock().advance(Duration.ofDays(5)); // Inside revision block window for second, conservative instance.
        Version version = Version.fromString("6.2");
        tester.controllerTester().upgradeSystem(version);
        tester.upgrader().maintain();
        assertEquals(Change.of(version), app1.instance().change());
        assertEquals(Change.empty(), app2.instance().change());
        assertEquals(Change.empty(), app3.instance().change());

        // Upgrade instance 1; upgrade rolls out first, with revision following.
        // The new platform won't roll out to the conservative instance until the normal one is upgraded.
        app1.submit(applicationPackage);
        assertEquals(Change.of(version).with(app1.application().revisions().last().get().id()), app1.instance().change());
        // Upgrade platform.
        app2.runJob(systemTest);
        app1.runJob(stagingTest)
                .runJob(productionUsWest1)
                .runJob(productionUsEast3);
        // Upgrade revision
        tester.clock().advance(Duration.ofSeconds(1)); // Ensure we see revision as rolling after upgrade.
        app2.runJob(systemTest);        // R
        app1.runJob(stagingTest)        // R
                .runJob(productionUsWest1); // R
        // productionUsEast3 won't change revision before its production test has completed for the upgrade, which is one of the last jobs!
        tester.clock().advance(Duration.ofHours(2));
        app1.runJob(productionEuWest1);
        tester.clock().advance(Duration.ofHours(1));
        app1.runJob(productionUsCentral1);
        app1.runJob(testUsCentral1);
        tester.clock().advance(Duration.ofSeconds(1));
        app1.runJob(productionUsCentral1); // R
        app1.runJob(testUsCentral1);       // R
        app1.runJob(productionApNortheast2);
        app1.runJob(productionApNortheast1);
        tester.clock().advance(Duration.ofHours(1));
        app1.runJob(testApNortheast1);
        app1.runJob(testApNortheast2);
        app1.runJob(productionApNortheast2); // R
        app1.runJob(productionApNortheast1); // R
        app1.runJob(testUsEast3);
        app1.runJob(productionApSoutheast1);
        tester.clock().advance(Duration.ofSeconds(1));
        app1.runJob(productionUsEast3);      // R
        tester.clock().advance(Duration.ofHours(2));
        app1.runJob(productionEuWest1);      // R
        tester.clock().advance(Duration.ofMinutes(330));
        app1.runJob(testApNortheast1);       // R
        app1.runJob(testApNortheast2);       // R
        app1.runJob(testUsEast3);            // R
        app1.runJob(productionApSoutheast1); // R

        app1.runJob(stagingTest);   // Tests with only the outstanding application change.
        app2.runJob(systemTest);    // Tests with only the outstanding application change.

        // Confidence rises to 'high', for the new version, and instance 2 starts to upgrade.
        tester.controllerTester().computeVersionStatus();
        tester.upgrader().maintain();
        tester.outstandingChangeDeployer().run();
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size(), tester.jobs().active().toString());
        assertEquals(Change.empty(), app1.instance().change());
        assertEquals(Change.of(version), app2.instance().change());
        assertEquals(Change.empty(), app3.instance().change());

        app2.runJob(productionEuWest1)
                .failDeployment(testEuWest1);

        // Instance 2 failed the last job, and now exits block window, letting application change roll out with the upgrade.
        tester.clock().advance(Duration.ofDays(1)); // Leave block window for revisions.
        tester.upgrader().maintain();
        tester.outstandingChangeDeployer().run();
        assertEquals(0, tester.jobs().active().size());
        tester.triggerJobs();
        assertEquals(1, tester.jobs().active().size());
        assertEquals(Change.empty(), app1.instance().change());
        assertEquals(Change.of(version).with(app1.application().revisions().last().get().id()), app2.instance().change());

        app2.runJob(productionEuWest1)
                .runJob(testEuWest1);
        assertEquals(Change.empty(), app2.instance().change());
        assertEquals(Change.empty(), app3.instance().change());

        // Two first instances upgraded and with new revision — last instance gets both changes as well.
        tester.upgrader().maintain();
        tester.outstandingChangeDeployer().run();
        assertEquals(Change.of(version).with(app1.lastSubmission().get()), app3.instance().change());

        tester.deploymentTrigger().cancelChange(app3.instanceId(), ALL);
        tester.outstandingChangeDeployer().run();
        tester.upgrader().maintain();
        assertEquals(Change.of(app1.lastSubmission().get()), app3.instance().change());

        app3.runJob(productionEuWest1);
        tester.upgrader().maintain();
        app1.runJob(stagingTest);
        app3.runJob(productionEuWest1);
        tester.triggerJobs();
        assertEquals(List.of(), tester.jobs().active());
        assertEquals(Change.empty(), app3.instance().change());
    }

    @Test
    void testRevisionJoinsUpgradeWithSeparateRollout() {
        var appPackage = new ApplicationPackageBuilder().region("us-central-1")
                .region("us-east-3")
                .region("us-west-1")
                .upgradeRollout("separate")
                .build();
        var app = tester.newDeploymentContext().submit(appPackage).deploy();

        // Platform rolls through first production zone.
        var version0 = tester.controller().readSystemVersion();
        var version1 = new Version("6.2");
        tester.controllerTester().upgradeSystem(version1);
        tester.upgrader().maintain();
        app.runJob(systemTest).runJob(stagingTest).runJob(productionUsCentral1);
        tester.clock().advance(Duration.ofMinutes(1));

        // Revision starts rolling, but stays behind.
        var revision0 = app.lastSubmission();
        app.submit(appPackage);
        var revision1 = app.lastSubmission();
        assertEquals(Change.of(version1).with(revision1.get()), app.instance().change());
        app.runJob(systemTest).runJob(stagingTest).runJob(productionUsCentral1);

        // Upgrade got here first, so attempts to proceed alone, but the upgrade fails.
        app.triggerJobs();
        assertEquals(new Versions(version1, revision0.get(), Optional.of(version0), revision0),
                tester.jobs().last(app.instanceId(), productionUsEast3).get().versions());
        app.timeOutConvergence(productionUsEast3);

        // Revision is allowed to join.
        app.triggerJobs();
        assertEquals(new Versions(version1, revision1.get(), Optional.of(version1), revision0),
                tester.jobs().last(app.instanceId(), productionUsEast3).get().versions());
        app.runJob(productionUsEast3);

        // Platform and revision now proceed together.
        app.runJob(stagingTest);
        app.triggerJobs();
        assertEquals(new Versions(version1, revision1.get(), Optional.of(version0), revision0),
                tester.jobs().last(app.instanceId(), productionUsWest1).get().versions());
        app.runJob(productionUsWest1);
        assertEquals(Change.empty(), app.instance().change());

        // New upgrade fails in staging-test, and revision to fix it is submitted.
        var version2 = new Version("6.3");
        tester.controllerTester().upgradeSystem(version2);
        tester.upgrader().maintain();
        app.runJob(systemTest).failDeployment(stagingTest);
        tester.clock().advance(Duration.ofMinutes(30));
        app.failDeployment(stagingTest);
        app.submit(appPackage);

        app.runJob(systemTest).runJob(stagingTest) // Tests run with combined upgrade.
                .runJob(productionUsCentral1)           // Combined upgrade stays together.
                .runJob(productionUsEast3).runJob(productionUsWest1);
        assertEquals(Map.of(), app.deploymentStatus().jobsToRun());
        assertEquals(Change.empty(), app.instance().change());
    }

    @Test
    void testProductionTestBlockingDeploymentWithSeparateRollout() {
        var appPackage = new ApplicationPackageBuilder().region("us-east-3")
                .region("us-west-1")
                .delay(Duration.ofHours(1))
                .test("us-east-3")
                .upgradeRollout("separate")
                .build();
        var app = tester.newDeploymentContext().submit(appPackage)
                .runJob(systemTest).runJob(stagingTest)
                .runJob(productionUsEast3).runJob(productionUsWest1);
        tester.clock().advance(Duration.ofHours(1));
        app.runJob(testUsEast3);
        assertEquals(Change.empty(), app.instance().change());

        // Platform rolls through first production zone.
        var version0 = tester.controller().readSystemVersion();
        var version1 = new Version("6.2");
        tester.controllerTester().upgradeSystem(version1);
        tester.upgrader().maintain();
        app.runJob(systemTest).runJob(stagingTest).runJob(productionUsEast3);

        // Revision starts rolling, but waits for production test to verify the upgrade.
        var revision0 = app.lastSubmission();
        app.submit(appPackage);
        var revision1 = app.lastSubmission();
        assertEquals(Change.of(version1).with(revision1.get()), app.instance().change());
        app.runJob(systemTest).runJob(stagingTest).triggerJobs();
        app.assertRunning(productionUsWest1);
        app.assertNotRunning(productionUsEast3);

        // Upgrade got here first, so attempts to proceed alone, but the upgrade fails.
        app.triggerJobs();
        assertEquals(new Versions(version1, revision0.get(), Optional.of(version0), revision0),
                tester.jobs().last(app.instanceId(), productionUsWest1).get().versions());
        app.timeOutConvergence(productionUsWest1).triggerJobs();

        // Upgrade now fails between us-east-3 deployment and test, so test is abandoned, and revision unblocked.
        app.assertRunning(productionUsEast3);
        assertEquals(new Versions(version1, revision1.get(), Optional.of(version1), revision0),
                tester.jobs().last(app.instanceId(), productionUsEast3).get().versions());
        app.runJob(productionUsEast3).triggerJobs()
                .jobAborted(productionUsWest1).runJob(productionUsWest1);
        tester.clock().advance(Duration.ofHours(1));
        app.runJob(testUsEast3);
        assertEquals(Change.empty(), app.instance().change());
    }

    @Test
    void testProductionTestNotBlockingDeploymentWithSimultaneousRollout() {
        var appPackage = new ApplicationPackageBuilder().region("us-east-3")
                .region("us-central-1")
                .region("us-west-1")
                .delay(Duration.ofHours(1))
                .test("us-east-3")
                .test("us-west-1")
                .upgradeRollout("simultaneous")
                .build();
        var app = tester.newDeploymentContext().submit(appPackage)
                .runJob(systemTest).runJob(stagingTest)
                .runJob(productionUsEast3).runJob(productionUsCentral1).runJob(productionUsWest1);
        tester.clock().advance(Duration.ofHours(1));
        app.runJob(testUsEast3).runJob(testUsWest1);
        assertEquals(Change.empty(), app.instance().change());

        // Platform rolls through first production zone.
        var version0 = tester.controller().readSystemVersion();
        var version1 = new Version("6.2");
        tester.controllerTester().upgradeSystem(version1);
        tester.upgrader().maintain();
        app.runJob(systemTest).runJob(stagingTest).runJob(productionUsEast3);

        // Revision starts rolling, and causes production test to abort when it reaches deployment.
        var revision0 = app.lastSubmission();
        app.submit(appPackage);
        var revision1 = app.lastSubmission();
        assertEquals(Change.of(version1).with(revision1.get()), app.instance().change());
        app.runJob(systemTest).runJob(stagingTest).triggerJobs();
        app.assertRunning(productionUsCentral1);
        app.assertRunning(productionUsEast3);

        // Revision deploys to first prod zone.
        app.triggerJobs();
        assertEquals(new Versions(version1, revision1.get(), Optional.of(version1), revision0),
                tester.jobs().last(app.instanceId(), productionUsEast3).get().versions());
        tester.clock().advance(Duration.ofSeconds(1));
        app.runJob(productionUsEast3);

        // Revision catches up in second prod zone.
        app.runJob(systemTest).runJob(stagingTest).runJob(stagingTest).triggerJobs();
        app.jobAborted(productionUsCentral1).triggerJobs();
        assertEquals(new Versions(version1, revision1.get(), Optional.of(version0), revision0),
                tester.jobs().last(app.instanceId(), productionUsCentral1).get().versions());
        app.runJob(productionUsCentral1).triggerJobs();

        // Revision proceeds alone in third prod zone, making test targets different for the two prod tests.
        assertEquals(new Versions(version0, revision1.get(), Optional.of(version0), revision0),
                tester.jobs().last(app.instanceId(), productionUsWest1).get().versions());
        app.runJob(productionUsWest1);
        app.triggerJobs();
        app.assertNotRunning(testUsEast3);
        tester.clock().advance(Duration.ofHours(1));

        // Test lets revision proceed alone, and us-west-1 is blocked until tested.
        app.runJob(testUsEast3).triggerJobs();
        app.assertNotRunning(productionUsWest1);
        app.runJob(testUsWest1).runJob(productionUsWest1).runJob(testUsWest1); // Test for us-east-3 is not re-run.
        assertEquals(Change.empty(), app.instance().change());
    }

    @Test
    void testVeryLengthyPipelineRevisions() {
        String lengthyDeploymentSpec =
                """
                <deployment version='1.0'>
                    <instance id='alpha'>
                        <test />
                        <staging />
                        <upgrade revision-change='always' />
                        <prod>
                            <region>us-east-3</region>
                            <test>us-east-3</test>
                        </prod>
                    </instance>
                    <instance id='beta'>
                        <upgrade revision-change='when-failing' />
                        <prod>
                            <region>us-east-3</region>
                            <test>us-east-3</test>
                        </prod>
                    </instance>
                    <instance id='gamma'>
                        <upgrade revision-change='when-clear' revision-target='next' min-risk='3' max-risk='6' />
                        <prod>
                            <region>us-east-3</region>
                            <test>us-east-3</test>
                        </prod>
                    </instance>
                </deployment>
                """;
        var appPackage = ApplicationPackageBuilder.fromDeploymentXml(lengthyDeploymentSpec);
        var alpha = tester.newDeploymentContext("t", "a", "alpha");
        var beta  = tester.newDeploymentContext("t", "a", "beta");
        var gamma = tester.newDeploymentContext("t", "a", "gamma");
        alpha.submit(appPackage, 0).deploy();

        // revision2 is submitted, and rolls through alpha.
        var revision1 = alpha.lastSubmission();
        alpha.submit(appPackage, 3); // Risk high enough that this may roll out alone to gamma.
        var revision2 = alpha.lastSubmission();

        alpha.runJob(systemTest).runJob(stagingTest)
                .runJob(productionUsEast3).runJob(testUsEast3);
        assertEquals(Optional.empty(), alpha.instance().change().revision());

        // revision3 is submitted when revision2 is half-way.
        tester.outstandingChangeDeployer().run();
        beta.runJob(productionUsEast3);
        alpha.submit(appPackage, 2); // Will only roll out to gamma together with the next revision.
        var revision3 = alpha.lastSubmission();
        beta.runJob(testUsEast3);
        assertEquals(Optional.empty(), beta.instance().change().revision());

        // revision3 is the target for alpha, beta is done, revision2 is the target for gamma.
        tester.outstandingChangeDeployer().run();
        assertEquals(revision3, alpha.instance().change().revision());
        assertEquals(Optional.empty(), beta.instance().change().revision());
        assertEquals(revision2, gamma.instance().change().revision());

        // revision3 rolls to beta, then a couple of new revisions are submitted to alpha, and the latter is the new target.
        alpha.runJob(systemTest).runJob(stagingTest)
                .runJob(productionUsEast3).runJob(testUsEast3);
        tester.outstandingChangeDeployer().run();
        assertEquals(Optional.empty(), alpha.instance().change().revision());
        assertEquals(revision3, beta.instance().change().revision());

        // revision5 supersedes revision4
        alpha.submit(appPackage, 3);
        var revision4 = alpha.lastSubmission();
        alpha.runJob(systemTest).runJob(stagingTest)
                .runJob(productionUsEast3);
        alpha.submit(appPackage, 2);
        var revision5 = alpha.lastSubmission();
        alpha.runJob(systemTest).runJob(stagingTest)
                .runJob(productionUsEast3).runJob(testUsEast3);
        tester.outstandingChangeDeployer().run();
        assertEquals(Optional.empty(), alpha.instance().change().revision());
        assertEquals(revision3, beta.instance().change().revision());

        // revision6 rolls through alpha, and becomes the next target for beta, which also completes revision3.
        alpha.submit(appPackage, 6);
        var revision6 = alpha.lastSubmission();
        alpha.runJob(systemTest).runJob(stagingTest)
                .runJob(productionUsEast3)
                .runJob(testUsEast3);
        beta.runJob(productionUsEast3).runJob(testUsEast3);
        tester.outstandingChangeDeployer().run();
        assertEquals(Optional.empty(), alpha.instance().change().revision());
        assertEquals(revision6, beta.instance().change().revision());

        // revision 2 fails in gamma, but this does not bring on revision 3
        gamma.failDeployment(productionUsEast3);
        tester.outstandingChangeDeployer().run();
        assertEquals(revision2, gamma.instance().change().revision());

        // revision 2 completes in gamma
        gamma.runJob(productionUsEast3)
                .runJob(testUsEast3);
        tester.outstandingChangeDeployer().run();
        assertEquals(Optional.empty(), alpha.instance().change().revision());
        assertEquals(Optional.empty(), gamma.instance().change().revision()); // no other revisions after 3 are ready, so gamma waits

        // revision6 rolls through beta, and revision3 is the next target for gamma with "when-clear" change-revision, now that 6 is blocking 4 and 5
        alpha.jobAborted(stagingTest).runJob(stagingTest);
        beta.runJob(productionUsEast3).runJob(testUsEast3);
        assertEquals(Optional.empty(), beta.instance().change().revision());

        tester.outstandingChangeDeployer().run();
        assertEquals(Optional.empty(), alpha.instance().change().revision());
        assertEquals(Optional.empty(), beta.instance().change().revision());
        assertEquals(revision3, gamma.instance().change().revision()); // revision4 never became ready, but 5 did, so 4 is skipped, and 3 rolls out alone instead.

        // revision 6 is next, once 3 is done
        // revision 3 completes
        gamma.runJob(productionUsEast3)
                .runJob(testUsEast3);
        tester.outstandingChangeDeployer().run();
        assertEquals(revision6, gamma.instance().change().revision());

        // revision 7 becomes ready for gamma, but must wait for the idle time of 8 hours before being deployed
        alpha.submit(appPackage, 1);
        var revision7 = alpha.lastSubmission();
        alpha.deploy();
        tester.outstandingChangeDeployer();
        assertEquals(Change.empty(), gamma.instance().change());
        assertEquals(revision6.get(), gamma.deployment(ZoneId.from("prod.us-east-3")).revision());

        tester.clock().advance(Duration.ofHours(8));
        tester.outstandingChangeDeployer().run();
        assertEquals(revision7, gamma.instance().change().revision());

        // revision 8 is has too low risk to roll out on its own, but will start rolling immediately when revision 9 is submitted
        gamma.deploy();
        alpha.submit(appPackage, 2);
        var revision8 = alpha.lastSubmission();
        alpha.deploy();
        tester.outstandingChangeDeployer();
        assertEquals(Change.empty(), gamma.instance().change());
        assertEquals(revision7.get(), gamma.deployment(ZoneId.from("prod.us-east-3")).revision());

        alpha.submit(appPackage, 5);
        tester.outstandingChangeDeployer().run();
        assertEquals(revision8, gamma.instance().change().revision());
    }

    @Test
    void testVeryLengthyPipelineUpgrade() {
        String lengthyDeploymentSpec =
                """
                <deployment version='1.0'>
                    <instance id='alpha'>
                        <test />
                        <staging />
                        <upgrade rollout='simultaneous' />
                        <prod>
                            <region>us-east-3</region>
                            <test>us-east-3</test>
                        </prod>
                    </instance>
                    <instance id='beta'>
                        <upgrade rollout='simultaneous' />
                        <prod>
                            <region>us-east-3</region>
                            <test>us-east-3</test>
                        </prod>
                    </instance>
                    <instance id='gamma'>
                        <upgrade rollout='separate' />
                        <prod>
                            <region>us-east-3</region>
                            <test>us-east-3</test>
                        </prod>
                    </instance>
                </deployment>
                """;
        var appPackage = ApplicationPackageBuilder.fromDeploymentXml(lengthyDeploymentSpec);
        var alpha = tester.newDeploymentContext("t", "a", "alpha");
        var beta  = tester.newDeploymentContext("t", "a", "beta");
        var gamma = tester.newDeploymentContext("t", "a", "gamma");
        alpha.submit(appPackage).deploy();

        // A version releases, but when the first upgrade has gotten through alpha, beta, and gamma, a newer version has high confidence.
        var version0 = tester.controller().readSystemVersion();
        var version1 = new Version("6.2");
        var version2 = new Version("6.3");
        tester.controllerTester().upgradeSystem(version1);

        tester.upgrader().maintain();
        alpha.runJob(systemTest).runJob(stagingTest)
                .runJob(productionUsEast3).runJob(testUsEast3);
        assertEquals(Change.empty(), alpha.instance().change());

        tester.upgrader().maintain();
        beta.runJob(productionUsEast3);
        tester.controllerTester().upgradeSystem(version2);
        beta.runJob(testUsEast3);
        assertEquals(Change.empty(), beta.instance().change());

        tester.upgrader().maintain();
        assertEquals(Change.of(version2), alpha.instance().change());
        assertEquals(Change.empty(), beta.instance().change());
        assertEquals(Change.of(version1), gamma.instance().change());
    }

    @Test
    void testRevisionJoinsUpgradeWithLeadingRollout() {
        var appPackage = new ApplicationPackageBuilder().region("us-central-1")
                .region("us-east-3")
                .region("us-west-1")
                .upgradeRollout("leading")
                .build();
        var app = tester.newDeploymentContext().submit(appPackage).deploy();

        // Platform rolls through first production zone.
        var version0 = tester.controller().readSystemVersion();
        var version1 = new Version("6.2");
        tester.controllerTester().upgradeSystem(version1);
        tester.upgrader().maintain();
        app.runJob(systemTest).runJob(stagingTest).runJob(productionUsCentral1);
        tester.clock().advance(Duration.ofMinutes(1));

        // Revision starts rolling, and catches up.
        var revision0 = app.lastSubmission();
        app.submit(appPackage);
        var revision1 = app.lastSubmission();
        assertEquals(Change.of(version1).with(revision1.get()), app.instance().change());
        app.runJob(systemTest).runJob(stagingTest).runJob(productionUsCentral1);

        // Upgrade got here first, and has triggered, but is now obsolete.
        app.triggerJobs();
        assertEquals(new Versions(version1, revision0.get(), Optional.of(version0), revision0),
                tester.jobs().last(app.instanceId(), productionUsEast3).get().versions());
        assertEquals(RunStatus.running, tester.jobs().last(app.instanceId(), productionUsEast3).get().status());

        // Once staging tests verify the joint upgrade, the job is replaced with that.
        app.runJob(stagingTest);
        app.triggerJobs();
        app.jobAborted(productionUsEast3).runJob(productionUsEast3);
        assertEquals(new Versions(version1, revision1.get(), Optional.of(version0), revision0),
                tester.jobs().last(app.instanceId(), productionUsEast3).get().versions());

        // Platform and revision now proceed together.
        app.triggerJobs();
        assertEquals(new Versions(version1, revision1.get(), Optional.of(version0), revision0),
                tester.jobs().last(app.instanceId(), productionUsWest1).get().versions());
        app.runJob(productionUsWest1);
        assertEquals(Change.empty(), app.instance().change());
    }

    @Test
    void testRevisionPassesUpgradeWithSimultaneousRollout() {
        var appPackage = new ApplicationPackageBuilder().region("us-central-1")
                .region("us-east-3")
                .region("us-west-1")
                .upgradeRollout("simultaneous")
                .build();
        var app = tester.newDeploymentContext().submit(appPackage).deploy();

        // Platform rolls through first production zone.
        var version0 = tester.controller().readSystemVersion();
        var version1 = new Version("6.2");
        tester.controllerTester().upgradeSystem(version1);
        tester.upgrader().maintain();
        app.runJob(systemTest).runJob(stagingTest).runJob(productionUsCentral1);
        tester.clock().advance(Duration.ofMinutes(1));

        // Revision starts rolling, and catches up.
        var revision0 = app.lastSubmission();
        app.submit(appPackage);
        var revision1 = app.lastSubmission();
        assertEquals(Change.of(version1).with(revision1.get()), app.instance().change());
        app.runJob(systemTest).runJob(stagingTest).runJob(productionUsCentral1);

        // Upgrade got here first, and has triggered, but is now obsolete.
        app.triggerJobs();
        app.assertRunning(productionUsEast3);
        assertEquals(new Versions(version1, revision0.get(), Optional.of(version0), revision0),
                tester.jobs().last(app.instanceId(), productionUsEast3).get().versions());
        assertEquals(RunStatus.running, tester.jobs().last(app.instanceId(), productionUsEast3).get().status());

        // Once staging tests verify the joint upgrade, the job is replaced with that.
        app.runJob(systemTest).runJob(stagingTest).runJob(stagingTest);
        app.triggerJobs();
        app.jobAborted(productionUsEast3).runJob(productionUsEast3);
        assertEquals(new Versions(version1, revision1.get(), Optional.of(version0), revision0),
                tester.jobs().last(app.instanceId(), productionUsEast3).get().versions());

        // Revision now proceeds alone.
        app.triggerJobs();
        assertEquals(new Versions(version0, revision1.get(), Optional.of(version0), revision0),
                tester.jobs().last(app.instanceId(), productionUsWest1).get().versions());
        app.runJob(productionUsWest1);

        // Upgrade follows.
        app.triggerJobs();
        assertEquals(new Versions(version1, revision1.get(), Optional.of(version0), revision1),
                tester.jobs().last(app.instanceId(), productionUsWest1).get().versions());
        app.runJob(productionUsWest1);
        assertEquals(Change.empty(), app.instance().change());
    }

    @Test
    void mixedDirectAndPipelineJobsInProduction() {
        ApplicationPackage cdPackage = new ApplicationPackageBuilder().region("us-east-3")
                                                                      .region("aws-us-east-1a")
                                                                      .build();
        ControllerTester wrapped = new ControllerTester(cd);
        wrapped.upgradeSystem(Version.fromString("6.1"));
        wrapped.computeVersionStatus();

        DeploymentTester tester = new DeploymentTester(wrapped);
        var app = tester.newDeploymentContext();

        app.runJob(productionUsEast3, cdPackage);
        app.submit(cdPackage);
        app.runJob(systemTest);
        // Staging test requires unknown initial version, and is broken.
        tester.controller().applications().deploymentTrigger().forceTrigger(app.instanceId(), productionUsEast3, "user", false, true, true);
        app.runJob(productionUsEast3)
                .abortJob(stagingTest) // Complete failing run.
                .runJob(stagingTest)   // Run staging-test for production zone with no prior deployment.
                .runJob(productionAwsUsEast1a);

        // Manually deploy to east again, then upgrade the system.
        app.runJob(productionUsEast3, cdPackage);
        var version = new Version("6.2");
        tester.controllerTester().upgradeSystem(version);
        tester.upgrader().maintain();
        // System and staging tests both require unknown versions, and are broken.
        tester.controller().applications().deploymentTrigger().forceTrigger(app.instanceId(), productionUsEast3, "user", false, true, true);
        app.runJob(productionUsEast3)
                .triggerJobs()
                .jobAborted(systemTest)
                .jobAborted(stagingTest)
                .runJob(systemTest)  // Run test for aws zone again.
                .runJob(stagingTest) // Run test for aws zone again.
                .runJob(productionAwsUsEast1a);

        // Deploy manually again, then submit new package.
        app.runJob(productionUsEast3, cdPackage);
        app.submit(cdPackage);
        app.triggerJobs().runJob(systemTest);
        // Staging test requires unknown initial version, and is broken.
        tester.controller().applications().deploymentTrigger().forceTrigger(app.instanceId(), productionUsEast3, "user", false, true, true);
        app.runJob(productionUsEast3)
                .jobAborted(stagingTest)
                .runJob(stagingTest)
                .runJob(productionAwsUsEast1a);
    }

    @Test
    void testsInSeparateInstance() {
        String deploymentSpec =
                """
                <deployment version='1.0' athenz-domain='domain' athenz-service='service'>
                    <instance id='canary'>
                        <upgrade policy='canary' />
                        <test />
                        <staging />
                    </instance>
                    <instance id='default'>
                        <prod>
                            <region>eu-west-1</region>
                            <test>eu-west-1</test>
                        </prod>
                    </instance>
                </deployment>
                """;

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

        tester.controllerTester().upgradeSystem(new Version("6.7.7"));
        tester.upgrader().maintain();

        canary.runJob(systemTest)
              .runJob(stagingTest);
        tester.upgrader().maintain();
        conservative.runJob(productionEuWest1)
                    .runJob(testEuWest1);

    }

    @Test
    void testEagerTests() {
        var app = tester.newDeploymentContext().submit().deploy();

        // Start upgrade, then receive new submission.
        Version version1 = new Version("6.8.9");
        RevisionId build1 = app.lastSubmission().get();
        tester.controllerTester().upgradeSystem(version1);
        tester.upgrader().maintain();
        app.runJob(stagingTest);
        app.submit();
        RevisionId build2 = app.lastSubmission().get();
        assertNotEquals(build1, build2);

        // App now free to start system tests eagerly, for new submission. These should run assuming upgrade succeeds.
        tester.triggerJobs();
        app.assertRunning(stagingTest);
        assertEquals(version1,
                app.instanceJobs().get(stagingTest).lastCompleted().get().versions().targetPlatform());
        assertEquals(build1,
                app.instanceJobs().get(stagingTest).lastCompleted().get().versions().targetRevision());

        assertEquals(version1,
                app.instanceJobs().get(stagingTest).lastTriggered().get().versions().sourcePlatform().get());
        assertEquals(build1,
                app.instanceJobs().get(stagingTest).lastTriggered().get().versions().sourceRevision().get());
        assertEquals(version1,
                app.instanceJobs().get(stagingTest).lastTriggered().get().versions().targetPlatform());
        assertEquals(build2,
                app.instanceJobs().get(stagingTest).lastTriggered().get().versions().targetRevision());

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
    void testTriggeringOfIdleTestJobsWhenFirstDeploymentIsOnNewerVersionThanChange() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder().systemTest()
                .stagingTest()
                .region("us-east-3")
                .region("us-west-1")
                .build();
        var app = tester.newDeploymentContext().submit(applicationPackage).deploy();
        var appToAvoidVersionGC = tester.newDeploymentContext("g", "c", "default").submit().deploy();

        Version version2 = new Version("6.8.9");
        Version version3 = new Version("6.9.10");
        tester.controllerTester().upgradeSystem(version2);
        tester.deploymentTrigger().forceChange(appToAvoidVersionGC.instanceId(), Change.of(version2));
        appToAvoidVersionGC.deployPlatform(version2);

        // app upgrades first zone to version3, and then the other two to version2.
        tester.controllerTester().upgradeSystem(version3);
        tester.deploymentTrigger().forceChange(app.instanceId(), Change.of(version3));
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

    @Test
    void testRetriggerQueue() {
        var app = tester.newDeploymentContext().submit().deploy();
        app.submit();
        tester.triggerJobs();

        tester.deploymentTrigger().reTrigger(app.instanceId(), productionUsEast3, null);
        tester.deploymentTrigger().reTriggerOrAddToQueue(app.deploymentIdIn(ZoneId.from("prod", "us-east-3")), null);
        tester.deploymentTrigger().reTriggerOrAddToQueue(app.deploymentIdIn(ZoneId.from("prod", "us-east-3")), null);

        List<RetriggerEntry> retriggerEntries = tester.controller().curator().readRetriggerEntries();
        assertEquals(1, retriggerEntries.size());
    }

    @Test
    void testOrchestrationWithIncompatibleVersionPairs() {
        Version version1 = new Version("7");
        Version version2 = new Version("8");
        Version version3 = new Version("8.1");
        tester.controllerTester().flagSource().withListFlag(PermanentFlags.INCOMPATIBLE_VERSIONS.id(), List.of("8"), String.class);

        // App deploys on version1.
        tester.controllerTester().upgradeSystem(version1);
        DeploymentContext app = tester.newDeploymentContext()
                .submit(new ApplicationPackageBuilder().region("us-east-3")
                                                       .compileVersion(version1)
                                                       .build())
                .deploy();

        // System upgrades to version2, and then version3, but the app is not upgraded.
        tester.controllerTester().upgradeSystem(version2);
        tester.upgrader().run();
        assertEquals(Change.empty(), app.instance().change());
        tester.newDeploymentContext("some", "other", "app")
              .submit(new ApplicationPackageBuilder().region("us-east-3")
                                                     .compileVersion(version2)
                                                     .build())
              .deploy();

        tester.controllerTester().upgradeSystem(version3);
        tester.upgrader().run();
        assertEquals(Change.empty(), app.instance().change());

        // App compiles against version2, but confidence is broken for the version on new major before app has time to upgrade.
        app.submit(new ApplicationPackageBuilder().region("us-east-3")
                .compileVersion(version2)
                .build());
        tester.upgrader().overrideConfidence(version2, Confidence.normal);
        tester.upgrader().overrideConfidence(version3, Confidence.broken);
        tester.controllerTester().computeVersionStatus();
        tester.upgrader().run();
        tester.outstandingChangeDeployer().run();

        // App instead deploys to version2.
        app.deploy();
        assertEquals(version2, tester.jobs().last(app.instanceId(), productionUsEast3).get().versions().targetPlatform());
        assertEquals(version2, app.application().revisions().get(tester.jobs().last(app.instanceId(), productionUsEast3).get().versions().targetRevision()).compileVersion().get());

        // App specifies version1 in deployment spec, compiles against version1, pins to version1, and then downgrades.
        app.submit(new ApplicationPackageBuilder().region("us-east-3")
                .majorVersion(7)
                .compileVersion(version1)
                .build());
        tester.deploymentTrigger().forceChange(app.instanceId(), app.instance().change().withPlatformPin());
        app.deploy();
        assertEquals(version1, tester.jobs().last(app.instanceId(), productionUsEast3).get().versions().targetPlatform());
        assertEquals(version1, app.application().revisions().get(tester.jobs().last(app.instanceId(), productionUsEast3).get().versions().targetRevision()).compileVersion().get());

        // A new app, compiled against version1, is deployed on version1.
        DeploymentContext newApp = tester.newDeploymentContext("new", "app", "default")
                .submit(new ApplicationPackageBuilder().region("us-east-3")
                        .compileVersion(version1)
                        .build())
                .deploy();
        assertEquals(version1, tester.jobs().last(newApp.instanceId(), productionUsEast3).get().versions().targetPlatform());
        assertEquals(version1, newApp.application().revisions().get(tester.jobs().last(newApp.instanceId(), productionUsEast3).get().versions().targetRevision()).compileVersion().get());

        // The new app enters a platform block window, and is pinned to the old platform;
        // the new submission overrides both those settings, as the new revision should roll out regardless.
        tester.atMondayMorning();
        tester.deploymentTrigger().forceChange(newApp.instanceId(), Change.empty().withPlatformPin());
        newApp.submit(new ApplicationPackageBuilder().compileVersion(version2)
                                                     .systemTest()
                                                     .blockChange(false, true, "mon", "0-23", "UTC")
                                                     .region("us-east-3")
                                                     .build());
        RevisionId newRevision = newApp.lastSubmission().get();

        assertEquals(Change.of(newRevision).with(version2), newApp.instance().change());
        newApp.deploy();
        assertEquals(version2, tester.jobs().last(newApp.instanceId(), productionUsEast3).get().versions().targetPlatform());
        assertEquals(version2, newApp.application().revisions().get(tester.jobs().last(newApp.instanceId(), productionUsEast3).get().versions().targetRevision()).compileVersion().get());

        // New app compiles against old major, and downgrades when a pin is also applied.
        newApp.submit(new ApplicationPackageBuilder().compileVersion(version1)
                                                     .systemTest()
                                                     .region("us-east-3")
                                                     .build());
        newRevision = newApp.lastSubmission().get();

        assertEquals(Change.of(newRevision).with(version1), newApp.instance().change());
        tester.triggerJobs();
        newApp.assertNotRunning(systemTest); // Without a pin, the platform won't downgrade, and 8 is incompatible with compiled 7.

        tester.outstandingChangeDeployer().run();
        assertEquals(Change.of(newRevision).with(version1), newApp.instance().change());
        tester.upgrader().run();
        assertEquals(Change.of(newRevision).with(version1), newApp.instance().change());

        tester.deploymentTrigger().forceChange(newApp.instanceId(), newApp.instance().change().withPlatformPin());
        tester.outstandingChangeDeployer().run();
        assertEquals(Change.of(newRevision).with(version1).withPlatformPin(), newApp.instance().change());
        tester.upgrader().run();
        assertEquals(Change.of(newRevision).with(version1).withPlatformPin(), newApp.instance().change());

        newApp.deploy();
        assertEquals(version1, tester.jobs().last(newApp.instanceId(), productionUsEast3).get().versions().targetPlatform());
        assertEquals(version1, newApp.application().revisions().get(tester.jobs().last(newApp.instanceId(), productionUsEast3).get().versions().targetRevision()).compileVersion().get());
    }

    @Test
    void testOutdatedMajorIsIllegal() {
        Version version0 = new Version("6.2");
        Version version1 = new Version("7.1");
        tester.controllerTester().upgradeSystem(version0);
        DeploymentContext old = tester.newDeploymentContext("t", "a", "default").submit()
                                      .runJob(systemTest).runJob(stagingTest).runJob(productionUsCentral1);
        old.runJob(JobType.dev("us-east-1"), applicationPackage());

        tester.controllerTester().upgradeSystem(version1);
        tester.upgrader().overrideConfidence(version1, Confidence.high);
        tester.controllerTester().computeVersionStatus();

        // New app can't deploy to 6.2
        DeploymentContext app = tester.newDeploymentContext("t", "b", "default");
        assertEquals("platform version 6.2 is not on a current major version in this system",
                     assertThrows(IllegalArgumentException.class,
                                  () -> tester.jobs().deploy(app.instanceId(),
                                                             JobType.dev("us-east-1"),
                                                             Optional.of(version0),
                                                             DeploymentContext.applicationPackage()))
                             .getMessage());

        // App which already deployed to 6.2 can still do so.
        tester.jobs().deploy(old.instanceId(),
                             JobType.dev("us-east-1"),
                             Optional.of(version0),
                             DeploymentContext.applicationPackage());

        app.submit();
        assertEquals("platform version 6.2 is not on a current major version in this system",
                     assertThrows(IllegalArgumentException.class,
                                  () -> tester.deploymentTrigger().forceChange(app.instanceId(), Change.of(version0), false))
                             .getMessage());

       tester.deploymentTrigger().forceChange(old.instanceId(), Change.of(version0), false);
       tester.deploymentTrigger().cancelChange(old.instanceId(), ALL);

        // Not even version incompatibility tricks the system.
        tester.controllerTester().flagSource().withListFlag(PermanentFlags.INCOMPATIBLE_VERSIONS.id(), List.of("7"), String.class);
        assertEquals("compile version 6.2 is incompatible with the current major version of this system",
                     assertThrows(IllegalArgumentException.class,
                                  () ->
                                          app.submit(new ApplicationPackageBuilder().region("us-central-1").region("us-east-3").region("us-west-1")
                                                                                    .compileVersion(version0)
                                                                                    .build()))
                             .getMessage());

        // Submit new revision on old major
        old.submit(new ApplicationPackageBuilder().region("us-central-1").region("us-east-3").region("us-west-1")
                                                  .compileVersion(version0)
                                                  .build())
           .deploy();

        // Upgrade.
        old.submit(new ApplicationPackageBuilder().region("us-central-1").region("us-east-3").region("us-west-1")
                                                  .compileVersion(version1)
                                                  .build())
           .deploy();

        // And downgrade again.
        old.submit(new ApplicationPackageBuilder().region("us-central-1").region("us-east-3").region("us-west-1")
                                                  .compileVersion(version0)
                                                  .build());

        assertEquals(Change.of(version0).with(old.lastSubmission().get()), old.instance().change());

        // An operator can still trigger roll-out of the otherwise illegal submission.
        tester.deploymentTrigger().forceChange(app.instanceId(), Change.of(app.lastSubmission().get()));
        assertEquals(Change.of(app.lastSubmission().get()), app.instance().change());
    }

    @Test
    void operatorMayForceUnknownVersion() {
        Version oldVersion = Version.fromString("6");
        Version currentVersion = Version.fromString("7");
        tester.controllerTester().flagSource().withListFlag(PermanentFlags.INCOMPATIBLE_VERSIONS.id(), List.of("7"), String.class);
        tester.controllerTester().upgradeSystem(currentVersion);
        assertEquals(List.of(currentVersion),
                     tester.controller().readVersionStatus().versions().stream().map(VespaVersion::versionNumber).toList());

        DeploymentContext app = tester.newDeploymentContext();
        assertEquals("compile version 6 is incompatible with the current major version of this system",
                     assertThrows(IllegalArgumentException.class,
                                  () -> app.submit(new ApplicationPackageBuilder().region("us-east-3")
                                                                                  .majorVersion(6)
                                                                                  .compileVersion(oldVersion)
                                                                                  .build()))
                             .getMessage());

        tester.deploymentTrigger().forceChange(app.instanceId(), Change.of(oldVersion).with(app.application().revisions().last().get().id()).withPlatformPin());
        app.deploy();
        assertEquals(oldVersion, app.deployment(ZoneId.from("prod", "us-east-3")).version());

        tester.controllerTester().computeVersionStatus();
        assertEquals(List.of(oldVersion, currentVersion),
                     tester.controller().readVersionStatus().versions().stream().map(VespaVersion::versionNumber).toList());
    }

    @Test
    void testInitialDeploymentPlatform() {
        Version version0 = tester.controllerTester().controller().readSystemVersion();
        Version version1 = new Version("6.2");
        Version version2 = new Version("6.3");
        assertEquals(version0, tester.newDeploymentContext("t", "a1", "default").submit().deploy().application().oldestDeployedPlatform().get());

        // A new version, with normal confidence, is the default for a new app.
        tester.controllerTester().upgradeSystem(version1);
        tester.upgrader().overrideConfidence(version1, Confidence.normal);
        tester.controllerTester().computeVersionStatus();
        assertEquals(version1, tester.newDeploymentContext("t", "a2", "default").submit().deploy().application().oldestDeployedPlatform().get());

        // A newer version has broken confidence, leaving the previous version as the default.
        tester.controllerTester().upgradeSystem(version2);
        tester.upgrader().overrideConfidence(version2, Confidence.broken);
        tester.controllerTester().computeVersionStatus();
        assertEquals(version1, tester.newDeploymentContext("t", "a3", "default").submit().deploy().application().oldestDeployedPlatform().get());

        DeploymentContext dev1 = tester.newDeploymentContext("t", "d1", "default");
        DeploymentContext dev2 = tester.newDeploymentContext("t", "d2", "default");
        assertEquals(version1, dev1.runJob(JobType.dev("us-east-1"), DeploymentContext.applicationPackage()).deployment(ZoneId.from("dev", "us-east-1")).version());

        DeploymentUpgrader devUpgrader = new DeploymentUpgrader(tester.controller(), Duration.ofHours(1));
        for (int i = 0; i < 24; i++) {
            tester.clock().advance(Duration.ofHours(1));
            devUpgrader.run();
        }
        dev1.assertNotRunning(JobType.dev("us-east-1"));

        // Normal confidence lets the newest version be the default again.
        tester.upgrader().overrideConfidence(version2, Confidence.normal);
        tester.controllerTester().computeVersionStatus();
        assertEquals(version2, tester.newDeploymentContext("t", "a4", "default").submit().deploy().application().oldestDeployedPlatform().get());
        assertEquals(version1, dev1.runJob(JobType.dev("us-east-1"), DeploymentContext.applicationPackage()).deployment(ZoneId.from("dev", "us-east-1")).version());
        assertEquals(version2, dev2.runJob(JobType.dev("us-east-1"), DeploymentContext.applicationPackage()).deployment(ZoneId.from("dev", "us-east-1")).version());

        for (int i = 0; i < 24; i++) {
            tester.clock().advance(Duration.ofHours(1));
            devUpgrader.run();
        }
        dev1.assertRunning(JobType.dev("us-east-1"));
        dev1.runJob(JobType.dev("us-east-1"));
        assertEquals(version2, dev1.deployment(ZoneId.from("dev", "us-east-1")).version());
    }

    @Test
    void testInstanceWithOnlySystemTestInTwoClouds() {
        String spec = """
                      <deployment>
                        <instance id='tests'>
                          <test />
                          <upgrade revision-target='next' />
                        </instance>
                        <instance id='main'>
                          <prod>
                            <region>us-east-3</region>
                            <region>alpha-centauri</region>
                          </prod>
                          <upgrade revision-target='next' />
                        </instance>
                      </deployment>
                      """;

        RegionName alphaCentauri = RegionName.from("alpha-centauri");
        ZoneApiMock.Builder builder = ZoneApiMock.newBuilder().withCloud("centauri").withSystem(tester.controller().system());
        ZoneApi testAlphaCentauri = builder.with(ZoneId.from(Environment.test, alphaCentauri)).build();
        ZoneApi stagingAlphaCentauri = builder.with(ZoneId.from(Environment.staging, alphaCentauri)).build();
        ZoneApi prodAlphaCentauri = builder.with(ZoneId.from(prod, alphaCentauri)).build();

        tester.controllerTester().zoneRegistry().addZones(testAlphaCentauri, stagingAlphaCentauri, prodAlphaCentauri);
        tester.controllerTester().setRoutingMethod(tester.controllerTester().zoneRegistry().zones().all().ids(), RoutingMethod.sharedLayer4);
        tester.configServer().bootstrap(tester.controllerTester().zoneRegistry().zones().all().ids(), SystemApplication.notController());

        ApplicationPackage appPackage = ApplicationPackageBuilder.fromDeploymentXml(spec);
        DeploymentContext tests = tester.newDeploymentContext("tenant", "application", "tests");
        DeploymentContext main = tester.newDeploymentContext("tenant", "application", "main");
        Version version1 = new Version("6.2");
        tester.controllerTester().upgradeSystem(version1);
        tests.submit(appPackage);
        JobId systemTestJob = new JobId(tests.instanceId(), systemTest);
        JobId stagingTestJob = new JobId(tests.instanceId(), stagingTest);
        JobId mainJob = new JobId(main.instanceId(), productionUsEast3);
        JobId centauriJob = new JobId(main.instanceId(), JobType.deploymentTo(prodAlphaCentauri.getId()));
        JobType centuariTest = JobType.systemTest(tester.controllerTester().zoneRegistry(), CloudName.from("centauri"));
        JobType centuariStaging = JobType.stagingTest(tester.controllerTester().zoneRegistry(), CloudName.from("centauri"));

        assertEquals(Set.of(systemTestJob, stagingTestJob, mainJob, centauriJob), tests.deploymentStatus().jobsToRun().keySet());
        tests.runJob(systemTest).runJob(stagingTest).triggerJobs();

        assertEquals(Set.of(systemTestJob, stagingTestJob, mainJob, centauriJob), tests.deploymentStatus().jobsToRun().keySet());
        tests.triggerJobs();
        assertEquals(3, tester.jobs().active().size());

        tests.runJob(centuariTest);
        tester.outstandingChangeDeployer().run();

        assertEquals(2, tester.jobs().active().size());
        main.assertRunning(productionUsEast3);

        tests.runJob(centuariStaging);
        main.runJob(productionUsEast3).runJob(centauriJob.type());

        assertEquals(Change.empty(), tests.instance().change());
        assertEquals(Change.empty(), main.instance().change());
        assertEquals(Set.of(), tests.deploymentStatus().jobsToRun().keySet());

        // Versions 2 and 3 become available.
        // Tests instance fails on 2, then updates to 3.
        // Version 2 should not be a target for either instance.
        // Version 2 should also not be possible to set as a forced target for the tests instance.
        Version version2 = new Version("6.3");
        tester.controllerTester().upgradeSystem(version2);
        tester.upgrader().run();
        tester.triggerJobs();

        assertEquals(Change.of(version2), tests.instance().change());
        assertEquals(Change.empty(), main.instance().change());
        assertEquals(Set.of(systemTestJob), tests.deploymentStatus().jobsToRun().keySet());
        assertEquals(2, tests.deploymentStatus().jobsToRun().get(systemTestJob).size());

        Version version3 = new Version("6.4");
        tester.controllerTester().upgradeSystem(version3);
        tests.runJob(systemTest)              // Success in default cloud.
             .failDeployment(centuariTest);   // Failure in centauri cloud.
        tester.upgrader().run();

        assertEquals(Change.of(version3), tests.instance().change());
        assertEquals(Change.empty(), main.instance().change());
        assertEquals(Set.of(systemTestJob), tests.deploymentStatus().jobsToRun().keySet());

        tests.runJob(systemTest).runJob(centuariTest);
        tester.upgrader().run();
        tests.runJob(stagingTest).runJob(centuariStaging);

        assertEquals(Change.empty(), tests.instance().change());
        assertEquals(Change.of(version3), main.instance().change());
        assertEquals(Set.of(mainJob, centauriJob), tests.deploymentStatus().jobsToRun().keySet());

        main.runJob(productionUsEast3);
        main.runJob(centauriJob.type());

        assertEquals(Change.empty(), tests.instance().change());
        assertEquals(Change.empty(), main.instance().change());
        assertEquals(Set.of(), tests.deploymentStatus().jobsToRun().keySet());

        tester.deploymentTrigger().forceChange(tests.instanceId(), Change.of(version2));

        assertEquals(Change.empty(), tests.instance().change());
        assertEquals(Change.empty(), main.instance().change());
        assertEquals(Set.of(), tests.deploymentStatus().jobsToRun().keySet());

        // Revisions 2 and 3 become available.
        // Tests instance fails on 2, then update to 3.
        // Revision 2 should not be a target for either instance.
        // Revision 2 should also not be possible to set as a forced target for the tests instance.
        tests.submit(appPackage);
        Optional<RevisionId> revision2 = tests.lastSubmission();

        assertEquals(Change.of(revision2.get()), tests.instance().change());
        assertEquals(Change.empty(), main.instance().change());
        assertEquals(Set.of(systemTestJob), tests.deploymentStatus().jobsToRun().keySet());
        assertEquals(2, tests.deploymentStatus().jobsToRun().get(systemTestJob).size());

        tests.submit(appPackage);
        Optional<RevisionId> revision3 = tests.lastSubmission();

        assertEquals(Change.of(revision2.get()), tests.instance().change());
        assertEquals(Change.empty(), main.instance().change());
        assertEquals(Set.of(systemTestJob), tests.deploymentStatus().jobsToRun().keySet());

        tests.failDeployment(systemTest);
        tester.outstandingChangeDeployer().run();

        assertEquals(Change.of(revision3.get()), tests.instance().change());
        assertEquals(Change.empty(), main.instance().change());
        assertEquals(Set.of(systemTestJob), tests.deploymentStatus().jobsToRun().keySet());

        tests.runJob(systemTest);
        assertEquals(Change.of(revision3.get()), tests.instance().change());
        assertEquals(Change.empty(), main.instance().change());
        assertEquals(Set.of(systemTestJob, stagingTestJob), tests.deploymentStatus().jobsToRun().keySet());

        tester.outstandingChangeDeployer().run();
        tester.outstandingChangeDeployer().run();
        tests.runJob(stagingTest);

        assertEquals(Change.of(revision3.get()), tests.instance().change());
        assertEquals(Change.empty(), main.instance().change());
        assertEquals(Set.of(systemTestJob, stagingTestJob), tests.deploymentStatus().jobsToRun().keySet());

        tests.runJob(centuariTest);
        tester.outstandingChangeDeployer().run();
        tester.outstandingChangeDeployer().run();

        assertEquals(Change.empty(), tests.instance().change());
        assertEquals(Change.of(revision3.get()), main.instance().change());
        assertEquals(Set.of(stagingTestJob, mainJob, centauriJob), tests.deploymentStatus().jobsToRun().keySet());

        tests.runJob(centuariStaging);

        assertEquals(Change.empty(), tests.instance().change());
        assertEquals(Change.of(revision3.get()), main.instance().change());
        assertEquals(Set.of(mainJob, centauriJob), tests.deploymentStatus().jobsToRun().keySet());

        main.runJob(productionUsEast3);
        main.runJob(centauriJob.type());
        tester.outstandingChangeDeployer().run();

        assertEquals(Change.empty(), tests.instance().change());
        assertEquals(Change.empty(), main.instance().change());
        assertEquals(Set.of(), tests.deploymentStatus().jobsToRun().keySet());

        tester.deploymentTrigger().forceChange(tests.instanceId(), Change.of(revision2.get()));

        assertEquals(Change.empty(), tests.instance().change());
        assertEquals(Change.empty(), main.instance().change());
        assertEquals(Set.of(), tests.deploymentStatus().jobsToRun().keySet());
    }

    @Test
    void testInstancesWithMultipleClouds() {
        String spec = """
                      <deployment>
                        <parallel>
                          <instance id='separate'>
                            <test />
                            <staging />
                            <prod>
                              <region>alpha-centauri</region>
                            </prod>
                          </instance>
                          <instance id='independent'>
                            <test />
                          </instance>
                          <steps>
                            <parallel>
                              <instance id='alpha'>
                                <test />
                                <prod>
                                  <region>us-east-3</region>
                                </prod>
                              </instance>
                              <instance id='beta'>
                                <test />
                                <prod>
                                  <region>alpha-centauri</region>
                                </prod>
                              </instance>
                              <instance id='gamma'>
                                <test />
                              </instance>
                            </parallel>
                            <instance id='nu'>
                              <staging />
                            </instance>
                            <instance id='omega'>
                              <prod>
                                <region>alpha-centauri</region>
                              </prod>
                            </instance>
                          </steps>
                          <instance id='dependent'>
                            <prod>
                              <region>us-east-3</region>
                            </prod>
                          </instance>
                        </parallel>
                      </deployment>
                      """;

        RegionName alphaCentauri = RegionName.from("alpha-centauri");
        ZoneApiMock.Builder builder = ZoneApiMock.newBuilder().withCloud("centauri").withSystem(tester.controller().system());
        ZoneApi testAlphaCentauri = builder.with(ZoneId.from(Environment.test, alphaCentauri)).build();
        ZoneApi stagingAlphaCentauri = builder.with(ZoneId.from(Environment.staging, alphaCentauri)).build();
        ZoneApi prodAlphaCentauri = builder.with(ZoneId.from(prod, alphaCentauri)).build();

        tester.controllerTester().zoneRegistry().addZones(testAlphaCentauri, stagingAlphaCentauri, prodAlphaCentauri);
        tester.controllerTester().setRoutingMethod(tester.controllerTester().zoneRegistry().zones().all().ids(), RoutingMethod.sharedLayer4);
        tester.configServer().bootstrap(tester.controllerTester().zoneRegistry().zones().all().ids(), SystemApplication.notController());

        ApplicationPackage appPackage = ApplicationPackageBuilder.fromDeploymentXml(spec);
        DeploymentContext alpha = tester.newDeploymentContext("tenant", "application", "alpha").submit(appPackage).deploy();
        DeploymentContext beta = tester.newDeploymentContext("tenant", "application", "beta");
        DeploymentContext gamma = tester.newDeploymentContext("tenant", "application", "gamma");
        DeploymentContext nu = tester.newDeploymentContext("tenant", "application", "nu");
        DeploymentContext omega = tester.newDeploymentContext("tenant", "application", "omega");
        DeploymentContext separate = tester.newDeploymentContext("tenant", "application", "separate");
        DeploymentContext independent = tester.newDeploymentContext("tenant", "application", "independent");
        DeploymentContext dependent = tester.newDeploymentContext("tenant", "application", "dependent");
        alpha.submit(appPackage);
        Map<JobId, List<DeploymentStatus.Job>> jobs = alpha.deploymentStatus().jobsToRun();

        JobType centauriTest = JobType.systemTest(tester.controller().zoneRegistry(), CloudName.from("centauri"));
        JobType centauriStaging = JobType.stagingTest(tester.controller().zoneRegistry(), CloudName.from("centauri"));
        JobType centauriProd = JobType.deploymentTo(ZoneId.from(prod, alphaCentauri));
        assertQueued("separate", jobs, systemTest, centauriTest);
        assertQueued("separate", jobs, stagingTest, centauriStaging);
        assertQueued("independent", jobs, systemTest, centauriTest);
        assertQueued("alpha", jobs, systemTest);
        assertQueued("beta", jobs, centauriTest);
        assertQueued("gamma", jobs, centauriTest);

        // Once alpha runs its default system test, it also runs the centauri system test, as omega depends on it.
        alpha.runJob(systemTest);
        assertQueued("alpha", alpha.deploymentStatus().jobsToRun(), centauriTest);

        // Run tests, and see production jobs are triggered as they are verified.
        for (DeploymentContext app : List.of(alpha, beta, gamma, nu, omega, separate, independent, dependent))
            tester.deploymentTrigger().forceChange(app.instanceId(), Change.of(alpha.lastSubmission().get()));

        // Missing separate staging test.
        alpha.triggerJobs().assertNotRunning(productionUsEast3);

        beta.runJob(centauriTest);
        // Missing separate centauri staging.
        beta.triggerJobs().assertNotRunning(centauriProd);

        gamma.runJob(centauriTest);

        // Missing alpha centauri test, and nu centauri staging.
        omega.triggerJobs().assertNotRunning(centauriProd);
        alpha.runJob(centauriTest);
        omega.triggerJobs().assertNotRunning(centauriProd);
        nu.runJob(centauriStaging);
        omega.triggerJobs().assertRunning(centauriProd);

        separate.triggerJobs().assertNotRunning(centauriProd);

        separate.runJob(centauriStaging);
        separate.triggerJobs().assertNotRunning(centauriProd);
        beta.triggerJobs().assertRunning(centauriProd);

        separate.runJob(centauriTest);
        separate.triggerJobs().assertRunning(centauriProd);

        dependent.triggerJobs().assertNotRunning(productionUsEast3);

        separate.runJob(systemTest).runJob(stagingTest).triggerJobs();
        dependent.triggerJobs().assertRunning(productionUsEast3);
        alpha.triggerJobs().assertRunning(productionUsEast3);

        separate.runJob(centauriProd);
        alpha.runJob(productionUsEast3);
        beta.runJob(centauriProd);
        omega.runJob(centauriProd);
        dependent.runJob(productionUsEast3);
        independent.runJob(centauriTest).runJob(systemTest);
        assertEquals(Map.of(), alpha.deploymentStatus().jobsToRun());
    }

    private static void assertQueued(String instance, Map<JobId, List<DeploymentStatus.Job>> jobs, JobType... expected) {
        List<DeploymentStatus.Job> queued = jobs.get(new JobId(ApplicationId.from("tenant", "application", instance), expected[0]));
        Set<ZoneId> remaining = new HashSet<>();
        for (JobType ex : expected) remaining.add(ex.zone());
        for (DeploymentStatus.Job q : queued)
            if ( ! remaining.remove(q.type().zone()))
                fail("unexpected queued job for " + instance + ": " + q.type());
        if ( ! remaining.isEmpty())
            fail("expected tests for " + instance + " were not queued in : " + remaining);
    }

    @Test
    void testNoTests() {
        DeploymentContext app = tester.newDeploymentContext();
        app.submit(new ApplicationPackageBuilder().systemTest().region("us-east-3").build());

        // Declared tests must have run actual tests to succeed.
        app.failTests(systemTest, true);
        assertFalse(tester.jobs().last(app.instanceId(), systemTest).get().hasSucceeded());
        app.failTests(stagingTest, true);
        assertTrue(tester.jobs().last(app.instanceId(), stagingTest).get().hasSucceeded());
    }

    @Test
    void testBrokenApplication() {
        DeploymentContext app = tester.newDeploymentContext();
        app.submit().runJob(systemTest).failDeployment(stagingTest).failDeployment(stagingTest);
        tester.clock().advance(Duration.ofDays(31));
        tester.outstandingChangeDeployer().run();
        assertEquals(OptionalLong.empty(), app.application().projectId());

        app.assertNotRunning(stagingTest);
        tester.triggerJobs();
        app.assertNotRunning(stagingTest);
        assertEquals(4, app.deploymentStatus().jobsToRun().size());

        app.submit().runJob(systemTest).failDeployment(stagingTest);
        tester.clock().advance(Duration.ofDays(20));
        app.submit().runJob(systemTest).failDeployment(stagingTest);
        tester.clock().advance(Duration.ofDays(20));
        tester.outstandingChangeDeployer().run();
        assertEquals(OptionalLong.of(1000), app.application().projectId());
        tester.clock().advance(Duration.ofDays(20));
        tester.outstandingChangeDeployer().run();
        assertEquals(OptionalLong.empty(), app.application().projectId());

        app.assertNotRunning(stagingTest);
        tester.triggerJobs();
        app.assertNotRunning(stagingTest);
        assertEquals(4, app.deploymentStatus().jobsToRun().size());

        app.submit().runJob(systemTest).runJob(stagingTest).failDeployment(productionUsCentral1);
        tester.clock().advance(Duration.ofDays(31));
        tester.outstandingChangeDeployer().run();
        assertEquals(OptionalLong.empty(), app.application().projectId());

        app.assertNotRunning(productionUsCentral1);
        tester.triggerJobs();
        app.assertNotRunning(productionUsCentral1);
        assertEquals(3, app.deploymentStatus().jobsToRun().size());

        app.submit().runJob(systemTest).runJob(stagingTest).timeOutConvergence(productionUsCentral1);
        tester.clock().advance(Duration.ofDays(31));
        tester.outstandingChangeDeployer().run();
        assertEquals(OptionalLong.of(1000), app.application().projectId());

        app.assertNotRunning(productionUsCentral1);
        tester.triggerJobs();
        app.assertRunning(productionUsCentral1);
    }

    @Test
    void testJobNames() {
        ZoneRegistryMock zones = new ZoneRegistryMock(SystemName.main);
        List<ZoneApi> existing = new ArrayList<>(zones.zones().all().zones());
        existing.add(ZoneApiMock.newBuilder().withCloud("pink-clouds").withId("test.zone").build());
        zones.setZones(existing);

        JobType defaultSystemTest = JobType.systemTest(zones, CloudName.DEFAULT);
        JobType pinkSystemTest = JobType.systemTest(zones, CloudName.from("pink-clouds"));

        // Job name is identity, used for looking up run history, etc..
        assertEquals(defaultSystemTest, pinkSystemTest);

        assertEquals(defaultSystemTest, JobType.systemTest(zones, null));
        assertEquals(defaultSystemTest, JobType.systemTest(zones, CloudName.from("dark-clouds")));
        assertEquals(defaultSystemTest, JobType.fromJobName("system-test", zones));

        assertEquals(ZoneId.from("test", "us-east-1"), defaultSystemTest.zone());
        assertEquals(ZoneId.from("staging", "us-east-3"), JobType.stagingTest(zones, zones.systemZone().getCloudName()).zone());

        assertEquals(ZoneId.from("test", "zone"), pinkSystemTest.zone());
        assertEquals(ZoneId.from("staging", "us-east-3"), JobType.stagingTest(zones, CloudName.from("pink-clouds")).zone());

        assertThrows(IllegalStateException.class, JobType.systemTest(zones, null)::zone);
        assertThrows(IllegalStateException.class, JobType.fromJobName("system-test", zones)::zone);
        assertThrows(IllegalStateException.class, JobType.fromJobName("staging-test", zones)::zone);
    }

    @Test
    void testOrderOfTests() {
        String deploymentXml = """
                               <deployment version="1.0">
                                 <test/>
                                 <staging/>
                                 <block-change days="fri" hours="0-23" time-zone="UTC" />
                                 <prod>
                                   <region>us-east-3</region>
                                   <delay hours="1"/>
                                   <test>us-east-3</test>
                                   <region>us-west-1</region>
                                 </prod>
                               </deployment>""";

        Version version1 = new Version("7.1");
        tester.controllerTester().upgradeSystem(version1);
        ApplicationPackage applicationPackage = ApplicationPackageBuilder.fromDeploymentXml(deploymentXml);
        tester.clock().setInstant(Instant.EPOCH.plusSeconds(8 * 60 * 60)); // Thursday morning.
        DeploymentContext app = tester.newDeploymentContext().submit(applicationPackage);
        RevisionId revision1 = app.lastSubmission().get();
        app.runJob(systemTest).runJob(stagingTest).runJob(productionUsEast3);
        tester.clock().advance(Duration.ofHours(1));
        app.runJob(testUsEast3).runJob(productionUsWest1);
        assertEquals(Change.empty(), app.instance().change());

        tester.clock().advance(Duration.ofDays(1)); // Enter block window.
        app.submit(applicationPackage);
        assertEquals(Change.empty(), app.instance().change());

        Version version2 = new Version("7.2");
        RevisionId revision2 = app.lastSubmission().get();

        app.runJob(systemTest).runJob(stagingTest);
        app.triggerJobs().assertNotRunning(productionUsEast3);
        tester.controllerTester().upgradeSystem(version2);
        tester.clock().advance(Duration.ofDays(1)); // Leave block window.
        tester.upgrader().run();
        tester.outstandingChangeDeployer().run();
        assertEquals(Change.of(revision2).with(version2), app.instance().change());
        app.runJob(systemTest).runJob(stagingTest);
        app.runJob(productionUsEast3);
        app.triggerJobs();
        app.assertNotRunning(productionUsEast3); // Platform upgrade should not start before test is done with revision.
        tester.clock().advance(Duration.ofHours(1));
        app.triggerJobs();
        app.assertNotRunning(productionUsEast3); // Platform upgrade should not start before test is done with revision.
        app.runJob(testUsEast3);
        app.runJob(productionUsEast3)
           .runJob(productionUsWest1);
        tester.clock().advance(Duration.ofHours(1));
        app.runJob(testUsEast3);
        app.runJob(productionUsWest1);
        assertEquals(Change.empty(), app.instance().change());
    }

    @Test
    void miniBenchmark() {
        String spec = """
                      <deployment version="1.0">
                          <parallel>
                              <instance id="instance0">
                                  <test tester-flavor="d-8-16-10" />
                                  <block-change version="true" revision="false" days="mon-fri,sun" hours="4-23" time-zone="UTC" />
                              </instance>
                              <instance id="instance1">
                                  <test tester-flavor="d-8-16-10" />
                                  <block-change version="true" revision="false" days="mon-fri,sun" hours="4-23" time-zone="UTC" />
                              </instance>
                              <instance id="instance2">
                                  <test tester-flavor="d-8-16-10" />
                                  <block-change version="true" revision="false" days="mon-fri,sun" hours="4-23" time-zone="UTC" />
                              </instance>
                              <instance id="instance3">
                                  <test tester-flavor="d-8-16-10" />
                                  <block-change version="true" revision="false" days="mon-fri,sun" hours="4-23" time-zone="UTC" />
                              </instance>
                              <instance id="stress">
                                  <staging />
                                  <block-change version="true" revision="false" days="mon-fri,sun" hours="4-23" time-zone="UTC" />
                              </instance>
                          </parallel>
                          <instance id="beta1">
                              <block-change version="true" revision="false" days="mon-fri,sun" hours="4-23" time-zone="UTC" />
                              <block-change version="true" revision="false" days="sat" hours="0-23" time-zone="UTC" />
                              <upgrade revision-change='when-clear' rollout='separate' revision-target='next' policy='conservative'/>
                              <prod>
                                  <parallel>
                                      <steps>
                                          <region>us-east-3</region>
                                          <test>us-east-3</test>
                                      </steps>
                                      <steps>
                                          <region>us-west-1</region>
                                          <test>us-west-1</test>
                                      </steps>
                                      <steps>
                                          <region>eu-west-1</region>
                                          <test>eu-west-1</test>
                                      </steps>
                                      <steps>
                                          <region>us-central-1</region>
                                          <test>us-central-1</test>
                                      </steps>
                                  </parallel>
                              </prod>
                          </instance>
                          <instance id="gamma5">
                              <block-change version="true" revision="false" days="mon-fri,sun" hours="4-23" time-zone="UTC" />
                              <block-change version="true" revision="false" days="sat" hours="0-23" time-zone="UTC" />
                              <upgrade revision-change='when-clear' rollout='separate' revision-target='next' policy='conservative'/>
                              <prod>
                                  <parallel>
                                      <steps>
                                          <region>us-east-3</region>
                                          <test>us-east-3</test>
                                      </steps>
                                      <steps>
                                          <region>us-west-1</region>
                                          <test>us-west-1</test>
                                      </steps>
                                      <steps>
                                          <region>eu-west-1</region>
                                          <test>eu-west-1</test>
                                      </steps>
                                      <steps>
                                          <region>us-central-1</region>
                                          <test>us-central-1</test>
                                      </steps>
                                  </parallel>
                              </prod>
                          </instance>
                          <instance id="delta21">
                              <block-change version="true" revision="false" days="mon-fri,sun" hours="4-23" time-zone="UTC" />
                              <block-change version="true" revision="false" days="sat" hours="0-23" time-zone="UTC" />
                              <upgrade revision-change='when-clear' rollout='separate' revision-target='next' policy='conservative'/>
                              <prod>
                                  <parallel>
                                      <steps>
                                          <region>us-east-3</region>
                                          <test>us-east-3</test>
                                      </steps>
                                      <steps>
                                          <region>us-west-1</region>
                                          <test>us-west-1</test>
                                      </steps>
                                      <steps>
                                          <region>eu-west-1</region>
                                          <test>eu-west-1</test>
                                      </steps>
                                      <steps>
                                          <region>us-central-1</region>
                                          <test>us-central-1</test>
                                      </steps>
                                  </parallel>
                              </prod>
                          </instance>
                          <instance id="prod21a">
                              <block-change version="true" revision="false" days="mon-fri,sun" hours="4-23" time-zone="UTC" />
                              <block-change version="true" revision="false" days="sat" hours="0-23" time-zone="UTC" />
                              <upgrade revision-change='when-clear' rollout='separate' revision-target='next' policy='conservative'/>
                              <prod>
                                  <parallel>
                                      <steps>
                                          <region>us-east-3</region>
                                          <test>us-east-3</test>
                                      </steps>
                                      <steps>
                                          <region>us-west-1</region>
                                          <test>us-west-1</test>
                                      </steps>
                                      <steps>
                                          <region>eu-west-1</region>
                                          <test>eu-west-1</test>
                                      </steps>
                                      <steps>
                                          <region>us-central-1</region>
                                          <test>us-central-1</test>
                                      </steps>
                                  </parallel>
                              </prod>
                          </instance>
                          <instance id="prod21b">
                              <block-change version="true" revision="false" days="mon-fri,sun" hours="4-23" time-zone="UTC" />
                              <block-change version="true" revision="false" days="sat" hours="0-23" time-zone="UTC" />
                              <upgrade revision-change='when-clear' rollout='separate' revision-target='next' policy='conservative'/>
                              <prod>
                                  <parallel>
                                      <steps>
                                          <region>us-east-3</region>
                                          <test>us-east-3</test>
                                      </steps>
                                      <steps>
                                          <region>us-west-1</region>
                                          <test>us-west-1</test>
                                      </steps>
                                      <steps>
                                          <region>eu-west-1</region>
                                          <test>eu-west-1</test>
                                      </steps>
                                      <steps>
                                          <region>us-central-1</region>
                                          <test>us-central-1</test>
                                      </steps>
                                  </parallel>
                              </prod>
                          </instance>
                          <instance id="prod21c">
                              <block-change version="true" revision="false" days="mon-fri,sun" hours="4-23" time-zone="UTC" />
                              <block-change version="true" revision="false" days="sat" hours="0-23" time-zone="UTC" />
                              <upgrade revision-change='when-clear' rollout='separate' revision-target='next' policy='conservative'/>
                              <prod>
                                  <parallel>
                                      <steps>
                                          <region>us-east-3</region>
                                          <test>us-east-3</test>
                                      </steps>
                                      <steps>
                                          <region>us-west-1</region>
                                          <test>us-west-1</test>
                                      </steps>
                                      <steps>
                                          <region>eu-west-1</region>
                                          <test>eu-west-1</test>
                                      </steps>
                                      <steps>
                                          <region>us-central-1</region>
                                          <test>us-central-1</test>
                                      </steps>
                                  </parallel>
                              </prod>
                          </instance>
                          <instance id="cd10">
                              <block-change version="true" revision="false" days="mon-fri,sun" hours="4-23" time-zone="UTC" />
                              <block-change version="true" revision="false" days="sat" hours="0-23" time-zone="UTC" />
                              <upgrade revision-change='when-clear' rollout='separate' revision-target='next' policy='conservative'/>
                              <prod>
                                  <parallel>
                                      <steps>
                                          <region>us-east-3</region>
                                          <test>us-east-3</test>
                                      </steps>
                                      <steps>
                                          <region>us-west-1</region>
                                          <test>us-west-1</test>
                                      </steps>
                                      <steps>
                                          <region>eu-west-1</region>
                                          <test>eu-west-1</test>
                                      </steps>
                                      <steps>
                                          <region>us-central-1</region>
                                          <test>us-central-1</test>
                                      </steps>
                                  </parallel>
                              </prod>
                          </instance>
                          <instance id="prod1">
                              <block-change version="true" revision="false" days="mon-fri,sun" hours="4-23" time-zone="UTC" />
                              <block-change version="true" revision="false" days="sat" hours="0-23" time-zone="UTC" />
                              <upgrade revision-change='when-clear' rollout='separate' revision-target='next' policy='conservative'/>
                              <prod>
                                  <parallel>
                                      <steps>
                                          <region>us-east-3</region>
                                          <test>us-east-3</test>
                                      </steps>
                                      <steps>
                                          <region>us-west-1</region>
                                          <test>us-west-1</test>
                                      </steps>
                                      <steps>
                                          <region>eu-west-1</region>
                                          <test>eu-west-1</test>
                                      </steps>
                                      <steps>
                                          <region>us-central-1</region>
                                          <test>us-central-1</test>
                                      </steps>
                                  </parallel>
                              </prod>
                          </instance>
                      </deployment>""";
        tester.newDeploymentContext("t", "a", "prod1").submit(ApplicationPackageBuilder.fromDeploymentXml(spec)).deploy();
    }

}
