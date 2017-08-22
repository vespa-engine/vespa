// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class FailureRedeployerTest {

    @Test
    public void testRetryingFailedJobsDuringDeployment() {
        DeploymentTester tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("canary")
                .environment(Environment.prod)
                .region("us-east-3")
                .build();
        Version version = Version.fromString("5.0");
        tester.updateVersionStatus(version);

        Application app = tester.createApplication("app1", "tenant1", 1, 11L);
        tester.notifyJobCompletion(DeploymentJobs.JobType.component, app, true);
        tester.deployAndNotify(DeploymentJobs.JobType.systemTest, app, applicationPackage, true);
        tester.deployAndNotify(DeploymentJobs.JobType.stagingTest, app, applicationPackage, true);
        tester.deployAndNotify(DeploymentJobs.JobType.productionUsEast3, app, applicationPackage, true);

        // New version is released
        version = Version.fromString("5.1");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();

        // Test environments pass
        tester.deployAndNotify(DeploymentJobs.JobType.systemTest, app, applicationPackage, true);
        tester.deployAndNotify(DeploymentJobs.JobType.stagingTest, app, applicationPackage, true);

        // Production job fails and is retried
        tester.clock().advance(Duration.ofSeconds(1)); // Advance time so that we can detect jobs in progress
        tester.deployAndNotify(DeploymentJobs.JobType.productionUsEast3, app, applicationPackage, false);
        assertEquals("Production job is retried", 1, tester.buildSystem().jobs().size());
        assertEquals("Application has pending upgrade to " + version, version, tester.versionChange(app.id()).get().version());

        // Another version is released, which cancels any pending upgrades to lower versions
        version = Version.fromString("5.2");
        tester.updateVersionStatus(version);
        tester.upgrader().maintain();
        assertEquals("Application starts upgrading to new version", 1, tester.buildSystem().jobs().size());
        assertEquals("Application has pending upgrade to " + version, version, tester.versionChange(app.id()).get().version());

        // Failure redeployer does not retry failing job for prod.us-east-3 as there's an ongoing deployment
        tester.clock().advance(Duration.ofMinutes(1));
        tester.failureRedeployer().maintain();
        assertFalse("Job is not retried", tester.buildSystem().jobs().stream()
                .anyMatch(j -> j.jobName().equals(DeploymentJobs.JobType.productionUsEast3.id())));

        // Test environments pass
        tester.deployAndNotify(DeploymentJobs.JobType.systemTest, app, applicationPackage, true);
        tester.deployAndNotify(DeploymentJobs.JobType.stagingTest, app, applicationPackage, true);

        // Production job fails again and exhausts all immediate retries
        tester.deployAndNotify(DeploymentJobs.JobType.productionUsEast3, app, applicationPackage, false);
        tester.buildSystem().takeJobsToRun();
        tester.clock().advance(Duration.ofMinutes(10));
        tester.notifyJobCompletion(DeploymentJobs.JobType.productionUsEast3, app, false);
        assertTrue("Retries exhausted", tester.buildSystem().jobs().isEmpty());
        assertTrue("Failure is recorded", tester.application(app.id()).deploymentJobs().hasFailures());

        // Failure redeployer retries job
        tester.clock().advance(Duration.ofMinutes(5));
        tester.failureRedeployer().maintain();
        assertEquals("Job is retried", 1, tester.buildSystem().jobs().size());

        // Production job finally succeeds
        tester.deployAndNotify(DeploymentJobs.JobType.productionUsEast3, app, applicationPackage, true);
        assertTrue("All jobs consumed", tester.buildSystem().jobs().isEmpty());
        assertFalse("No failures", tester.application(app.id()).deploymentJobs().hasFailures());
    }

    @Test
    public void testRetriesDeploymentWithStuckJobs() {
        DeploymentTester tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("canary")
                .environment(Environment.prod)
                .region("us-east-3")
                .build();

        Application app = tester.createApplication("app1", "tenant1", 1, 11L);
        tester.notifyJobCompletion(DeploymentJobs.JobType.component, app, true);
        tester.deployAndNotify(DeploymentJobs.JobType.systemTest, app, applicationPackage, true);

        // staging-test starts, but does not complete
        assertEquals(DeploymentJobs.JobType.stagingTest.id(), tester.buildSystem().takeJobsToRun().get(0).jobName());
        tester.failureRedeployer().maintain();
        assertTrue("No jobs retried", tester.buildSystem().jobs().isEmpty());

        // Just over 12 hours pass, deployment is retried from beginning
        tester.clock().advance(Duration.ofHours(12).plus(Duration.ofSeconds(1)));
        tester.failureRedeployer().maintain();
        assertEquals(DeploymentJobs.JobType.component.id(), tester.buildSystem().takeJobsToRun().get(0).jobName());

        // Ensure that system-test is trigered after component. Triggering component records a new change, but in this
        // case there's already a change in progress which we want to discard and start over
        tester.notifyJobCompletion(DeploymentJobs.JobType.component, app, true);
        assertEquals(DeploymentJobs.JobType.systemTest.id(), tester.buildSystem().jobs().get(0).jobName());
    }

    @Test
    public void testRetriesJobsFailingForCurrentChange() {
        DeploymentTester tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("canary")
                .environment(Environment.prod)
                .region("us-east-3")
                .build();
        Version version = Version.fromString("5.0");
        tester.updateVersionStatus(version);

        Application app = tester.createApplication("app1", "tenant1", 1, 11L);
        tester.notifyJobCompletion(DeploymentJobs.JobType.component, app, true);
        tester.deployAndNotify(DeploymentJobs.JobType.systemTest, app, applicationPackage, true);
        tester.deployAndNotify(DeploymentJobs.JobType.stagingTest, app, applicationPackage, true);
        tester.deployAndNotify(DeploymentJobs.JobType.productionUsEast3, app, applicationPackage, true);

        // New version is released
        version = Version.fromString("5.1");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        assertEquals("Application has pending upgrade to " + version, version, tester.versionChange(app.id()).get().version());

        // system-test fails and exhausts all immediate retries
        tester.deployAndNotify(DeploymentJobs.JobType.systemTest, app, applicationPackage, false);
        tester.buildSystem().takeJobsToRun();
        tester.clock().advance(Duration.ofMinutes(10));
        tester.notifyJobCompletion(DeploymentJobs.JobType.systemTest, app, false);
        assertTrue("Retries exhausted", tester.buildSystem().jobs().isEmpty());

        // Another version is released
        version = Version.fromString("5.2");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        assertEquals("Application has pending upgrade to " + version, version, tester.versionChange(app.id()).get().version());

        // Consume system-test job for 5.2
        tester.buildSystem().takeJobsToRun();

        // Failure re-deployer does not retry failing system-test job as it failed for an older change
        tester.clock().advance(Duration.ofMinutes(5));
        tester.failureRedeployer().maintain();
        assertTrue("No jobs retried", tester.buildSystem().jobs().isEmpty());
    }

}
