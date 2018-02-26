// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.SystemName;
import com.yahoo.log.event.Collection;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;

import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.component;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.productionUsEast3;
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
        tester.jobCompletion(component).application(app).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.systemTest);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.stagingTest);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.productionUsEast3);

        // New version is released
        version = Version.fromString("5.1");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();

        // Test environments pass
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.systemTest);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.stagingTest);

        // Production job fails and is retried
        tester.clock().advance(Duration.ofSeconds(1)); // Advance time so that we can detect jobs in progress
        tester.deployAndNotify(app, applicationPackage, false, DeploymentJobs.JobType.productionUsEast3);
        assertEquals("Production job is retried", 1, tester.deploymentQueue().jobs().size());
        assertEquals("Application has pending upgrade to " + version, version, tester.application(app.id()).change().platform().get());

        // Another version is released, which cancels any pending upgrades to lower versions
        version = Version.fromString("5.2");
        tester.updateVersionStatus(version);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.productionUsEast3); // Finish previous production job.
        tester.upgrader().maintain();
        assertEquals("Application starts upgrading to new version", 1, tester.deploymentQueue().jobs().size());
        assertEquals("Application has pending upgrade to " + version, version, tester.application(app.id()).change().platform().get());

        // Failure redeployer does not retry failing job for prod.us-east-3 as there's an ongoing deployment
        tester.clock().advance(Duration.ofMinutes(1));
        tester.readyJobTrigger().maintain();
        assertFalse("Job is not retried", tester.deploymentQueue().jobs().stream()
                .anyMatch(j -> j.jobName().equals(DeploymentJobs.JobType.productionUsEast3.jobName())));

        // Test environments pass
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.systemTest);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.stagingTest);

        // Production job fails again and exhausts all immediate retries
        tester.deployAndNotify(app, applicationPackage, false, DeploymentJobs.JobType.productionUsEast3);
        tester.deploymentQueue().takeJobsToRun();
        tester.clock().advance(Duration.ofMinutes(10));
        tester.jobCompletion(DeploymentJobs.JobType.productionUsEast3).application(app).unsuccessful().submit();
        assertTrue("Retries exhausted", tester.deploymentQueue().jobs().isEmpty());
        assertTrue("Failure is recorded", tester.application(app.id()).deploymentJobs().hasFailures());

        // Failure redeployer retries job
        tester.clock().advance(Duration.ofMinutes(5));
        tester.readyJobTrigger().maintain();
        assertEquals("Job is retried", Collections.singletonList(new BuildService.BuildJob(app.deploymentJobs().projectId().get(), productionUsEast3.jobName())), tester.deploymentQueue().jobs());

        // Production job finally succeeds
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.productionUsEast3);
        assertTrue("All jobs consumed", tester.deploymentQueue().jobs().isEmpty());
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
        tester.jobCompletion(component).application(app).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.systemTest);

        // staging-test starts, but does not complete
        assertEquals(DeploymentJobs.JobType.stagingTest.jobName(), tester.deploymentQueue().takeJobsToRun().get(0).jobName());
        tester.readyJobTrigger().maintain();
        assertTrue("No jobs retried", tester.deploymentQueue().jobs().isEmpty());

        // Just over 12 hours pass, job is retried
        tester.clock().advance(Duration.ofHours(12).plus(Duration.ofSeconds(1)));
        tester.readyJobTrigger().maintain();
        assertEquals(DeploymentJobs.JobType.stagingTest.jobName(), tester.deploymentQueue().takeJobsToRun().get(0).jobName());

        // Deployment completes
        tester.deploy(DeploymentJobs.JobType.stagingTest, app, applicationPackage, true);
        tester.jobCompletion(DeploymentJobs.JobType.stagingTest).application(app).submit();
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.productionUsEast3);
        assertTrue("All jobs consumed", tester.deploymentQueue().jobs().isEmpty());
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
        tester.jobCompletion(component).application(app).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.systemTest);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.stagingTest);
        tester.deployAndNotify(app, applicationPackage, true, DeploymentJobs.JobType.productionUsEast3);

        // New version is released
        version = Version.fromString("5.1");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        assertEquals("Application has pending upgrade to " + version, version, tester.application(app.id()).change().platform().get());

        // system-test fails and exhausts all immediate retries
        tester.deployAndNotify(app, applicationPackage, false, DeploymentJobs.JobType.systemTest);
        tester.deploymentQueue().takeJobsToRun();
        tester.clock().advance(Duration.ofMinutes(10));
        tester.jobCompletion(DeploymentJobs.JobType.systemTest).application(app).unsuccessful().submit();
        assertTrue("Retries exhausted", tester.deploymentQueue().jobs().isEmpty());

        // Another version is released
        version = Version.fromString("5.2");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();
        assertEquals("Application has pending upgrade to " + version, version, tester.application(app.id()).change().platform().get());

        // Consume system-test job for 5.2
        tester.deploymentQueue().takeJobsToRun();

        // Failure re-deployer does not retry failing system-test job as it failed for an older change
        tester.clock().advance(Duration.ofMinutes(5));
        tester.readyJobTrigger().maintain();
        assertTrue("No jobs retried", tester.deploymentQueue().jobs().isEmpty());
    }

    @Test
    public void retryIgnoresStaleJobData() throws Exception {
        DeploymentTester tester = new DeploymentTester();
        tester.controllerTester().zoneRegistry().setSystem(SystemName.cd);
        tester.controllerTester().zoneRegistry().setZones(ZoneId.from("prod", "cd-us-central-1"));

        // Current system version, matches version in test data
        Version version = Version.fromString("6.141.117");
        tester.configServer().setDefaultVersion(version);
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());

        // Load test data data
        byte[] json = Files.readAllBytes(Paths.get("src/test/java/com/yahoo/vespa/hosted/controller/maintenance/testdata/canary-with-stale-data.json"));
        Slime slime = SlimeUtils.jsonToSlime(json);
        Application application = tester.controllerTester().createApplication(slime);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("canary")
                .region("cd-us-central-1")
                .build();
        tester.jobCompletion(component).application(application).uploadArtifact(applicationPackage).submit();

        // New version is released
        version = Version.fromString("6.142.1");
        tester.configServer().setDefaultVersion(version);
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();

        // Test environments pass
        tester.deploy(DeploymentJobs.JobType.systemTest, application, applicationPackage);
        tester.deploymentQueue().takeJobsToRun();
        tester.clock().advance(Duration.ofMinutes(10));
        tester.jobCompletion(DeploymentJobs.JobType.systemTest).application(application).submit();

        tester.deploy(DeploymentJobs.JobType.stagingTest, application, applicationPackage);
        tester.deploymentQueue().takeJobsToRun();
        tester.clock().advance(Duration.ofMinutes(10));
        tester.jobCompletion(DeploymentJobs.JobType.stagingTest).application(application).submit();

        // Production job starts, but does not complete
        assertEquals(1, tester.deploymentQueue().jobs().size());
        assertEquals("Production job triggered", DeploymentJobs.JobType.productionCdUsCentral1.jobName(), tester.deploymentQueue().jobs().get(0).jobName());
        tester.deploymentQueue().takeJobsToRun();

        // Failure re-deployer runs
        tester.readyJobTrigger().maintain();
        assertTrue("No jobs retried", tester.deploymentQueue().jobs().isEmpty());

        // Deployment notifies completeness but has not actually made a deployment
        tester.jobCompletion(DeploymentJobs.JobType.productionCdUsCentral1).application(application).submit();
        assertTrue("Change not really deployed", tester.application(application.id()).change().isPresent());

        // Deployment actually deploys and notifies completeness
        tester.deploy(DeploymentJobs.JobType.productionCdUsCentral1, application, applicationPackage);
        tester.jobCompletion(DeploymentJobs.JobType.productionCdUsCentral1).application(application).submit();
        assertFalse("Change not really deployed", tester.application(application.id()).change().isPresent());
    }

    @Test
    public void ignoresPullRequestInstances() throws Exception {
        DeploymentTester tester = new DeploymentTester();
        tester.controllerTester().zoneRegistry().setSystem(SystemName.cd);

        // Current system version, matches version in test data
        Version version = Version.fromString("6.42.1");
        tester.configServer().setDefaultVersion(version);
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());

        // Load test data data
        byte[] json = Files.readAllBytes(Paths.get("src/test/java/com/yahoo/vespa/hosted/controller/maintenance/testdata/pr-instance-with-dead-locked-job.json"));
        Slime slime = SlimeUtils.jsonToSlime(json);
        Application application = tester.controllerTester().createApplication(slime);

        // Failure redeployer does not restart deployment
        tester.readyJobTrigger().maintain();
        assertTrue("No jobs scheduled", tester.deploymentQueue().jobs().isEmpty());
    }

    @Test
    public void applicationWithoutProjectIdIsNotTriggered() throws Exception {
        DeploymentTester tester = new DeploymentTester();

        // Current system version, matches version in test data
        Version version = Version.fromString("6.42.1");
        tester.configServer().setDefaultVersion(version);
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());

        // Load test data data
        byte[] json = Files.readAllBytes(Paths.get("src/test/java/com/yahoo/vespa/hosted/controller/maintenance/testdata/application-without-project-id.json"));
        Slime slime = SlimeUtils.jsonToSlime(json);
        tester.controllerTester().createApplication(slime);

        // Failure redeployer does not restart deployment
        tester.readyJobTrigger().maintain();
        assertTrue("No jobs scheduled", tester.deploymentQueue().jobs().isEmpty());
    }

}
