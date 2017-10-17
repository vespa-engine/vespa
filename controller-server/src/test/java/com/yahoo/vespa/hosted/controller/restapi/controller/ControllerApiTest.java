// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.controller;

import com.yahoo.application.container.handler.Request;
import com.yahoo.vespa.hosted.controller.restapi.ContainerControllerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * @author bratseth
 */
public class ControllerApiTest extends ControllerContainerTest {

    private final static String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/controller/responses/";

    @Test
    public void testControllerApi() throws Exception {
        ContainerControllerTester tester = new ContainerControllerTester(container, responseFiles);

        tester.assertResponse(new Request("http://localhost:8080/controller/v1/"), new File("root.json"));

        // POST deactivation of a maintenance job
        assertResponse(new Request("http://localhost:8080/controller/v1/maintenance/inactive/DeploymentExpirer",
                                   new byte[0], Request.Method.POST),
                       200,
                       "{\"message\":\"Deactivated job 'DeploymentExpirer'\"}");
        // GET a list of all maintenance jobs
        tester.assertResponse(new Request("http://localhost:8080/controller/v1/maintenance/"),
                              new File("maintenance.json"));
        // DELETE deactivation of a maintenance job
        assertResponse(new Request("http://localhost:8080/controller/v1/maintenance/inactive/DeploymentExpirer",
                                   new byte[0], Request.Method.DELETE),
                       200,
                       "{\"message\":\"Re-activated job 'DeploymentExpirer'\"}");
    }

    @Test
    public void testUpgraderApi() throws Exception {
        ContainerControllerTester tester = new ContainerControllerTester(container, responseFiles);

        // Get current configuration
        tester.assertResponse(new Request("http://localhost:8080/controller/v1/jobs/upgrader"),
                              "{\"upgradesPerMinute\":0.5,\"ignoreConfidence\":false}",
                              200);

        // Set invalid configuration
        tester.assertResponse(new Request("http://localhost:8080/controller/v1/jobs/upgrader",
                                          "{\"upgradesPerMinute\":-1}", Request.Method.PATCH),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Upgrades per minute must be >= 0\"}",
                              400);

        // Unrecognized field
        tester.assertResponse(new Request("http://localhost:8080/controller/v1/jobs/upgrader",
                                          "{\"foo\":bar}", Request.Method.PATCH),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Unable to configure upgrader with data in request: '{\\\"foo\\\":bar}'\"}",
                              + 400);

        // Patch configuration
        tester.assertResponse(new Request("http://localhost:8080/controller/v1/jobs/upgrader",
                                          "{\"upgradesPerMinute\":42.0}", Request.Method.PATCH),
                              "{\"upgradesPerMinute\":42.0,\"ignoreConfidence\":false}",
                              200);

        // Patch configuration
        tester.assertResponse(new Request("http://localhost:8080/controller/v1/jobs/upgrader",
                                          "{\"ignoreConfidence\":true}", Request.Method.PATCH),
                              "{\"upgradesPerMinute\":42.0,\"ignoreConfidence\":true}",
                              200);
    }

}
