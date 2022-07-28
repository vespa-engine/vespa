// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.flags;

import com.yahoo.application.container.handler.Request;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLog;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mpolden
 */
public class AuditedFlagsApiTest extends ControllerContainerTest {

    private static final String responses = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/flags/responses/";
    private static final AthenzIdentity operator = AthenzUser.fromUserId("operatorUser");

    private ContainerTester tester;

    @BeforeEach
    public void before() {
        addUserToHostedOperatorRole(operator);
        tester = new ContainerTester(container, responses);
    }

    @Test
    void test_audit_logging() {
        var body = "{\n" +
                "  \"id\": \"id1\",\n" +
                "  \"rules\": [\n" +
                "    {\n" +
                "      \"value\": true\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        assertResponse(new Request("http://localhost:8080/flags/v1/data/id1?force=true", body, Request.Method.PUT),
                "", 200);
        var log = tester.controller().auditLogger().readLog();
        assertEquals(1, log.entries().size());
        var entry = log.entries().get(0);
        assertEquals(operator.getFullName(), entry.principal());
        assertEquals(AuditLog.Entry.Method.PUT, entry.method());
        assertEquals("/flags/v1/data/id1?force=true", entry.resource());
        assertEquals(body, log.entries().get(0).data().get());
    }

    private void assertResponse(Request request, String body, int statusCode) {
        addIdentityToRequest(request, operator);
        tester.assertResponse(request, body, statusCode);
    }

}
