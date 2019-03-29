// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.component.Version;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.deployment.InternalDeploymentTester;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;

import static com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException.ErrorCode.INVALID_APPLICATION_PACKAGE;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionUsCentral1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionUsEast3;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionUsWest1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.systemTest;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud.Status.FAILURE;
import static com.yahoo.vespa.hosted.controller.deployment.InternalDeploymentTester.appId;
import static com.yahoo.vespa.hosted.controller.deployment.InternalDeploymentTester.testerId;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.deploymentFailed;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.installationFailed;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.running;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.testFailure;
import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 * @author freva
 */
public class JobControllerApiHandlerHelperTest {

    @Test
    public void testResponses() {
        InternalDeploymentTester tester = new InternalDeploymentTester();
        tester.clock().setInstant(Instant.EPOCH);

        // Revision 1 gets deployed everywhere.
        ApplicationVersion revision1 = tester.deployNewSubmission();
        assertEquals(2, tester.app().deploymentJobs().projectId().getAsLong());

        tester.clock().advance(Duration.ofMillis(1000));
        // Revision 2 gets deployed everywhere except in us-east-3.
        ApplicationVersion revision2 = tester.newSubmission();
        tester.runJob(systemTest);
        tester.runJob(stagingTest);
        tester.runJob(productionUsCentral1);

        tester.tester().readyJobTrigger().maintain();

        // us-east-3 eats the deployment failure and fails before deployment, while us-west-1 fails after.
        tester.configServer().throwOnNextPrepare(new ConfigServerException(URI.create("url"), "ERROR!", INVALID_APPLICATION_PACKAGE, null));
        tester.runner().run();
        assertEquals(deploymentFailed, tester.jobs().last(appId, productionUsEast3).get().status());

        ZoneId usWest1 = productionUsWest1.zone(tester.tester().controller().system());
        tester.configServer().convergeServices(appId, usWest1);
        tester.configServer().convergeServices(testerId.id(), usWest1);
        tester.setEndpoints(appId, usWest1);
        tester.setEndpoints(testerId.id(), usWest1);
        tester.runner().run();
        tester.cloud().set(FAILURE);
        tester.runner().run();
        assertEquals(testFailure, tester.jobs().last(appId, productionUsWest1).get().status());
        assertEquals(revision2, tester.app().deployments().get(productionUsCentral1.zone(tester.tester().controller().system())).applicationVersion());
        assertEquals(revision1, tester.app().deployments().get(productionUsEast3.zone(tester.tester().controller().system())).applicationVersion());
        assertEquals(revision2, tester.app().deployments().get(productionUsWest1.zone(tester.tester().controller().system())).applicationVersion());

        tester.clock().advance(Duration.ofMillis(1000));

        // Revision 3 starts.
        tester.newSubmission();
        tester.runJob(systemTest);
        tester.runJob(stagingTest);
        tester.tester().readyJobTrigger().maintain(); // Starts a run for us-central-1.
        tester.tester().readyJobTrigger().maintain(); // Starts a new staging test run.
        tester.runner().run();
        assertEquals(running, tester.jobs().last(appId, productionUsCentral1).get().status());
        assertEquals(running, tester.jobs().last(appId, stagingTest).get().status());

        // Staging is expired, and the job fails and won't be retried immediately.
        tester.tester().controller().applications().deactivate(appId, stagingTest.zone(tester.tester().controller().system()));
        tester.runner().run();
        assertEquals(installationFailed, tester.jobs().last(appId, stagingTest).get().status());

        tester.clock().advance(Duration.ofMillis(100_000)); // More than the minute within which there are immediate retries.
        tester.tester().readyJobTrigger().maintain();
        assertEquals(installationFailed, tester.jobs().last(appId, stagingTest).get().status());

        // System upgrades to a new version, which won't yet start.
        Version platform = new Version("7.1");
        tester.tester().upgradeSystem(platform);

        // us-central-1 has started, deployed, and is installing. Deployment is not yet verified.
        // us-east-3 is waiting for the failed staging test and us-central-1, while us-west-1 is waiting only for us-central-1.
        // Only us-east-3 is verified, on revision1.
        // staging-test has 4 runs: one success without sources on revision1, one success from revision1 to revision2,
        // one success from revision2 to revision3 and one failure from revision1 to revision3.
        assertResponse(JobControllerApiHandlerHelper.runResponse(tester.jobs().runs(appId, stagingTest), URI.create("https://some.url:43/root")), "staging-runs.json");
        assertResponse(JobControllerApiHandlerHelper.runDetailsResponse(tester.jobs(), tester.jobs().last(appId, productionUsEast3).get().id(), "0"), "us-east-3-log-without-first.json");
        assertResponse(JobControllerApiHandlerHelper.jobTypeResponse(tester.tester().controller(), appId, URI.create("https://some.url:43/root/")), "overview.json");

    }

    private void compare(HttpResponse response, String expected) throws JSONException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.render(baos);

        System.err.println(baos);

        JSONObject actualJSON = new JSONObject(new String(baos.toByteArray()));
        JSONObject expectedJSON = new JSONObject(expected);
        assertEquals(expectedJSON.toString(), actualJSON.toString());
    }

    private void assertResponse(HttpResponse response, String fileName) {
        try {
            Path path = Paths.get("src/test/java/com/yahoo/vespa/hosted/controller/restapi/application/responses/").resolve(fileName);
            String expected = new String(Files.readAllBytes(path));
            compare(response, expected);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
