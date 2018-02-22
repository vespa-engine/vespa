// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.screwdriver;

import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.component.Version;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobError;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.BuildJob;
import com.yahoo.vespa.hosted.controller.deployment.BuildSystem;
import com.yahoo.vespa.hosted.controller.restapi.ContainerControllerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 * @author jvenstad
 */
// TODO Move /application/v4/.../jobreport specific testing to ApplicationApiTest
public class ScrewdriverApiTest extends ControllerContainerTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/screwdriver/responses/";
    private static final ZoneId testZone = ZoneId.from(Environment.test, RegionName.from("us-east-1"));
    private static final ZoneId stagingZone = ZoneId.from(Environment.staging, RegionName.from("us-east-3"));
    private static final AthenzIdentity HOSTED_VESPA_OPERATOR = AthenzUser.fromUserId("johnoperator");

    @Test
    public void testGetReleaseStatus() throws Exception {
        ContainerControllerTester tester = new ContainerControllerTester(container, responseFiles);
        tester.containerTester().assertResponse(authenticatedRequest("http://localhost:8080/screwdriver/v1/release/vespa"),
                                                "{\"error-code\":\"NOT_FOUND\",\"message\":\"Information about the current system version is not available at this time\"}",
                                                404);

        tester.controller().updateVersionStatus(VersionStatus.compute(tester.controller()));
        tester.containerTester().assertResponse(authenticatedRequest("http://localhost:8080/screwdriver/v1/release/vespa"),
                                                new File("release-response.json"), 200);
    }

    @Test
    public void testJobStatusReporting() throws Exception {
        ContainerControllerTester tester = new ContainerControllerTester(container, responseFiles);
        addUserToHostedOperatorRole(HOSTED_VESPA_OPERATOR);
        tester.containerTester().updateSystemVersion();
        long projectId = 1;
        Application app = tester.createApplication();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("corp-us-east-1")
                .build();

        Version vespaVersion = new Version("6.1"); // system version from mock config server client

        BuildJob job = new BuildJob(this::notifyCompletion, tester.artifactRepository())
                .application(app)
                .projectId(projectId);
        job.type(JobType.component).uploadArtifact(applicationPackage).submit();
        tester.deploy(app, applicationPackage, testZone, projectId);
        job.type(JobType.systemTest).submit();

        // Notifying about unknown job fails
        Request request = new Request("http://localhost:8080/application/v4/tenant/tenant1/application/application1/jobreport",
                                      asJson(job.type(JobType.productionUsEast3).report()),
                                      Request.Method.POST);
        addIdentityToRequest(request, HOSTED_VESPA_OPERATOR);
        tester.containerTester().assertResponse(request, new File("unexpected-completion.json"), 400);

        // ... and assert it was recorded
        JobStatus recordedStatus =
                tester.controller().applications().get(app.id()).get().deploymentJobs().jobStatus().get(JobType.component);
        
        assertNotNull("Status was recorded", recordedStatus);
        assertTrue(recordedStatus.isSuccess());
        assertEquals(vespaVersion, recordedStatus.lastCompleted().get().version());

        recordedStatus =
                tester.controller().applications().get(app.id()).get().deploymentJobs().jobStatus().get(JobType.productionApNortheast2);
        assertNull("Status of never-triggered jobs is empty", recordedStatus);

        Response response;

        response = container.handleRequest(new Request("http://localhost:8080/screwdriver/v1/jobsToRun", "", Request.Method.GET));
        assertTrue("Response contains system-test", response.getBodyAsString().contains(JobType.systemTest.jobName()));
        assertTrue("Response contains staging-test", response.getBodyAsString().contains(JobType.stagingTest.jobName()));
        assertEquals("Response contains only two items", 2, SlimeUtils.jsonToSlime(response.getBody()).get().entries());

        // Check that GET didn't affect the enqueued jobs.
        response = container.handleRequest(new Request("http://localhost:8080/screwdriver/v1/jobsToRun", "", Request.Method.DELETE));
        assertTrue("Response contains system-test", response.getBodyAsString().contains(JobType.systemTest.jobName()));
        assertTrue("Response contains staging-test", response.getBodyAsString().contains(JobType.stagingTest.jobName()));
        assertEquals("Response contains only two items", 2, SlimeUtils.jsonToSlime(response.getBody()).get().entries());

        Thread.sleep(50);
        // Check that the *first* DELETE has removed the enqueued jobs.
        assertResponse(new Request("http://localhost:8080/screwdriver/v1/jobsToRun", "", Request.Method.DELETE),
                200, "[]");
    }

    @Test
    public void testJobStatusReportingOutOfCapacity() {
        ContainerControllerTester tester = new ContainerControllerTester(container, responseFiles);
        tester.containerTester().updateSystemVersion();

        long projectId = 1;
        Application app = tester.createApplication();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("corp-us-east-1")
                .build();

        // Report job failing with out of capacity
        BuildJob job = new BuildJob(this::notifyCompletion, tester.artifactRepository())
                .application(app)
                .projectId(projectId);
        job.type(JobType.component).uploadArtifact(applicationPackage).submit();

        tester.deploy(app, applicationPackage, testZone, projectId);
        job.type(JobType.systemTest).submit();
        tester.deploy(app, applicationPackage, stagingZone, projectId);
        job.type(JobType.stagingTest).error(JobError.outOfCapacity).submit();

        // Appropriate error is recorded
        JobStatus jobStatus = tester.controller().applications().get(app.id())
                .get()
                .deploymentJobs()
                .jobStatus()
                .get(JobType.stagingTest);
        assertFalse(jobStatus.isSuccess());
        assertEquals(JobError.outOfCapacity, jobStatus.jobError().get());
    }

    @Test
    public void testTriggerJobForApplication() {
        ContainerControllerTester tester = new ContainerControllerTester(container, responseFiles);
        BuildSystem buildSystem = tester.controller().applications().deploymentTrigger().buildSystem();
        tester.containerTester().updateSystemVersion();

        Application app = tester.createApplication();
        tester.controller().applications().lockOrThrow(app.id(), application ->
                tester.controller().applications().store(application.withProjectId(1)));

        // Unknown application
        assertResponse(new Request("http://localhost:8080/screwdriver/v1/trigger/tenant/foo/application/bar",
                                   new byte[0], Request.Method.POST),
                       400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"foo.bar not found\"}");

        // Invalid job
        assertResponse(new Request("http://localhost:8080/screwdriver/v1/trigger/tenant/" +
                                   app.id().tenant().value() + "/application/" + app.id().application().value(),
                                   "invalid".getBytes(StandardCharsets.UTF_8), Request.Method.POST),
                       400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Unknown job name 'invalid'\"}");

        // component is triggered if no job is specified in request body
        assertResponse(new Request("http://localhost:8080/screwdriver/v1/trigger/tenant/" +
                                   app.id().tenant().value() + "/application/" + app.id().application().value(),
                                   new byte[0], Request.Method.POST),
                       200, "{\"message\":\"Triggered component for tenant1.application1\"}");

        assertFalse(buildSystem.jobs().isEmpty());
        assertEquals(JobType.component.jobName(), buildSystem.jobs().get(0).jobName());
        assertEquals(1L, buildSystem.jobs().get(0).projectId());
        buildSystem.takeJobsToRun();

        // Triggers specific job when given
        assertResponse(new Request("http://localhost:8080/screwdriver/v1/trigger/tenant/" +
                                   app.id().tenant().value() + "/application/" + app.id().application().value(),
                                   "staging-test".getBytes(StandardCharsets.UTF_8), Request.Method.POST),
                       200, "{\"message\":\"Triggered staging-test for tenant1.application1\"}");
        assertFalse(buildSystem.jobs().isEmpty());
        assertEquals(JobType.stagingTest.jobName(), buildSystem.jobs().get(0).jobName());
        assertEquals(1L, buildSystem.jobs().get(0).projectId());
    }

    private void notifyCompletion(DeploymentJobs.JobReport report) {
        assertResponse(new Request("http://localhost:8080/application/v4/tenant/tenant1/application/application1/jobreport",
                                   asJson(report),
                                   Request.Method.POST),
                       200, "{\"message\":\"ok\"}");
    }

    private static byte[] asJson(DeploymentJobs.JobReport report) {
        Slime slime = new Slime();
        Cursor cursor = slime.setObject();
        cursor.setLong("projectId", report.projectId());
        cursor.setString("jobName", report.jobType().jobName());
        cursor.setLong("buildNumber", report.buildNumber());
        report.jobError().ifPresent(jobError -> cursor.setString("jobError", jobError.name()));
        report.sourceRevision().ifPresent(sr -> {
            Cursor sourceRevision = cursor.setObject("sourceRevision");
            sourceRevision.setString("repository", sr.repository());
            sourceRevision.setString("branch", sr.branch());
            sourceRevision.setString("commit", sr.commit());
        });
        cursor.setString("tenant", report.applicationId().tenant().value());
        cursor.setString("application", report.applicationId().application().value());
        cursor.setString("instance", report.applicationId().instance().value());
        try {
            return SlimeUtils.toJsonBytes(slime);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
