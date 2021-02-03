// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.component.Version;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.test.json.JsonTestHelper;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TestReport;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException.ErrorCode.INVALID_APPLICATION_PACKAGE;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.devAwsUsEast2a;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.devUsEast1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionUsCentral1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionUsEast3;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.productionUsWest1;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.systemTest;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.testUsCentral1;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.applicationPackage;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.deploymentFailed;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.installationFailed;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.running;
import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 * @author freva
 */
public class JobControllerApiHandlerHelperTest {

    @Test
    public void testResponses() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .stagingTest()
                .blockChange(true, true, "mon,tue", "7-13", "UTC")
                .blockChange(false, true, "sun", "0-23", "CET")
                .blockChange(true, false, "fri-sat", "8", "America/Los_Angeles")
                .region("us-central-1")
                .test("us-central-1")
                .parallel("us-west-1", "us-east-3")
                .build();
        DeploymentTester tester = new DeploymentTester();
        var app = tester.newDeploymentContext();
        tester.clock().setInstant(Instant.EPOCH);

        // All completed runs will have a test report.
        tester.cloud().testReport(TestReport.fromJson("{\"summary\":{\"success\": 1, \"failed\": 0}}"));

        // Revision 1 gets deployed everywhere.
        app.submit(applicationPackage).deploy();
        ApplicationVersion revision1 = app.lastSubmission().get();
        assertEquals(1000, tester.application().projectId().getAsLong());
        // System test includes test report
        assertResponse(JobControllerApiHandlerHelper.runDetailsResponse(tester.jobs(), tester.jobs().last(app.instanceId(), systemTest).get().id(), "0"), "system-test-log.json");

        tester.clock().advance(Duration.ofMillis(1000));
        // Revision 2 gets deployed everywhere except in us-east-3.
        ApplicationVersion revision2 = app.submit(applicationPackage).lastSubmission().get();
        app.runJob(systemTest);
        app.runJob(stagingTest);
        app.runJob(productionUsCentral1);
        app.runJob(testUsCentral1);

        tester.triggerJobs();

        // us-east-3 eats the deployment failure and fails before deployment, while us-west-1 fails after.
        tester.configServer().throwOnNextPrepare(new ConfigServerException(URI.create("url"), "Failed to deploy application", "ERROR!", INVALID_APPLICATION_PACKAGE, null));
        tester.runner().run();
        assertEquals(deploymentFailed, tester.jobs().last(app.instanceId(), productionUsEast3).get().status());

        tester.runner().run();
        tester.clock().advance(Duration.ofHours(4).plusSeconds(1));
        tester.runner().run();
        assertEquals(installationFailed, tester.jobs().last(app.instanceId(), productionUsWest1).get().status());
        assertEquals(revision2, app.deployment(productionUsCentral1.zone(tester.controller().system())).applicationVersion());
        assertEquals(revision1, app.deployment(productionUsEast3.zone(tester.controller().system())).applicationVersion());
        assertEquals(revision2, app.deployment(productionUsWest1.zone(tester.controller().system())).applicationVersion());

        tester.clock().advance(Duration.ofMillis(1000));

        // Revision 3 starts.
        app.submit(applicationPackage)
           .runJob(systemTest).runJob(stagingTest);
        tester.triggerJobs(); // Starts runs for us-central-1 and a new staging test run.
        tester.runner().run();
        assertEquals(running, tester.jobs().last(app.instanceId(), productionUsCentral1).get().status());
        assertEquals(running, tester.jobs().last(app.instanceId(), stagingTest).get().status());

        // Staging deployment expires and the job fails, and is immediately retried.
        tester.controller().applications().deactivate(app.instanceId(), stagingTest.zone(tester.controller().system()));
        tester.runner().run();
        assertEquals(installationFailed, tester.jobs().last(app.instanceId(), stagingTest).get().status());

        // Staging deployment expires again, the job fails for the second time, and won't be retried immediately.
        tester.clock().advance(Duration.ofMillis(100_000)); // Advance time to avoid immediate retry
        tester.triggerJobs();
        tester.runner().run();
        assertEquals(running, tester.jobs().last(app.instanceId(), stagingTest).get().status());
        tester.controller().applications().deactivate(app.instanceId(), stagingTest.zone(tester.controller().system()));
        tester.runner().run();
        assertEquals(installationFailed, tester.jobs().last(app.instanceId(), stagingTest).get().status());

        tester.triggerJobs();
        assertEquals(installationFailed, tester.jobs().last(app.instanceId(), stagingTest).get().status());

        // System upgrades to a new version, which won't yet start.
        Version platform = new Version("7.1");
        tester.controllerTester().upgradeSystem(platform);
        tester.upgrader().maintain();
        tester.triggerJobs();

        // us-central-1 has started, deployed, and is installing. Deployment is not yet verified.
        // us-east-3 is waiting for the failed staging test and us-central-1, while us-west-1 is waiting only for us-central-1.
        // Only us-east-3 is verified, on revision1.
        // staging-test has 5 runs: one success without sources on revision1, one success from revision1 to revision2,
        // one success from revision2 to revision3 and two failures from revision1 to revision3.
        assertResponse(JobControllerApiHandlerHelper.runResponse(tester.jobs().runs(app.instanceId(), stagingTest), URI.create("https://some.url:43/root")), "staging-runs.json");
        assertResponse(JobControllerApiHandlerHelper.runDetailsResponse(tester.jobs(), tester.jobs().last(app.instanceId(), stagingTest).get().id(), "0"), "staging-test-log.json");
        assertResponse(JobControllerApiHandlerHelper.runDetailsResponse(tester.jobs(), tester.jobs().last(app.instanceId(), productionUsEast3).get().id(), "0"), "us-east-3-log-without-first.json");
        assertResponse(JobControllerApiHandlerHelper.jobTypeResponse(tester.controller(), app.instanceId(), URI.create("https://some.url:43/root/")), "overview.json");

        var userApp = tester.newDeploymentContext(app.instanceId().tenant().value(), app.instanceId().application().value(), "user");
        userApp.runJob(devAwsUsEast2a, applicationPackage);
        assertResponse(JobControllerApiHandlerHelper.runResponse(tester.jobs().runs(userApp.instanceId(), devAwsUsEast2a), URI.create("https://some.url:43/root")), "dev-aws-us-east-2a-runs.json");
        assertResponse(JobControllerApiHandlerHelper.jobTypeResponse(tester.controller(), userApp.instanceId(), URI.create("https://some.url:43/root/")), "overview-user-instance.json");
        assertResponse(JobControllerApiHandlerHelper.overviewResponse(tester.controller(), app.application().id(), URI.create("https://some.url:43/root/")), "deployment-overview-2.json");
    }

    @Test
    public void testDevResponses() {
        DeploymentTester tester = new DeploymentTester();
        var app = tester.newDeploymentContext();
        tester.clock().setInstant(Instant.EPOCH);

        ZoneId zone = JobType.devUsEast1.zone(tester.controller().system());
        tester.jobs().deploy(app.instanceId(), JobType.devUsEast1, Optional.empty(), applicationPackage());
        tester.configServer().setLogStream("1554970337.935104\t17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\t5480\tcontainer\tstdout\tinfo\tERROR: Bundle canary-application [71] Unable to get module class path. (java.lang.NullPointerException)\n");
        assertResponse(JobControllerApiHandlerHelper.runDetailsResponse(tester.jobs(), tester.jobs().last(app.instanceId(), devUsEast1).get().id(), null), "dev-us-east-1-log-first-part.json");

        tester.configServer().setLogStream("Nope, this won't be logged");
        tester.configServer().convergeServices(app.instanceId(), zone);
        tester.runner().run();

        assertResponse(JobControllerApiHandlerHelper.jobTypeResponse(tester.controller(), app.instanceId(), URI.create("https://some.url:43/root")), "dev-overview.json");
        assertResponse(JobControllerApiHandlerHelper.runDetailsResponse(tester.jobs(), tester.jobs().last(app.instanceId(), devUsEast1).get().id(), "8"), "dev-us-east-1-log-second-part.json");
    }

    @Test
    public void testResponsesWithDirectDeployment() {
        var tester = new DeploymentTester();
        var app = tester.newDeploymentContext();
        tester.clock().setInstant(Instant.EPOCH);
        var region = "us-west-1";
        var applicationPackage = new ApplicationPackageBuilder().region(region).build();
        // Deploy directly to production zone, like integration tests.
        tester.controller().applications().deploy(tester.instance().id(), ZoneId.from("prod", region),
                                                           Optional.of(applicationPackage),
                                                           new DeployOptions(true, Optional.empty(),
                                                                             false, false));
        assertResponse(JobControllerApiHandlerHelper.jobTypeResponse(tester.controller(), app.instanceId(), URI.create("https://some.url:43/root/")),
                       "jobs-direct-deployment.json");
    }

    private void compare(HttpResponse response, String expected) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.render(baos);
        JsonTestHelper.assertJsonEquals(expected, baos.toString());
    }

    private void assertResponse(HttpResponse response, String fileName) {
        try {
            Path path = Paths.get("src/test/java/com/yahoo/vespa/hosted/controller/restapi/application/responses/").resolve(fileName);
            String expected = Files.readString(path);
            compare(response, expected);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
