// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.configserver;

import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.vespa.hosted.controller.integration.ConfigServerProxyMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.proxy.ProxyRequest;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author freva
 */
public class ConfigServerApiHandlerTest extends ControllerContainerTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/configserver/responses/";
    private static final List<ZoneApi> zones = List.of(
            ZoneApiMock.fromId("prod.us-north-1"),
            ZoneApiMock.fromId("dev.aws-us-north-2"),
            ZoneApiMock.fromId("test.us-north-3"),
            ZoneApiMock.fromId("staging.us-north-4"));

    private ContainerTester tester;
    private ConfigServerProxyMock proxy;

    @BeforeEach
    public void before() {
        tester = new ContainerTester(container, responseFiles);
        tester.serviceRegistry().zoneRegistry()
              .setDefaultRegionForEnvironment(Environment.dev, RegionName.from("us-north-2"))
              .setZones(zones);
        proxy = (ConfigServerProxyMock) container.components().getComponent(ConfigServerProxyMock.class.getName());
    }

    @Test
    void test_requests() {
        // GET /configserver/v1
        tester.assertResponse(operatorRequest("http://localhost:8080/configserver/v1"),
                new File("root.json"));

        // GET /configserver/v1/nodes/v2
        tester.assertResponse(operatorRequest("http://localhost:8080/configserver/v1/prod/us-north-1/nodes/v2"),
                "ok");
        assertLastRequest("https://cfg.prod.us-north-1.test.vip:4443/", "GET");

        // GET /configserver/v1/nodes/v2/node/?recursive=true
        tester.assertResponse(operatorRequest("http://localhost:8080/configserver/v1/prod/us-north-1/nodes/v2/node/?recursive=true"),
                "ok");
        assertLastRequest("https://cfg.prod.us-north-1.test.vip:4443/", "GET");

        // POST /configserver/v1/dev/us-north-2/nodes/v2/command/restart?hostname=node1
        tester.assertResponse(operatorRequest("http://localhost:8080/configserver/v1/dev/aws-us-north-2/nodes/v2/command/restart?hostname=node1",
                "", Request.Method.POST),
                "ok");

        // PUT /configserver/v1/prod/us-north-1/nodes/v2/state/dirty/node1
        tester.assertResponse(operatorRequest("http://localhost:8080/configserver/v1/prod/us-north-1/nodes/v2/state/dirty/node1",
                "", Request.Method.PUT), "ok");
        assertLastRequest("https://cfg.prod.us-north-1.test.vip:4443/", "PUT");

        // DELETE /configserver/v1/prod/us-north-1/nodes/v2/node/node1
        tester.assertResponse(operatorRequest("http://localhost:8080/configserver/v1/prod/controller/nodes/v2/node/node1",
                "", Request.Method.DELETE), "ok");
        assertLastRequest("https://localhost:4443/", "DELETE");

        // PATCH /configserver/v1/prod/us-north-1/nodes/v2/node/node1
        tester.assertResponse(operatorRequest("http://localhost:8080/configserver/v1/dev/aws-us-north-2/nodes/v2/node/node1",
                "{\"currentRestartGeneration\": 1}",
                Request.Method.PATCH), "ok");
        assertLastRequest("https://cfg.dev.aws-us-north-2.test.vip:4443/", "PATCH");
        assertEquals("{\"currentRestartGeneration\": 1}", proxy.lastRequestBody().get());

        assertFalse(tester.controller().auditLogger().readLog().entries().isEmpty(), "Actions are logged to audit log");
    }

    @Test
    void test_allowed_apis() {
        // GET /configserver/v1/prod/us-north-1
        tester.assertResponse(() -> operatorRequest("http://localhost:8080/configserver/v1/prod/us-north-1/"),
                "{\"error-code\":\"FORBIDDEN\",\"message\":\"Cannot access path '/' through /configserver/v1, following APIs are permitted: /flags/v1/, /nodes/v2/, /orchestrator/v1/\"}",
                403);

        tester.assertResponse(() -> operatorRequest("http://localhost:8080/configserver/v1/prod/us-north-1/application/v2/tenant/vespa"),
                "{\"error-code\":\"FORBIDDEN\",\"message\":\"Cannot access path '/application/v2/tenant/vespa' through /configserver/v1, following APIs are permitted: /flags/v1/, /nodes/v2/, /orchestrator/v1/\"}",
                403);
    }

    @Test
    void test_invalid_requests() {
        // POST /configserver/v1/prod/us-north-34/nodes/v2
        tester.assertResponse(() -> operatorRequest("http://localhost:8080/configserver/v1/prod/us-north-42/nodes/v2",
                        "", Request.Method.POST),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"No such zone: prod.us-north-42\"}", 400);
        assertFalse(proxy.lastReceived().isPresent());
    }

    @Test
    void non_operators_are_forbidden() {
        // Read request
        tester.assertResponse(() -> authenticatedRequest("http://localhost:8080/configserver/v1/prod/us-north-1/nodes/v2/node"),
                "{\n" +
                        "  \"code\" : 403,\n" +
                        "  \"message\" : \"Access denied\"\n" +
                        "}", 403);

        // Write request
        tester.assertResponse(() -> authenticatedRequest("http://localhost:8080/configserver/v1/prod/us-north-1/nodes/v2/node", "", Request.Method.POST),
                "{\n" +
                        "  \"code\" : 403,\n" +
                        "  \"message\" : \"Access denied\"\n" +
                        "}", 403);
    }

    @Test
    void unauthenticated_request_are_unauthorized() {
        {
            // Read request
            Request request = new Request("http://localhost:8080/configserver/v1/prod/us-north-1/nodes/v2/node", "", Request.Method.GET);
            tester.assertResponse(() -> request, "{\n  \"message\" : \"Not authenticated\"\n}", 401);
        }

        {
            // Write request
            Request request = new Request("http://localhost:8080/configserver/v1/prod/us-north-1/nodes/v2/node", "", Request.Method.POST);
            tester.assertResponse(() -> request, "{\n  \"message\" : \"Not authenticated\"\n}", 401);
        }
    }


    private void assertLastRequest(String target, String method) {
        ProxyRequest last = proxy.lastReceived().orElseThrow();
        assertEquals(List.of(URI.create(target)), last.getTargets());
        assertEquals(com.yahoo.jdisc.http.HttpRequest.Method.valueOf(method), last.getMethod());
    }
}
