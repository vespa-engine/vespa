// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.controller;

import com.yahoo.application.container.handler.Request;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.hosted.controller.restapi.ContainerControllerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.Test;

import java.io.File;

/**
 * @author bratseth
 */
public class ControllerApiTest extends ControllerContainerTest {

    private final static String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/controller/responses/";
    private static final AthenzIdentity HOSTED_VESPA_OPERATOR = AthenzUser.fromUserId("johnoperator");

    @Test
    public void testControllerApi() throws Exception {
        ContainerControllerTester tester = new ContainerControllerTester(container, responseFiles);

        tester.assertResponse(authenticatedRequest("http://localhost:8080/controller/v1/", new byte[0], Request.Method.GET), new File("root.json"));

        // POST deactivation of a maintenance job
        assertResponse(authenticatedRequest("http://localhost:8080/controller/v1/maintenance/inactive/DeploymentExpirer",
                                            new byte[0], Request.Method.POST),
                       200,
                       "{\"message\":\"Deactivated job 'DeploymentExpirer'\"}");
        // GET a list of all maintenance jobs
        tester.assertResponse(authenticatedRequest("http://localhost:8080/controller/v1/maintenance/", new byte[0], Request.Method.GET),
                              new File("maintenance.json"));
        // DELETE deactivation of a maintenance job
        assertResponse(authenticatedRequest("http://localhost:8080/controller/v1/maintenance/inactive/DeploymentExpirer",
                                            new byte[0], Request.Method.DELETE),
                       200,
                       "{\"message\":\"Re-activated job 'DeploymentExpirer'\"}");
    }

    @Test
    public void testUpgraderApi() throws Exception {
        addUserToHostedOperatorRole(HOSTED_VESPA_OPERATOR);

        ContainerControllerTester tester = new ContainerControllerTester(container, responseFiles);

        // Get current configuration
        tester.assertResponse(authenticatedRequest("http://localhost:8080/controller/v1/jobs/upgrader", new byte[0], Request.Method.GET),
                              "{\"upgradesPerMinute\":0.5,\"confidenceOverrides\":[]}",
                              200);

        // Set invalid configuration
        tester.assertResponse(
                hostedOperatorRequest("http://localhost:8080/controller/v1/jobs/upgrader", "{\"upgradesPerMinute\":-1}", Request.Method.PATCH),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Upgrades per minute must be >= 0\"}",
                400);

        // Ignores unrecognized field
        tester.assertResponse(
                hostedOperatorRequest("http://localhost:8080/controller/v1/jobs/upgrader","{\"foo\":\"bar\"}", Request.Method.PATCH),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"No such modifiable field(s)\"}",
                400);

        // Set upgrades per minute
        tester.assertResponse(
                hostedOperatorRequest("http://localhost:8080/controller/v1/jobs/upgrader", "{\"upgradesPerMinute\":42.0}", Request.Method.PATCH),
                "{\"upgradesPerMinute\":42.0,\"confidenceOverrides\":[]}",
                200);

        // Override confidence
        tester.assertResponse(
                hostedOperatorRequest("http://localhost:8080/controller/v1/jobs/upgrader/confidence/6.42", "broken", Request.Method.POST),
                "{\"upgradesPerMinute\":42.0,\"confidenceOverrides\":[{\"6.42\":\"broken\"}]}",
                200);

        // Override confidence for another version
        tester.assertResponse(
                hostedOperatorRequest("http://localhost:8080/controller/v1/jobs/upgrader/confidence/6.43", "broken", Request.Method.POST),
                "{\"upgradesPerMinute\":42.0,\"confidenceOverrides\":[{\"6.42\":\"broken\"},{\"6.43\":\"broken\"}]}",
                200);

        // Remove first override
        tester.assertResponse(
                hostedOperatorRequest("http://localhost:8080/controller/v1/jobs/upgrader/confidence/6.42", "", Request.Method.DELETE),
                "{\"upgradesPerMinute\":42.0,\"confidenceOverrides\":[{\"6.43\":\"broken\"}]}",
                200);
    }

    private static Request hostedOperatorRequest(String uri, String body, Request.Method method) {
        return addIdentityToRequest(new Request(uri, body, method), HOSTED_VESPA_OPERATOR);
    }

}
