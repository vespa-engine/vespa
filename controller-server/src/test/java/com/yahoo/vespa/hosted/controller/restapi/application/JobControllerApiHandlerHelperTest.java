// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.component.Version;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TestReport;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.notification.Notification.Type;
import com.yahoo.vespa.hosted.controller.notification.NotificationSource;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException.ErrorCode.INVALID_APPLICATION_PACKAGE;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.devAwsUsEast2a;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.devUsEast1;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.productionUsCentral1;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.productionUsEast3;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.productionUsWest1;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.stagingTest;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.systemTest;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.testUsCentral1;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.applicationPackage;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.deploymentFailed;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.installationFailed;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.invalidApplication;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.running;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author jonmv
 * @author freva
 */
public class JobControllerApiHandlerHelperTest {

    @Test
    void testResponses() {
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
        RevisionId revision1 = app.lastSubmission().get();
        assertEquals(1000, tester.application().projectId().getAsLong());
        // System test includes test report
        assertResponse(JobControllerApiHandlerHelper.runDetailsResponse(tester.jobs(), tester.jobs().last(app.instanceId(), systemTest).get().id(), "0"), "system-test-log.json");

        tester.clock().advance(Duration.ofMillis(1000));
        // Revision 2 gets deployed everywhere except in us-east-3.
        RevisionId revision2 = app.submit(applicationPackage).lastSubmission().get();
        app.runJob(systemTest);
        app.runJob(stagingTest);
        app.runJob(productionUsCentral1);
        app.runJob(testUsCentral1);

        tester.triggerJobs();

        // us-east-3 eats the deployment failure and fails before deployment, while us-west-1 fails after.
        tester.configServer().throwOnNextPrepare(new ConfigServerException(INVALID_APPLICATION_PACKAGE, "ERROR!", "Failed to deploy application"));
        tester.runner().run();
        assertEquals(invalidApplication, tester.jobs().last(app.instanceId(), productionUsEast3).get().status());

        tester.runner().run();
        tester.clock().advance(Duration.ofHours(4).plusSeconds(1));
        tester.runner().run();
        assertEquals(installationFailed, tester.jobs().last(app.instanceId(), productionUsWest1).get().status());
        assertEquals(revision2, app.deployment(productionUsCentral1.zone()).revision());
        assertEquals(revision1, app.deployment(productionUsEast3.zone()).revision());
        assertEquals(revision2, app.deployment(productionUsWest1.zone()).revision());

        tester.clock().advance(Duration.ofMillis(1000));

        // Revision 3 starts.
        app.submit(applicationPackage)
                .runJob(systemTest).runJob(stagingTest);
        tester.triggerJobs(); // Starts runs for us-central-1 and a new staging test run.
        tester.runner().run();
        assertEquals(running, tester.jobs().last(app.instanceId(), productionUsCentral1).get().status());
        assertEquals(running, tester.jobs().last(app.instanceId(), stagingTest).get().status());

        // Staging deployment expires and the job fails, and is immediately retried.
        tester.controller().applications().deactivate(app.instanceId(), stagingTest.zone());
        tester.runner().run();
        assertEquals(installationFailed, tester.jobs().last(app.instanceId(), stagingTest).get().status());

        // Staging deployment expires again, the job fails for the second time, and won't be retried immediately.
        tester.clock().advance(Duration.ofMillis(100_000)); // Advance time to avoid immediate retry
        tester.triggerJobs();
        tester.runner().run();
        assertEquals(running, tester.jobs().last(app.instanceId(), stagingTest).get().status());
        tester.controller().applications().deactivate(app.instanceId(), stagingTest.zone());
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
        assertResponse(JobControllerApiHandlerHelper.runResponse(app.application(), tester.jobs().runs(app.instanceId(), stagingTest), Optional.empty(), URI.create("https://some.url:43/root")), "staging-runs.json");
        assertResponse(JobControllerApiHandlerHelper.runDetailsResponse(tester.jobs(), tester.jobs().last(app.instanceId(), stagingTest).get().id(), "0"), "staging-test-log.json");
        assertResponse(JobControllerApiHandlerHelper.runDetailsResponse(tester.jobs(), tester.jobs().last(app.instanceId(), productionUsEast3).get().id(), "0"), "us-east-3-log-without-first.json");
        assertResponse(JobControllerApiHandlerHelper.jobTypeResponse(tester.controller(), app.instanceId(), URI.create("https://some.url:43/root/")), "overview.json");

        var userApp = tester.newDeploymentContext(app.instanceId().tenant().value(), app.instanceId().application().value(), "user");
        userApp.runJob(devAwsUsEast2a, applicationPackage);
        assertResponse(JobControllerApiHandlerHelper.runResponse(app.application(), tester.jobs().runs(userApp.instanceId(), devAwsUsEast2a), Optional.empty(), URI.create("https://some.url:43/root")), "dev-aws-us-east-2a-runs.json");
        assertResponse(JobControllerApiHandlerHelper.jobTypeResponse(tester.controller(), userApp.instanceId(), URI.create("https://some.url:43/root/")), "overview-user-instance.json");
        assertResponse(JobControllerApiHandlerHelper.overviewResponse(tester.controller(), app.application().id(), URI.create("https://some.url:43/root/")), "deployment-overview-2.json");

        tester.configServer().setLogStream(() -> "no more logs");
        assertResponse(JobControllerApiHandlerHelper.vespaLogsResponse(tester.jobs(), new RunId(app.instanceId(), stagingTest, 1), 0, false), "vespa.log");
        assertResponse(JobControllerApiHandlerHelper.vespaLogsResponse(tester.jobs(), new RunId(app.instanceId(), stagingTest, 1), 0, true), "vespa.log");
    }

    @Test
    void testDevResponses() {
        DeploymentTester tester = new DeploymentTester();
        var app = tester.newDeploymentContext();
        tester.clock().setInstant(Instant.EPOCH);

        ZoneId zone = DeploymentContext.devUsEast1.zone();
        tester.jobs().deploy(app.instanceId(), DeploymentContext.devUsEast1, Optional.empty(), applicationPackage());
        tester.configServer().setLogStream(() -> "1554970337.935104\t17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\t5480\tcontainer\tstdout\tinfo\tERROR: Bundle canary-application [71] Unable to get module class path. (java.lang.NullPointerException)\n");
        assertResponse(JobControllerApiHandlerHelper.runDetailsResponse(tester.jobs(), tester.jobs().last(app.instanceId(), devUsEast1).get().id(), null), "dev-us-east-1-log-first-part.json");

        tester.configServer().setLogStream(() -> "Nope, this won't be logged");
        tester.configServer().convergeServices(app.instanceId(), zone);
        tester.runner().run();
        assertResponse(JobControllerApiHandlerHelper.runDetailsResponse(tester.jobs(), tester.jobs().last(app.instanceId(), devUsEast1).get().id(), "8"), "dev-us-east-1-log-second-part.json");

        tester.jobs().deploy(app.instanceId(), DeploymentContext.devUsEast1, Optional.empty(), applicationPackage());
        assertResponse(JobControllerApiHandlerHelper.jobTypeResponse(tester.controller(), app.instanceId(), URI.create("https://some.url:43/root")), "dev-overview.json");
    }

    @Test
    void testResponsesWithDirectDeployment() {
        var tester = new DeploymentTester();
        var app = tester.newDeploymentContext();
        tester.clock().setInstant(Instant.EPOCH);
        var region = "us-west-1";
        var applicationPackage = new ApplicationPackageBuilder().region(region).build();
        // Deploy directly to production zone, like integration tests.
        tester.controller().jobController().deploy(tester.instance().id(), productionUsWest1, Optional.empty(), applicationPackage);
        assertResponse(JobControllerApiHandlerHelper.jobTypeResponse(tester.controller(), app.instanceId(), URI.create("https://some.url:43/root/")),
                "jobs-direct-deployment.json");
    }

    @Test
    void testResponsesWithDryRunDeployment() {
        var tester = new DeploymentTester();
        var app = tester.newDeploymentContext();
        tester.clock().setInstant(Instant.EPOCH);
        var region = "us-west-1";
        var applicationPackage = new ApplicationPackageBuilder().region(region).build();
        // Deploy directly to production zone, like integration tests, with dryRun.
        tester.controller().jobController().deploy(tester.instance().id(), productionUsWest1, Optional.empty(), applicationPackage, true, true);
        assertResponse(JobControllerApiHandlerHelper.jobTypeResponse(tester.controller(), app.instanceId(), URI.create("https://some.url:43/root/")),
                "jobs-direct-deployment.json");
    }

    private void assertResponse(HttpResponse response, String fileName) {
        try {
            Path path = Paths.get("src/test/java/com/yahoo/vespa/hosted/controller/restapi/application/responses/").resolve(fileName);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            response.render(baos);
            if (fileName.endsWith(".json")) {
                byte[] actualJson = SlimeUtils.toJsonBytes(SlimeUtils.jsonToSlimeOrThrow(baos.toByteArray()).get(), false);
                // Files.write(path, actualJson);
                byte[] expected = Files.readAllBytes(path);
                assertEquals(new String(SlimeUtils.toJsonBytes(SlimeUtils.jsonToSlimeOrThrow(expected).get(), false), UTF_8),
                             new String(actualJson, UTF_8));
            }
            else {
                assertEquals(Files.readString(path),
                             baos.toString(UTF_8));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
