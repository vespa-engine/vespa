// Copyright 2021 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.changemanagement;

import com.yahoo.application.container.handler.Request;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.intellij.lang.annotations.Language;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

public class ChangeManagementApiHandlerTest extends ControllerContainerTest {

    private static final String responses = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/changemanagement/responses/";
    private static final AthenzIdentity operator = AthenzUser.fromUserId("operatorUser");

    private ContainerTester tester;

    @Before
    public void before() {
        tester = new ContainerTester(container, responses);
        addUserToHostedOperatorRole(operator);
    }

    @Test
    public void test_api() {
        assertFile(new Request("http://localhost:8080/changemanagement/v1/assessment", "{}", Request.Method.POST), "initial.json");
    }

    private void assertResponse(Request request, @Language("JSON") String body, int statusCode) {
        addIdentityToRequest(request, operator);
        tester.assertResponse(request, body, statusCode);
    }

    private void assertFile(Request request, String filename) {
        addIdentityToRequest(request, operator);
        tester.assertResponse(request, new File(filename));
    }
}
