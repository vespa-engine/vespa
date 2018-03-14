// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.NToken;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.Tenant;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.EndpointStatus;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ScrewdriverBuildJob;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.identifiers.ScrewdriverId;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserGroup;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobError;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.application.SourceRevision;
import com.yahoo.vespa.hosted.controller.athenz.mock.AthenzDbMock;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentQueue;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.deployment.BuildJob;
import com.yahoo.vespa.hosted.controller.persistence.ApplicationSerializer;
import com.yahoo.vespa.hosted.controller.rotation.RotationId;
import com.yahoo.vespa.hosted.controller.rotation.RotationLock;
import com.yahoo.vespa.hosted.controller.versions.DeploymentStatistics;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.component;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.productionCorpUsEast1;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.productionUsEast3;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.productionUsWest1;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.systemTest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 * @author mpolden
 */
public class ControllerTest {

    private static final ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
            .environment(Environment.prod)
            .region("corp-us-east-1")
            .build();

    private static final ApplicationPackage applicationPackage2 = new ApplicationPackageBuilder()
            .environment(Environment.prod)
            .region("corp-us-east-1")
            .region("us-west-1")
            .build();

    @Test
    public void testDeployment() {
        // Setup system
        DeploymentTester tester = new DeploymentTester();
        ApplicationController applications = tester.controller().applications();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("corp-us-east-1")
                .region("us-east-3")
                .build();

        // staging job - succeeding
        Version version1 = tester.defaultVespaVersion();
        Application app1 = tester.createApplication("app1", "tenant1", 1, 11L);
        tester.jobCompletion(component).application(app1).uploadArtifact(applicationPackage).submit();
        assertEquals("Application version is known from completion of initial job",
                     ApplicationVersion.from(BuildJob.defaultSourceRevision, BuildJob.defaultBuildNumber),
                     tester.controller().applications().require(app1.id()).change().application().get());
        tester.deployAndNotify(app1, applicationPackage, true, systemTest);
        tester.deployAndNotify(app1, applicationPackage, true, stagingTest);
        assertEquals(4, applications.require(app1.id()).deploymentJobs().jobStatus().size());

        ApplicationVersion applicationVersion = tester.controller().applications().require(app1.id()).change().application().get();
        assertTrue("Application version has been set during deployment", applicationVersion != ApplicationVersion.unknown);
        assertStatus(JobStatus.initial(stagingTest)
                              .withTriggering(version1, applicationVersion, "", tester.clock().instant().minus(Duration.ofMillis(1)))
                              .withCompletion(42, Optional.empty(), tester.clock().instant(), tester.controller()), app1.id(), tester.controller());

        // Causes first deployment job to be triggered
        assertStatus(JobStatus.initial(productionCorpUsEast1)
                              .withTriggering(version1, applicationVersion, "", tester.clock().instant()), app1.id(), tester.controller());
        tester.clock().advance(Duration.ofSeconds(1));

        // production job (failing)
        tester.deployAndNotify(app1, applicationPackage, false, productionCorpUsEast1);
        assertEquals(4, applications.require(app1.id()).deploymentJobs().jobStatus().size());

        JobStatus expectedJobStatus = JobStatus.initial(productionCorpUsEast1)
                                               .withTriggering(version1, applicationVersion, "", tester.clock().instant()) // Triggered first without application version info
                                               .withCompletion(42, Optional.of(JobError.unknown), tester.clock().instant(), tester.controller())
                                               .withTriggering(version1, applicationVersion, "", tester.clock().instant()); // Re-triggering (due to failure) has application version info

        assertStatus(expectedJobStatus, app1.id(), tester.controller());

        // Simulate restart
        tester.restartController();
        applications = tester.controller().applications();

        assertNotNull(tester.controller().tenants().tenant(new TenantId("tenant1")));
        assertNotNull(applications.get(ApplicationId.from(TenantName.from("tenant1"),
                                                          ApplicationName.from("application1"),
                                                          InstanceName.from("default"))));
        assertEquals(4, applications.require(app1.id()).deploymentJobs().jobStatus().size());


        tester.clock().advance(Duration.ofHours(1));

        // Need to complete the job, or new jobs won't start.
        tester.jobCompletion(productionCorpUsEast1).application(app1).unsuccessful().submit();

        // system and staging test job - succeeding
        tester.jobCompletion(component).application(app1).submit();
        tester.deployAndNotify(app1, applicationPackage, true, false, systemTest);
        assertStatus(JobStatus.initial(systemTest)
                              .withTriggering(version1, applicationVersion, "", tester.clock().instant().minus(Duration.ofMillis(1)))
                              .withCompletion(42, Optional.empty(), tester.clock().instant(), tester.controller()), app1.id(), tester.controller());
        tester.deployAndNotify(app1, applicationPackage, true, stagingTest);

        // production job succeeding now
        tester.deployAndNotify(app1, applicationPackage, true, productionCorpUsEast1);
        expectedJobStatus = expectedJobStatus
                .withTriggering(version1, applicationVersion, "", tester.clock().instant().minus(Duration.ofMillis(1)))
                .withCompletion(42, Optional.empty(), tester.clock().instant(), tester.controller());
        assertStatus(expectedJobStatus, app1.id(), tester.controller());

        // causes triggering of next production job
        assertStatus(JobStatus.initial(productionUsEast3)
                              .withTriggering(version1, applicationVersion, "", tester.clock().instant()),
                     app1.id(), tester.controller());
        tester.deployAndNotify(app1, applicationPackage, true, productionUsEast3);

        assertEquals(5, applications.get(app1.id()).get().deploymentJobs().jobStatus().size());

        // prod zone removal is not allowed
        applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-east-3")
                .build();
        tester.jobCompletion(component).application(app1).buildNumber(43).uploadArtifact(applicationPackage).submit();
        try {
            tester.deploy(systemTest, app1, applicationPackage);
            fail("Expected exception due to unallowed production deployment removal");
        }
        catch (IllegalArgumentException e) {
            assertEquals("deployment-removal: application 'tenant1.app1' is deployed in corp-us-east-1, but does not include this zone in deployment.xml", e.getMessage());
        }
        assertNotNull("Zone was not removed",
                      applications.require(app1.id()).deployments().get(productionCorpUsEast1.zone(SystemName.main).get()));
        JobStatus jobStatus = applications.require(app1.id()).deploymentJobs().jobStatus().get(productionCorpUsEast1);
        assertNotNull("Deployment job was not removed", jobStatus);
        assertEquals(42, jobStatus.lastCompleted().get().id());
        assertEquals("staging-test completed", jobStatus.lastCompleted().get().reason());

        // prod zone removal is allowed with override
        applicationPackage = new ApplicationPackageBuilder()
                .allow(ValidationId.deploymentRemoval)
                .upgradePolicy("default")
                .environment(Environment.prod)
                .region("us-east-3")
                .build();
        tester.jobCompletion(component).application(app1).buildNumber(44).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app1, applicationPackage, true, systemTest);
        assertNull("Zone was removed",
                   applications.require(app1.id()).deployments().get(productionCorpUsEast1.zone(SystemName.main).get()));
        assertNull("Deployment job was removed", applications.require(app1.id()).deploymentJobs().jobStatus().get(productionCorpUsEast1));
    }

    @Test
    public void testDeploymentApplicationVersion() {
        DeploymentTester tester = new DeploymentTester();
        Application app = tester.createApplication("app1", "tenant1", 1, 11L);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("corp-us-east-1")
                .region("us-east-3")
                .build();
        SourceRevision source = new SourceRevision("repo", "master", "commit1");

        ApplicationVersion applicationVersion = ApplicationVersion.from(source, 101);
        runDeployment(tester, app.id(), applicationVersion, applicationPackage, source,101);
        assertEquals("Artifact is downloaded twice in staging and once for other zones", 5,
                     tester.artifactRepository().hits(app.id(), applicationVersion.id()));

        // Application is upgraded. This makes deployment orchestration pick the last successful application version in
        // zones which do not have permanent deployments, e.g. test and staging
        runUpgrade(tester, app.id(), applicationVersion);
    }

    @Test
    public void testDeployVersion() {
        // Setup system
        DeploymentTester tester = new DeploymentTester();
        ApplicationController applications = tester.controller().applications();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .build();
        Version systemVersion = tester.controller().versionStatus().systemVersion().get().versionNumber();

        Application app1 = tester.createApplication("application1", "tenant1", 1, 1L);

        // First deployment: An application change
        tester.jobCompletion(component).application(app1).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app1, applicationPackage, true, systemTest);
        tester.deployAndNotify(app1, applicationPackage, true, stagingTest);
        tester.deployAndNotify(app1, applicationPackage, true, productionUsWest1);

        app1 = applications.require(app1.id());
        assertEquals("First deployment gets system version", systemVersion, app1.oldestDeployedVersion().get());
        assertEquals(systemVersion, tester.configServer().lastPrepareVersion().get());

        // Unexpected deployment
        tester.deploy(productionUsWest1, app1, applicationPackage);
        // applications are immutable, so any change to one, including deployment changes, would give rise to a new instance.
        assertEquals("Unexpected deployment is ignored", app1, applications.require(app1.id()));

        // Application change after a new system version, and a region added
        Version newSystemVersion = incrementSystemVersion(tester.controller());
        assertTrue(newSystemVersion.isAfter(systemVersion));

        applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .region("us-east-3")
                .build();
        tester.jobCompletion(component).application(app1).buildNumber(43).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app1, applicationPackage, true, systemTest);
        tester.deployAndNotify(app1, applicationPackage, true, stagingTest);
        tester.deployAndNotify(app1, applicationPackage, true, productionUsWest1);

        app1 = applications.require(app1.id());
        assertEquals("Application change preserves version", systemVersion, app1.oldestDeployedVersion().get());
        assertEquals(systemVersion, tester.configServer().lastPrepareVersion().get());

        // A deployment to the new region gets the same version
        tester.deployAndNotify(app1, applicationPackage, true, productionUsEast3);
        app1 = applications.require(app1.id());
        assertEquals("Application change preserves version", systemVersion, app1.oldestDeployedVersion().get());
        assertEquals(systemVersion, tester.configServer().lastPrepareVersion().get());
        assertFalse("Change deployed", app1.change().isPresent());

        // Version upgrade changes system version
        applications.deploymentTrigger().triggerChange(app1.id(), Change.of(newSystemVersion));
        tester.deployAndNotify(app1, applicationPackage, true, systemTest);
        tester.deployAndNotify(app1, applicationPackage, true, stagingTest);
        tester.deployAndNotify(app1, applicationPackage, true, productionUsWest1);
        tester.deployAndNotify(app1, applicationPackage, true, productionUsEast3);

        app1 = applications.require(app1.id());
        assertEquals("Version upgrade changes version", newSystemVersion, app1.oldestDeployedVersion().get());
        assertEquals(newSystemVersion, tester.configServer().lastPrepareVersion().get());
    }

    /** Adds a new version, higher than the current system version, makes it the system version and returns it */
    private Version incrementSystemVersion(Controller controller) {
        Version systemVersion = controller.versionStatus().systemVersion().get().versionNumber();
        Version newSystemVersion = new Version(systemVersion.getMajor(), systemVersion.getMinor()+1, 0);
        VespaVersion newSystemVespaVersion = new VespaVersion(DeploymentStatistics.empty(newSystemVersion),
                                                              "commit1",
                                                              Instant.now(),
                                                              true,
                                                              Collections.emptyList(),
                                                              VespaVersion.Confidence.low
        );
        List<VespaVersion> versions = new ArrayList<>(controller.versionStatus().versions());
        for (int i = 0; i < versions.size(); i++) {
            VespaVersion c = versions.get(i);
            if (c.isCurrentSystemVersion())
                versions.set(i, new VespaVersion(c.statistics(), c.releaseCommit(), c.committedAt(),
                                                 false, c.configServerHostnames(),
                                                 c.confidence()));
        }
        versions.add(newSystemVespaVersion);
        controller.updateVersionStatus(new VersionStatus(versions));
        return newSystemVersion;
    }

    @Test
    public void testPullRequestDeployment() {
        // Setup system
        ControllerTester tester = new ControllerTester();
        ApplicationController applications = tester.controller().applications();

        // staging deployment
        long app1ProjectId = 22;
        ApplicationId app1 = tester.createAndDeploy("tenant1",  "domain1",
                                                    "application1", Environment.staging,
                                                    app1ProjectId).id();

        // pull-request deployment - uses different instance id
        ApplicationId app1pr = tester.createAndDeploy("tenant1",  "domain1",
                                                      "application1", "1",
                                                      Environment.staging, app1ProjectId, null).id();

        assertTrue(applications.get(app1).isPresent());
        assertEquals(app1, applications.get(app1).get().id());
        assertTrue(applications.get(app1pr).isPresent());
        assertEquals(app1pr, applications.get(app1pr).get().id());

        // Simulate restart
        tester.createNewController();
        applications = tester.controller().applications();

        assertTrue(applications.get(app1).isPresent());
        assertEquals(app1, applications.get(app1).get().id());
        assertTrue(applications.get(app1pr).isPresent());
        assertEquals(app1pr, applications.get(app1pr).get().id());

        // Deleting application also removes PR instance
        ApplicationId app2 = tester.createAndDeploy("tenant1",  "domain1",
                                                    "application2", Environment.staging,
                                                    33).id();
        tester.controller().applications().deleteApplication(app1, Optional.of(new NToken("ntoken")));
        assertEquals("All instances deleted", 0,
                     tester.controller().applications().asList(app1.tenant()).stream()
                                                    .filter(app -> app.id().application().equals(app1.application()))
                                                    .count());
        assertEquals("Other application survives", 1,
                     tester.controller().applications().asList(app1.tenant()).stream()
                           .filter(app -> app.id().application().equals(app2.application()))
                           .count());
    }

    @Test
    public void testFailingSinceUpdates() {
        // Setup system
        DeploymentTester tester = new DeploymentTester();

        // Setup application
        Application app = tester.createApplication("app1", "foo", 1, 1L);

        // Initial failure
        Instant initialFailure = tester.clock().instant();
        tester.jobCompletion(component).application(app).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app, applicationPackage, false, systemTest);
        assertEquals("Failure age is right at initial failure",
                     initialFailure.plus(Duration.ofMillis(2)), firstFailing(app, tester).get().at());

        // Failure again -- failingSince should remain the same
        tester.clock().advance(Duration.ofMillis(1000));
        tester.deployAndNotify(app, applicationPackage, false, systemTest);
        assertEquals("Failure age is right at second consecutive failure",
                     initialFailure.plus(Duration.ofMillis(2)), firstFailing(app, tester).get().at());

        // Success resets failingSince
        tester.clock().advance(Duration.ofMillis(1000));
        tester.deployAndNotify(app, applicationPackage, true, systemTest);
        assertFalse(firstFailing(app, tester).isPresent());

        // Complete deployment
        tester.deployAndNotify(app, applicationPackage, true, stagingTest);
        tester.deployAndNotify(app, applicationPackage, true, productionCorpUsEast1);

        // Two repeated failures again.
        // Initial failure
        tester.clock().advance(Duration.ofMillis(1000));
        initialFailure = tester.clock().instant();
        tester.jobCompletion(component).application(app).submit();
        tester.deployAndNotify(app, applicationPackage, false, systemTest);
        assertEquals("Failure age is right at initial failure",
                     initialFailure.plus(Duration.ofMillis(2)), firstFailing(app, tester).get().at());

        // Failure again -- failingSince should remain the same
        tester.clock().advance(Duration.ofMillis(1000));
        tester.deployAndNotify(app, applicationPackage, false, systemTest);
        assertEquals("Failure age is right at second consecutive failure",
                     initialFailure.plus(Duration.ofMillis(2)), firstFailing(app, tester).get().at());
    }

    private Optional<JobStatus.JobRun> firstFailing(Application application, DeploymentTester tester) {
        return tester.controller().applications().get(application.id()).get().deploymentJobs().jobStatus().get(systemTest).firstFailing();
    }

    @Test
    public void requeueOutOfCapacityStagingJob() {
        DeploymentTester tester = new DeploymentTester();

        long project1 = 1;
        long project2 = 2;
        long project3 = 3;
        Application app1 = tester.createApplication("app1", "tenant1", project1, 1L);
        Application app2 = tester.createApplication("app2", "tenant2", project2, 1L);
        Application app3 = tester.createApplication("app3", "tenant3", project3, 1L);
        DeploymentQueue deploymentQueue = tester.controller().applications().deploymentTrigger().deploymentQueue();

        // all applications: system-test completes successfully
        tester.jobCompletion(component).application(app1).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app1, applicationPackage, true, systemTest);

        tester.jobCompletion(component).application(app2).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app2, applicationPackage, true, systemTest);

        tester.jobCompletion(component).application(app3).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app3, applicationPackage, true, systemTest);

        // all applications: staging test jobs queued
        assertEquals(3, deploymentQueue.jobs().size());

        // app1: staging-test job fails with out of capacity and is added to the front of the queue
        tester.deploy(stagingTest, app1, applicationPackage);
        tester.jobCompletion(stagingTest)
              .application(app1)
              .error(JobError.outOfCapacity)
              .submit();
        assertEquals(stagingTest.jobName(), deploymentQueue.jobs().get(0).jobName());
        assertEquals(project1, deploymentQueue.jobs().get(0).projectId());

        // app2 and app3: Completes deployment
        tester.deployAndNotify(app2, applicationPackage, true, stagingTest);
        tester.deployAndNotify(app2, applicationPackage, true, productionCorpUsEast1);
        tester.deployAndNotify(app3, applicationPackage, true, stagingTest);
        tester.deployAndNotify(app3, applicationPackage, true, productionCorpUsEast1);

        // app1: 15 minutes pass, staging-test job is still failing due out of capacity, but is no longer re-queued by
        // out of capacity retry mechanism
        tester.clock().advance(Duration.ofMinutes(15));
        tester.jobCompletion(stagingTest).application(app1).error(JobError.outOfCapacity).submit(); // Clear the previous staging test
        tester.jobCompletion(component).application(app1).buildNumber(43).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app1, applicationPackage, true, false, systemTest);
        tester.deploy(stagingTest, app1, applicationPackage);
        assertEquals(1, deploymentQueue.takeJobsToRun().size());
        tester.jobCompletion(stagingTest).application(app1).error(JobError.outOfCapacity).submit();
        assertTrue("No jobs queued", deploymentQueue.jobs().isEmpty());

        // app2 and app3: New change triggers system-test jobs
        // Provide a changed application package, too, or the deployment is a no-op.
        tester.jobCompletion(component).application(app2).buildNumber(43).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app2, applicationPackage2, true, systemTest);

        tester.jobCompletion(component).application(app3).buildNumber(43).uploadArtifact(applicationPackage).submit();
        tester.deployAndNotify(app3, applicationPackage2, true, systemTest);

        assertEquals(2, deploymentQueue.jobs().size());

        // app1: 4 hours pass in total, staging-test job for app1 is re-queued by periodic trigger mechanism and added at the
        // back of the queue
        tester.clock().advance(Duration.ofHours(3));
        tester.clock().advance(Duration.ofMinutes(50));
        tester.readyJobTrigger().maintain();

        assertEquals(Collections.singletonList(new BuildService.BuildJob(project2, stagingTest.jobName())), deploymentQueue.takeJobsToRun());
        assertEquals(Collections.singletonList(new BuildService.BuildJob(project3, stagingTest.jobName())), deploymentQueue.takeJobsToRun());
        assertEquals(Collections.singletonList(new BuildService.BuildJob(project1, stagingTest.jobName())), deploymentQueue.takeJobsToRun());
        assertEquals(Collections.emptyList(), deploymentQueue.takeJobsToRun());
    }

    private void assertStatus(JobStatus expectedStatus, ApplicationId id, Controller controller) {
        Application app = controller.applications().get(id).get();
        JobStatus existingStatus = app.deploymentJobs().jobStatus().get(expectedStatus.type());
        assertNotNull("Status of type " + expectedStatus.type() + " is present", existingStatus);
        assertEquals(expectedStatus, existingStatus);
    }

    @Test
    public void testGlobalRotations() throws IOException {
        // Setup tester and app def
        ControllerTester tester = new ControllerTester();
        ZoneId zone = ZoneId.from(Environment.defaultEnvironment(), RegionName.defaultName());
        ApplicationId appId = ApplicationId.from("tenant", "app1", "default");
        DeploymentId deployId = new DeploymentId(appId, zone);

        // Check initial rotation status
        Map<String, EndpointStatus> rotationStatus = tester.controller().applications().getGlobalRotationStatus(deployId);
        assertEquals(1, rotationStatus.size());

        assertTrue(rotationStatus.get("qrs-endpoint").getStatus().equals(EndpointStatus.Status.in));

        // Set the global rotations out of service
        EndpointStatus status = new EndpointStatus(EndpointStatus.Status.out, "Testing I said", "Test", tester.clock().instant().getEpochSecond());
        List<String> overrides = tester.controller().applications().setGlobalRotationStatus(deployId, status);
        assertEquals(1, overrides.size());

        // Recheck the override rotation status
        rotationStatus = tester.controller().applications().getGlobalRotationStatus(deployId);
        assertEquals(1, rotationStatus.size());
        assertTrue(rotationStatus.get("qrs-endpoint").getStatus().equals(EndpointStatus.Status.out));
        assertTrue(rotationStatus.get("qrs-endpoint").getReason().equals("Testing I said"));
    }

    @Test
    public void testDeployUntestedChangeFails() {
        DeploymentTester tester = new DeploymentTester();
        ApplicationController applications = tester.controller().applications();
        TenantId tenant = tester.controllerTester().createTenant("tenant1", "domain1", 11L);
        Application app = tester.controllerTester().createApplication(tenant, "app1", "default", 1);
        tester.deployCompletely(app, applicationPackage);

        tester.controller().applications().lockOrThrow(app.id(), application -> {
            application = application.withChange(Change.of(Version.fromString("6.3")));
            applications.store(application);
            try {
                tester.controllerTester().deploy(app, ZoneId.from("prod", "corp-us-east-1"), applicationPackage);
                fail("Expected exception");
            } catch (IllegalArgumentException e) {
                assertEquals("Rejecting deployment of application 'tenant1.app1' to zone prod.corp-us-east-1 as upgrade to 6.3 is not tested", e.getMessage());
            }
        });
    }

    @Test
    public void testCleanupOfStaleDeploymentData() throws IOException {
        DeploymentTester tester = new DeploymentTester();
        tester.controllerTester().zoneRegistry().setSystem(SystemName.cd);
        tester.controllerTester().zoneRegistry().setZones(ZoneId.from("prod", "cd-us-central-1"));

        Supplier<Map<JobType, JobStatus>> statuses = () ->
                tester.application(ApplicationId.from("vespa", "canary", "default"))
                      .deploymentJobs().jobStatus();

        // Current system version, matches version in test data
        Version version = Version.fromString("6.141.117");
        tester.configServer().setDefaultVersion(version);
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());

        // Load test data data
        byte[] json = Files.readAllBytes(Paths.get("src/test/java/com/yahoo/vespa/hosted/controller/maintenance/testdata/canary-with-stale-data.json"));
        Application application = tester.controllerTester().createApplication(SlimeUtils.jsonToSlime(json));

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("canary")
                .region("cd-us-central-1")
                .build();
        tester.jobCompletion(component).application(application).uploadArtifact(applicationPackage).submit();

        long cdJobsCount = statuses.get().keySet().stream()
                        .filter(type -> type.zone(SystemName.cd).isPresent())
                        .count();

        long mainJobsCount = statuses.get().keySet().stream()
                .filter(type -> type.zone(SystemName.main).isPresent() && ! type.zone(SystemName.cd).isPresent())
                .count();

        assertEquals("Irrelevant (main) data is present.", 8, mainJobsCount);

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
        tester.jobCompletion(systemTest).application(application).submit();

        long newCdJobsCount = statuses.get().keySet().stream()
                .filter(type -> type.zone(SystemName.cd).isPresent())
                .count();

        long newMainJobsCount = statuses.get().keySet().stream()
                .filter(type -> type.zone(SystemName.main).isPresent() && ! type.zone(SystemName.cd).isPresent())
                .count();

        assertEquals("Irrelevant (main) job data is removed.", 0, newMainJobsCount);
        assertEquals("Relevant (cd) data is not removed.", cdJobsCount, newCdJobsCount);
    }

    @Test
    public void testDnsAliasRegistration() {
        DeploymentTester tester = new DeploymentTester();
        Application application = tester.createApplication("app1", "tenant1", 1, 1L);

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .globalServiceId("foo")
                .region("us-west-1")
                .region("us-central-1") // Two deployments should result in each DNS alias being registered once
                .build();

        tester.deployCompletely(application, applicationPackage);
        assertEquals(2, tester.controllerTester().nameService().records().size());

        Optional<Record> record = tester.controllerTester().nameService().findRecord(
                Record.Type.CNAME, RecordName.from("app1--tenant1.global.vespa.yahooapis.com")
                                                                                    );
        assertTrue(record.isPresent());
        assertEquals("app1--tenant1.global.vespa.yahooapis.com", record.get().name().asString());
        assertEquals("rotation-fqdn-01.", record.get().data().asString());

       record = tester.controllerTester().nameService().findRecord(
                Record.Type.CNAME, RecordName.from("app1.tenant1.global.vespa.yahooapis.com")
        );
        assertTrue(record.isPresent());
        assertEquals("app1.tenant1.global.vespa.yahooapis.com", record.get().name().asString());
        assertEquals("rotation-fqdn-01.", record.get().data().asString());
    }

    @Test
    public void testUpdatesExistingDnsAlias() {
        DeploymentTester tester = new DeploymentTester();

        // Application 1 is deployed and deleted
        {
            Application app1 = tester.createApplication("app1", "tenant1", 1, 1L);
            ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                    .environment(Environment.prod)
                    .globalServiceId("foo")
                    .region("us-west-1")
                    .region("us-central-1") // Two deployments should result in each DNS alias being registered once
                    .build();

            tester.deployCompletely(app1, applicationPackage);
            assertEquals(2, tester.controllerTester().nameService().records().size());

            Optional<Record> record = tester.controllerTester().nameService().findRecord(
                    Record.Type.CNAME, RecordName.from("app1--tenant1.global.vespa.yahooapis.com")
                                                                                        );
            assertTrue(record.isPresent());
            assertEquals("app1--tenant1.global.vespa.yahooapis.com", record.get().name().asString());
            assertEquals("rotation-fqdn-01.", record.get().data().asString());

            record = tester.controllerTester().nameService().findRecord(
                    Record.Type.CNAME, RecordName.from("app1.tenant1.global.vespa.yahooapis.com")
            );
            assertTrue(record.isPresent());
            assertEquals("app1.tenant1.global.vespa.yahooapis.com", record.get().name().asString());
            assertEquals("rotation-fqdn-01.", record.get().data().asString());

            // Application is deleted and rotation is unassigned
            applicationPackage = new ApplicationPackageBuilder()
                    .environment(Environment.prod)
                    .allow(ValidationId.deploymentRemoval)
                    .build();
            tester.jobCompletion(component).application(app1).uploadArtifact(applicationPackage).submit();
            tester.deployAndNotify(app1, applicationPackage, true, systemTest);
            tester.applications().deactivate(app1, ZoneId.from(Environment.test, RegionName.from("us-east-1")));
            tester.applications().deactivate(app1, ZoneId.from(Environment.staging, RegionName.from("us-east-3")));
            tester.applications().deleteApplication(app1.id(), Optional.of(new NToken("ntoken")));
            try (RotationLock lock = tester.applications().rotationRepository().lock()) {
                assertTrue("Rotation is unassigned",
                           tester.applications().rotationRepository().availableRotations(lock)
                                 .containsKey(new RotationId("rotation-id-01")));
            }

            // Records remain
            record = tester.controllerTester().nameService().findRecord(
                    Record.Type.CNAME, RecordName.from("app1--tenant1.global.vespa.yahooapis.com")
            );
            assertTrue(record.isPresent());

            record = tester.controllerTester().nameService().findRecord(
                    Record.Type.CNAME, RecordName.from("app1.tenant1.global.vespa.yahooapis.com")
            );
            assertTrue(record.isPresent());
        }

        // Application 2 is deployed and assigned same rotation as application 1 had before deletion
        {
            Application app2 = tester.createApplication("app2", "tenant2", 1, 1L);
            ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                    .environment(Environment.prod)
                    .globalServiceId("foo")
                    .region("us-west-1")
                    .region("us-central-1")
                    .build();
            tester.deployCompletely(app2, applicationPackage);
            assertEquals(4, tester.controllerTester().nameService().records().size());

            Optional<Record> record = tester.controllerTester().nameService().findRecord(
                    Record.Type.CNAME, RecordName.from("app2--tenant2.global.vespa.yahooapis.com")
                                                                                        );
            assertTrue(record.isPresent());
            assertEquals("app2--tenant2.global.vespa.yahooapis.com", record.get().name().asString());
            assertEquals("rotation-fqdn-01.", record.get().data().asString());

            record = tester.controllerTester().nameService().findRecord(
                    Record.Type.CNAME, RecordName.from("app2.tenant2.global.vespa.yahooapis.com")
            );
            assertTrue(record.isPresent());
            assertEquals("app2.tenant2.global.vespa.yahooapis.com", record.get().name().asString());
            assertEquals("rotation-fqdn-01.", record.get().data().asString());

        }

        // Application 1 is recreated, deployed and assigned a new rotation
        {
            Application app1 = tester.createApplication("app1", "tenant1", 1, 1L);
            ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                    .environment(Environment.prod)
                    .globalServiceId("foo")
                    .region("us-west-1")
                    .region("us-central-1")
                    .build();
            tester.deployCompletely(app1, applicationPackage);
            app1 = tester.applications().require(app1.id());
            assertEquals("rotation-id-02", app1.rotation().get().id().asString());

            // Existing DNS records are updated to point to the newly assigned rotation
            assertEquals(4, tester.controllerTester().nameService().records().size());

            Optional<Record> record = tester.controllerTester().nameService().findRecord(
                    Record.Type.CNAME, RecordName.from("app1--tenant1.global.vespa.yahooapis.com")
                                                                                        );
            assertTrue(record.isPresent());
            assertEquals("rotation-fqdn-02.", record.get().data().asString());

            record = tester.controllerTester().nameService().findRecord(
                    Record.Type.CNAME, RecordName.from("app1.tenant1.global.vespa.yahooapis.com")
            );
            assertTrue(record.isPresent());
            assertEquals("rotation-fqdn-02.", record.get().data().asString());

        }

    }

    @Test
    public void testDeployWithoutProjectId() {
        DeploymentTester tester = new DeploymentTester();
        tester.controllerTester().zoneRegistry().setSystem(SystemName.cd);
        tester.controllerTester().zoneRegistry().setZones(ZoneId.from("prod", "cd-us-central-1"));
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("cd-us-central-1")
                .build();

        // Create application
        Application app = tester.createApplication("app1", "tenant1", 1, 2L);

        // Direct deploy is allowed when project ID is missing
        ZoneId zone = ZoneId.from("prod", "cd-us-central-1");
        // Same options as used in our integration tests
        DeployOptions options = new DeployOptions(Optional.empty(), Optional.empty(), false,
                                                  false);
        tester.controller().applications().deployApplication(app.id(), zone, Optional.of(applicationPackage), options);

        assertTrue("Application deployed and activated",
                   tester.controllerTester().configServer().activated().getOrDefault(app.id(), false));

        assertTrue("No job status added",
                   tester.applications().require(app.id()).deploymentJobs().jobStatus().isEmpty());

    }

    private void runUpgrade(DeploymentTester tester, ApplicationId application, ApplicationVersion version) {
        Version next = Version.fromString("6.2");
        tester.upgradeSystem(next);
        runDeployment(tester, tester.applications().require(application), version, Optional.of(next), Optional.empty());
    }

    private void runDeployment(DeploymentTester tester, ApplicationId application, ApplicationVersion version,
                               ApplicationPackage applicationPackage, SourceRevision sourceRevision, long buildNumber) {
        Application app = tester.applications().require(application);
        tester.jobCompletion(component)
              .application(app)
              .buildNumber(buildNumber)
              .sourceRevision(sourceRevision)
              .uploadArtifact(applicationPackage)
              .submit();

        ApplicationVersion change = ApplicationVersion.from(sourceRevision, buildNumber);
        assertEquals(change.id(), tester.controller().applications()
                                        .require(application)
                                        .change().application().get().id());
        runDeployment(tester, app, version, Optional.empty(), Optional.of(applicationPackage));
    }

    private void runDeployment(DeploymentTester tester, Application app, ApplicationVersion version,
                               Optional<Version> upgrade, Optional<ApplicationPackage> applicationPackage) {
        Version vespaVersion = upgrade.orElseGet(tester::defaultVespaVersion);

        // Deploy in test
        tester.deployAndNotify(app, applicationPackage, true, true, systemTest);
        tester.deployAndNotify(app, applicationPackage, true, true, stagingTest);
        assertStatus(JobStatus.initial(stagingTest)
                             .withTriggering(vespaVersion, version, "",
                                             tester.clock().instant().minus(Duration.ofMillis(1)))
                             .withCompletion(42, Optional.empty(), tester.clock().instant(),
                                             tester.controller()), app.id(), tester.controller());

        // Deploy in production
        tester.deployAndNotify(app, applicationPackage, true, true, productionCorpUsEast1);
        assertStatus(JobStatus.initial(productionCorpUsEast1)
                             .withTriggering(vespaVersion, version, "",
                                             tester.clock().instant().minus(Duration.ofMillis(1)))
                             .withCompletion(42, Optional.empty(), tester.clock().instant(),
                                             tester.controller()), app.id(), tester.controller());
        tester.deployAndNotify(app, applicationPackage, true, true, productionUsEast3);
        assertStatus(JobStatus.initial(productionUsEast3)
                             .withTriggering(vespaVersion, version, "",
                                             tester.clock().instant().minus(Duration.ofMillis(1)))
                             .withCompletion(42, Optional.empty(), tester.clock().instant(),
                                             tester.controller()), app.id(), tester.controller());

        // Verify deployed version
        app = tester.controller().applications().require(app.id());
        for (Deployment deployment : app.productionDeployments().values()) {
            assertEquals(version, deployment.applicationVersion());
            upgrade.ifPresent(v -> assertEquals(v, deployment.version()));
        }
    }

    @Test
    public void testDeploymentOfNewInstanceWithIllegalApplicationName() {
        ControllerTester tester = new ControllerTester();
        String application = "this_application_name_is_far_too_long_and_has_underscores";
        ZoneId zone = ZoneId.from("test", "us-east-1");
        DeployOptions options = new DeployOptions(Optional.of(new ScrewdriverBuildJob(new ScrewdriverId("123"),
                                                                                      null)),
                                                  Optional.empty(),
                                                  false,
                                                  false);

        tester.createTenant("tenant", "domain", null);

        // Deploy an application which doesn't yet exist, and which has an illegal application name.
        try {
            tester.controller().applications().deployApplication(ApplicationId.from("tenant", application, "123"), zone, Optional.empty(), options);
            fail("Illegal application name should cause validation exception.");
        }
        catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid id"));
        }

        // Sneak an illegal application in the back door.
        tester.createApplication(new ApplicationSerializer().toSlime(new Application(ApplicationId.from("tenant", application, "default"))));

        // Deploy a PR instance for the application, with no NToken.
        tester.controller().applications().deployApplication(ApplicationId.from("tenant", application, "456"), zone, Optional.empty(), options);
        assertTrue(tester.controller().applications().get(ApplicationId.from("tenant", application, "456")).isPresent());
    }

}
