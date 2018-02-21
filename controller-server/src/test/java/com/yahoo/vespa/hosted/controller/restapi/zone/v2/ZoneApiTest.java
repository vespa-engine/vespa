// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.zone.v2;

import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Request.Method;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.hosted.controller.ConfigServerProxyMock;
import com.yahoo.vespa.hosted.controller.ZoneRegistryMock;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.restapi.ContainerControllerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author mpolden
 */
public class ZoneApiTest extends ControllerContainerTest {

    private static final AthenzIdentity HOSTED_VESPA_OPERATOR = AthenzUser.fromUserId("johnoperator");
    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/zone/v2/responses/";
    private static final List<ZoneId> zones = Arrays.asList(
            ZoneId.from(Environment.prod, RegionName.from("us-north-1")),
            ZoneId.from(Environment.dev, RegionName.from("us-north-2")),
            ZoneId.from(Environment.test, RegionName.from("us-north-3")),
            ZoneId.from(Environment.staging, RegionName.from("us-north-4"))
                                                           );

    private ContainerControllerTester tester;
    private ConfigServerProxyMock proxy;

    @Before
    public void before() {
        ZoneRegistryMock zoneRegistry = (ZoneRegistryMock) container.components()
                                                                    .getComponent(ZoneRegistryMock.class.getName());
        zoneRegistry.setDefaultRegionForEnvironment(Environment.dev, RegionName.from("us-north-2"))
                    .setZones(zones);
        this.tester = new ContainerControllerTester(container, responseFiles);
        this.proxy = (ConfigServerProxyMock) container.components().getComponent(ConfigServerProxyMock.class.getName());
        addUserToHostedOperatorRole(HOSTED_VESPA_OPERATOR);
    }

    @Test
    public void test_requests() throws Exception {
        // GET /zone/v2
        tester.containerTester().assertResponse(authenticatedRequest("http://localhost:8080/zone/v2"),
                                                new File("root.json"));

        // GET /zone/v2/prod/us-north-1
        tester.containerTester().assertResponse(authenticatedRequest("http://localhost:8080/zone/v2/prod/us-north-1"),
                                                "ok");
        assertEquals("prod", proxy.lastReceived().get().getEnvironment());
        assertEquals("us-north-1", proxy.lastReceived().get().getRegion());
        assertEquals("", proxy.lastReceived().get().getConfigServerRequest());
        assertEquals("GET", proxy.lastReceived().get().getMethod());

        // GET /zone/v2/nodes/v2/node/?recursive=true
        tester.containerTester().assertResponse(authenticatedRequest("http://localhost:8080/zone/v2/prod/us-north-1/nodes/v2/node/?recursive=true"),
                                                "ok");

        assertEquals("prod", proxy.lastReceived().get().getEnvironment());
        assertEquals("us-north-1", proxy.lastReceived().get().getRegion());
        assertEquals("/nodes/v2/node/?recursive=true", proxy.lastReceived().get().getConfigServerRequest());
        assertEquals("GET", proxy.lastReceived().get().getMethod());

        // POST /zone/v2/dev/us-north-2/nodes/v2/command/restart?hostname=node1
        tester.containerTester().assertResponse(hostedOperatorRequest("http://localhost:8080/zone/v2/dev/us-north-2/nodes/v2/command/restart?hostname=node1",
                                                            new byte[0], Method.POST),
                                                "ok");
        assertEquals("dev", proxy.lastReceived().get().getEnvironment());
        assertEquals("us-north-2", proxy.lastReceived().get().getRegion());
        assertEquals("/nodes/v2/command/restart?hostname=node1", proxy.lastReceived().get().getConfigServerRequest());
        assertEquals("POST", proxy.lastReceived().get().getMethod());

        // PUT /zone/v2/prod/us-north-1/nodes/v2/state/dirty/node1
        tester.containerTester().assertResponse(hostedOperatorRequest("http://localhost:8080/zone/v2/prod/us-north-1/nodes/v2/state/dirty/node1",
                                                            new byte[0], Method.PUT), "ok");
        assertEquals("prod", proxy.lastReceived().get().getEnvironment());
        assertEquals("us-north-1", proxy.lastReceived().get().getRegion());
        assertEquals("/nodes/v2/state/dirty/node1", proxy.lastReceived().get().getConfigServerRequest());
        assertEquals("PUT", proxy.lastReceived().get().getMethod());

        // DELETE /zone/v2/prod/us-north-1/nodes/v2/node/node1
        tester.containerTester().assertResponse(hostedOperatorRequest("http://localhost:8080/zone/v2/prod/us-north-1/nodes/v2/node/node1",
                                                            new byte[0], Method.DELETE), "ok");
        assertEquals("prod", proxy.lastReceived().get().getEnvironment());
        assertEquals("us-north-1", proxy.lastReceived().get().getRegion());
        assertEquals("/nodes/v2/node/node1", proxy.lastReceived().get().getConfigServerRequest());
        assertEquals("DELETE", proxy.lastReceived().get().getMethod());

        // PATCH /zone/v2/prod/us-north-1/nodes/v2/node/node1
        tester.containerTester().assertResponse(hostedOperatorRequest("http://localhost:8080/zone/v2/prod/us-north-1/nodes/v2/node/node1",
                                                            Utf8.toBytes("{\"currentRestartGeneration\": 1}"),
                                                            Method.PATCH), "ok");
        assertEquals("prod", proxy.lastReceived().get().getEnvironment());
        assertEquals("us-north-1", proxy.lastReceived().get().getRegion());
        assertEquals("/nodes/v2/node/node1", proxy.lastReceived().get().getConfigServerRequest());
        assertEquals("PATCH", proxy.lastReceived().get().getMethod());
        assertEquals("{\"currentRestartGeneration\": 1}", proxy.lastRequestBody().get());
    }

    @Test
    public void test_invalid_requests() throws Exception {
        // POST /zone/v2/prod/us-north-34/nodes/v2
        tester.containerTester().assertResponse(hostedOperatorRequest("http://localhost:8080/zone/v2/prod/us-north-42/nodes/v2",
                                                            new byte[0], Method.POST),
                                                new File("unknown-zone.json"), 400);
        assertFalse(proxy.lastReceived().isPresent());
    }

    private static Request hostedOperatorRequest(String uri, byte[] body, Request.Method method) {
        return addIdentityToRequest(new Request(uri, body, method), HOSTED_VESPA_OPERATOR);
    }

}
