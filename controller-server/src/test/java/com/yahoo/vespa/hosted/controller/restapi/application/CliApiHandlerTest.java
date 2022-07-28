// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author mpolden
 */
public class CliApiHandlerTest extends ControllerContainerTest {

    private ContainerTester tester;

    @BeforeEach
    public void before() {
        tester = new ContainerTester(container, "src/test/java/com/yahoo/vespa/hosted/controller/restapi/application/responses/");
    }

    @Test
    void root() {
        tester.assertResponse(authenticatedRequest("http://localhost:8080/cli/v1/"), "{\"minVersion\":\"7.547.18\"}");
    }

}
