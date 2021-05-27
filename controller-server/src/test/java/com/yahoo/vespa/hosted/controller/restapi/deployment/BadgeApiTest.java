// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.deployment;

import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.Test;

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
    public void testBadgeApi() throws IOException {
        ContainerTester tester = new ContainerTester(container, responseFiles);
        var application = new DeploymentTester(new ControllerTester(tester)).newDeploymentContext("tenant", "application", "default");
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder().systemTest()
                                                                               .parallel("us-west-1", "aws-us-east-1a")
                                                                               .test("us-west-1")
                                                                               .region("ap-southeast-1")
                                                                               .test("ap-southeast-1")
                                                                               .region("eu-west-1")
                                                                               .test("eu-west-1")
                                                                               .build();
        application.submit(applicationPackage).deploy();
        application.submit(applicationPackage)
                   .runJob(JobType.systemTest)
                   .runJob(JobType.stagingTest)
                   .runJob(JobType.productionUsWest1)
                   .runJob(JobType.productionAwsUsEast1a)
                   .runJob(JobType.testUsWest1)
                   .runJob(JobType.productionApSoutheast1)
                   .failDeployment(JobType.testApSoutheast1);
        application.submit(applicationPackage)
                   .runJob(JobType.systemTest)
                   .runJob(JobType.stagingTest);
        for (int i = 0; i < 31; i++)
            application.failDeployment(JobType.productionUsWest1);
        application.triggerJobs();
        tester.controller().applications().deploymentTrigger().reTrigger(application.instanceId(), JobType.testEuWest1);

        tester.assertResponse(authenticatedRequest("http://localhost:8080/badge/v1/tenant/application/default"),
                              Files.readString(Paths.get(responseFiles + "overview.svg")), 200);
        tester.assertResponse(authenticatedRequest("http://localhost:8080/badge/v1/tenant/application/default/production-us-west-1?historyLength=32"),
                              Files.readString(Paths.get(responseFiles + "history.svg")), 200);

        // New change not reflected before cache entry expires.
        tester.serviceRegistry().clock().advance(Duration.ofSeconds(59));
        application.runJob(JobType.productionUsWest1);
        tester.assertResponse(authenticatedRequest("http://localhost:8080/badge/v1/tenant/application/default/production-us-west-1?historyLength=32"),
                              Files.readString(Paths.get(responseFiles + "history.svg")), 200);

        // Cached entry refreshed after a minute.
        tester.serviceRegistry().clock().advance(Duration.ofSeconds(1));
        tester.assertResponse(authenticatedRequest("http://localhost:8080/badge/v1/tenant/application/default/production-us-west-1?historyLength=32"),
                              Files.readString(Paths.get(responseFiles + "history2.svg")), 200);
    }

}
