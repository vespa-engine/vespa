// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.deployment;

import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.Test;

/**
 * @author jonmv
 */
public class BadgeApiTest extends ControllerContainerTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/deployment/responses/";

    @Test
    public void testBadgeApi() {
        ContainerTester tester = new ContainerTester(container, responseFiles);
        var application = new DeploymentTester(new ControllerTester(tester)).newDeploymentContext("tenant", "application", "default");
        application.submit();

        tester.assertResponse(authenticatedRequest("http://localhost:8080/badge/v1/tenant/application/default"),
                              "", 302);
        tester.assertResponse(authenticatedRequest("http://localhost:8080/badge/v1/tenant/application/default/system-test?historyLength=10"),
                              "", 302);
    }

}
