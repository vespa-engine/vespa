// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.deployment;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * @author jonmv
 */
public class BadgeApiTest extends ControllerContainerTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/deployment/responses/";

    @Test
    void testBadgeApi() throws IOException {
        ContainerTester tester = new ContainerTester(container, responseFiles);
        DeploymentTester deploymentTester = new DeploymentTester(new ControllerTester(tester));
        deploymentTester.controllerTester().upgradeSystem(Version.fromString("6.1"));
        var application = deploymentTester.newDeploymentContext("tenant", "application", "default");
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder().parallel("us-west-1", "aws-us-east-1a")
                .test("us-west-1")
                .region("ap-southeast-1")
                .test("ap-southeast-1")
                .region("eu-west-1")
                .test("eu-west-1")
                .build();
        application.submit(applicationPackage).deploy();
        application.submit(applicationPackage)
                .runJob(DeploymentContext.systemTest)
                .runJob(DeploymentContext.stagingTest)
                .runJob(DeploymentContext.productionUsWest1)
                .runJob(DeploymentContext.productionAwsUsEast1a)
                .runJob(DeploymentContext.testUsWest1)
                .runJob(DeploymentContext.productionApSoutheast1)
                .failDeployment(DeploymentContext.testApSoutheast1);
        application.submit(applicationPackage)
                .failTests(DeploymentContext.systemTest, true)
                .runJob(DeploymentContext.stagingTest);
        for (int i = 0; i < 31; i++)
            if ((i & 1) == 0)
                application.failDeployment(DeploymentContext.productionUsWest1);
            else
                application.triggerJobs().abortJob(DeploymentContext.productionUsWest1);
        application.triggerJobs();
        tester.controller().applications().deploymentTrigger().reTrigger(application.instanceId(), DeploymentContext.systemTest, "reason");
        tester.controller().applications().deploymentTrigger().reTrigger(application.instanceId(), DeploymentContext.testEuWest1, "reason");

        tester.assertResponse(authenticatedRequest("http://localhost:8080/badge/v1/tenant/application/default"),
                Files.readString(Paths.get(responseFiles + "overview.svg")), 200);
        tester.assertResponse(authenticatedRequest("http://localhost:8080/badge/v1/tenant/application/default/production-us-west-1?historyLength=0"),
                Files.readString(Paths.get(responseFiles + "single-running.svg")), 200);
        tester.assertResponse(authenticatedRequest("http://localhost:8080/badge/v1/tenant/application/default/system-test"),
                Files.readString(Paths.get(responseFiles + "running-test.svg")), 200);
        tester.assertResponse(authenticatedRequest("http://localhost:8080/badge/v1/tenant/application/default/production-us-west-1?historyLength=32"),
                Files.readString(Paths.get(responseFiles + "history.svg")), 200);

        // New change not reflected before cache entry expires.
        tester.serviceRegistry().clock().advance(Duration.ofSeconds(59));
        application.runJob(DeploymentContext.productionUsWest1);
        tester.assertResponse(authenticatedRequest("http://localhost:8080/badge/v1/tenant/application/default/production-us-west-1?historyLength=32"),
                Files.readString(Paths.get(responseFiles + "history.svg")), 200);

        // Cached entry refreshed after a minute.
        tester.serviceRegistry().clock().advance(Duration.ofSeconds(1));
        tester.assertResponse(authenticatedRequest("http://localhost:8080/badge/v1/tenant/application/default/production-us-west-1?historyLength=32"),
                Files.readString(Paths.get(responseFiles + "history2.svg")), 200);

        tester.assertResponse(authenticatedRequest("http://localhost:8080/badge/v1/tenant/application/default/production-us-west-1?historyLength=0"),
                Files.readString(Paths.get(responseFiles + "single-done.svg")), 200);
    }

}
