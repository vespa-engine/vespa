// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.screwdriver;

import com.yahoo.application.container.handler.Request;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobReport;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.SourceRevision;
import com.yahoo.vespa.hosted.controller.restapi.ContainerControllerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * @author bratseth
 * @author jonmv
 */
public class ScrewdriverApiTest extends ControllerContainerTest {

    @Test
    public void testTriggerJobForApplication() {
        ContainerControllerTester tester = new ContainerControllerTester(container, null);
        tester.containerTester().computeVersionStatus();

        Application app = tester.createApplication();
        tester.controller().applications().lockOrThrow(app.id(), application ->
                tester.controller().applications().store(application.withProjectId(OptionalLong.of(1L))));

        // Unknown application
        assertResponse(new Request("http://localhost:8080/screwdriver/v1/trigger/tenant/foo/application/bar",
                                   new byte[0], Request.Method.POST, () -> "user"),
                       400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"foo.bar not found\"}");

        // Invalid job
        assertResponse(new Request("http://localhost:8080/screwdriver/v1/trigger/tenant/" +
                                   app.id().tenant().value() + "/application/" + app.id().application().value(),
                                   "invalid".getBytes(StandardCharsets.UTF_8), Request.Method.POST, () -> "user"),
                       400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Unknown job name 'invalid'\"}");

        // component is triggered if no job is specified in request body
        assertResponse(new Request("http://localhost:8080/screwdriver/v1/trigger/tenant/" +
                                   app.id().tenant().value() + "/application/" + app.id().application().value(),
                                   new byte[0], Request.Method.POST, () -> "user"),
                       200, "{\"message\":\"Triggered component for tenant1.application1\"}");
        tester.controller().applications().deploymentTrigger().notifyOfCompletion(JobReport.ofComponent(app.id(),
                                                                                                        1,
                                                                                                        42,
                                                                                                        Optional.empty(),
                                                                                                        new SourceRevision("repo", "branch", "commit")));

        // Triggers specific job when given, when job is a test, or tested.
        assertResponse(new Request("http://localhost:8080/screwdriver/v1/trigger/tenant/" +
                                   app.id().tenant().value() + "/application/" + app.id().application().value(),
                                   "staging-test".getBytes(StandardCharsets.UTF_8), Request.Method.POST, () -> "user"),
                       200, "{\"message\":\"Triggered staging-test for tenant1.application1\"}");

        // Triggers test jobs (only system-test here since deployment spec is unknown) when given untested production job.
        assertResponse(new Request("http://localhost:8080/screwdriver/v1/trigger/tenant/" +
                                   app.id().tenant().value() + "/application/" + app.id().application().value(),
                                   "production-us-east-3".getBytes(StandardCharsets.UTF_8), Request.Method.POST, () -> "user"),
                       200, "{\"message\":\"Triggered system-test for tenant1.application1\"}");

    }

}
