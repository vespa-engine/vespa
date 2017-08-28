// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 * @author mpolden
 */
public class DeploymentTriggerTest {

    @Test
    public void testTriggerFailing() {
        DeploymentTester tester = new DeploymentTester();
        Application app1 = tester.createAndDeploy("app1", 1, "default");

        Version version = new Version(5, 2);
        tester.deploymentTrigger().triggerChange(app1.id(), new Change.VersionChange(version));
        tester.completeUpgradeWithError(app1, version, "default", JobType.stagingTest);
        assertEquals("Retried immediately", 1, tester.buildSystem().jobs().size());

        tester.buildSystem().takeJobsToRun();
        assertEquals("Job removed", 0, tester.buildSystem().jobs().size());        
        tester.clock().advance(Duration.ofHours(2));
        tester.deploymentTrigger().triggerFailing(app1.id(), "unit test");
        assertEquals("Retried job", 1, tester.buildSystem().jobs().size());
        assertEquals(JobType.stagingTest.id(), tester.buildSystem().jobs().get(0).jobName());

        tester.buildSystem().takeJobsToRun();
        assertEquals("Job removed", 0, tester.buildSystem().jobs().size());
        tester.clock().advance(Duration.ofHours(7));
        tester.deploymentTrigger().triggerFailing(app1.id(), "unit test");
        assertEquals("Retried from the beginning", 1, tester.buildSystem().jobs().size());
        assertEquals(JobType.component.id(), tester.buildSystem().jobs().get(0).jobName());
    }

    @Test
    public void deploymentSpecDecidesTriggerOrder() {
        DeploymentTester tester = new DeploymentTester();
        BuildSystem buildSystem = tester.buildSystem();
        TenantId tenant = tester.controllerTester().createTenant("tenant1", "domain1", 1L);
        Application application = tester.controllerTester().createApplication(tenant, "app1", "default", 1L);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("corp-us-east-1")
                .region("us-central-1")
                .region("us-west-1")
                .build();

        // Component job finishes
        tester.notifyJobCompletion(JobType.component, application, true);

        // Application is deployed to all test environments and declared zones
        tester.deployAndNotify(JobType.systemTest, application, applicationPackage, true);
        tester.deployAndNotify(JobType.stagingTest, application, applicationPackage, true);
        tester.deployAndNotify(JobType.productionCorpUsEast1, application, applicationPackage, true);
        tester.deployAndNotify(JobType.productionUsCentral1, application, applicationPackage, true);
        tester.deployAndNotify(JobType.productionUsWest1, application, applicationPackage, true);
        assertTrue("All jobs consumed", buildSystem.jobs().isEmpty());
    }

    @Test
    public void deploymentsSpecWithDelays() {
        DeploymentTester tester = new DeploymentTester();
        BuildSystem buildSystem = tester.buildSystem();
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
        tester.notifyJobCompletion(JobType.component, application, true);

        // Test jobs pass
        tester.deployAndNotify(JobType.systemTest, application, applicationPackage, true);
        tester.clock().advance(Duration.ofSeconds(1)); // Make staging test sort as the last successful job
        tester.deployAndNotify(JobType.stagingTest, application, applicationPackage, true);
        assertTrue("No more jobs triggered at this time", buildSystem.jobs().isEmpty());

        // 30 seconds pass, us-west-1 is triggered
        tester.clock().advance(Duration.ofSeconds(30));
        tester.deploymentTrigger().triggerDelayed();

        // Consume us-west-1 job without reporting completion
        assertEquals(1, buildSystem.jobs().size());
        assertEquals(JobType.productionUsWest1.id(), buildSystem.jobs().get(0).jobName());
        buildSystem.takeJobsToRun();

        // 3 minutes pass, delayed trigger does nothing as us-west-1 is still in progress
        tester.clock().advance(Duration.ofMinutes(3));
        tester.deploymentTrigger().triggerDelayed();
        assertTrue("No more jobs triggered at this time", buildSystem.jobs().isEmpty());

        // us-west-1 completes
        tester.deploy(JobType.productionUsWest1, application, applicationPackage);
        tester.notifyJobCompletion(JobType.productionUsWest1, application, true);

        // Delayed trigger does nothing as not enough time has passed after us-west-1 completion
        tester.deploymentTrigger().triggerDelayed();
        assertTrue("No more jobs triggered at this time", buildSystem.jobs().isEmpty());

        // 3 minutes pass, us-central-1 is triggered
        tester.clock().advance(Duration.ofMinutes(3));
        tester.deploymentTrigger().triggerDelayed();
        tester.deployAndNotify(JobType.productionUsCentral1, application, applicationPackage, true);
        assertTrue("All jobs consumed", buildSystem.jobs().isEmpty());

        // Delayed trigger job runs again, with nothing to trigger
        tester.clock().advance(Duration.ofMinutes(10));
        tester.deploymentTrigger().triggerDelayed();
        assertTrue("All jobs consumed", buildSystem.jobs().isEmpty());
    }


    @Test
    public void testSuccessfulDeploymentApplicationPackageChanged() {
        DeploymentTester tester = new DeploymentTester();
        BuildSystem buildSystem = tester.buildSystem();
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
                .region("ap-northeast-1")
                .build();

        // Component job finishes
        tester.notifyJobCompletion(JobType.component, application, true);

        // Application is deployed to all test environments and declared zones
        tester.deployAndNotify(JobType.systemTest, application, newApplicationPackage, true);
        tester.deploy(JobType.stagingTest, application, previousApplicationPackage, true);
        tester.deployAndNotify(JobType.stagingTest, application, newApplicationPackage, true);
        tester.deployAndNotify(JobType.productionCorpUsEast1, application, newApplicationPackage, true);
        tester.deployAndNotify(JobType.productionUsCentral1, application, newApplicationPackage, true);
        tester.deployAndNotify(JobType.productionUsWest1, application, newApplicationPackage, true);
        tester.deployAndNotify(JobType.productionApNortheast1, application, newApplicationPackage, true);
        assertTrue("All jobs consumed", buildSystem.jobs().isEmpty());
    }
}
