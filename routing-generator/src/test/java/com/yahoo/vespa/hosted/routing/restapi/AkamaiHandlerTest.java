// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.routing.restapi;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpRequestBuilder;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.vespa.hosted.routing.RoutingTable;
import com.yahoo.vespa.hosted.routing.RoutingTable.Endpoint;
import com.yahoo.vespa.hosted.routing.mock.HealthStatusMock;
import com.yahoo.vespa.hosted.routing.mock.RoutingStatusMock;
import com.yahoo.vespa.hosted.routing.status.HealthStatus;
import com.yahoo.vespa.hosted.routing.status.ServerGroup;
import com.yahoo.yolean.Exceptions;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author oyving
 * @author mpolden
 */
public class AkamaiHandlerTest {

    private static final String
            ENDPOINT_OK = "ok.vespa.yahooapis.com",
            ENDPOINT_UNKNOWN = "unknown.vespa.yahooapis.com",
            ENDPOINT_UNAVAILABLE = "out.vespa.yahooapis.com",
            ENDPOINT_UNHEALTHY = "unhealthy.vespa.yahooapis.com",
            ENDPOINT_INACTIVE = "inactive.vespa.yahooapis.com";

    private static final String ENDPOINT_WITH_PORT_OK = ENDPOINT_OK + ":4080";

    private final RoutingStatusMock statusService = new RoutingStatusMock().setStatus("i3.a3.t3.us-north-1.prod", false);

    private final HealthStatus healthStatus = new HealthStatusMock().setStatus(new ServerGroup(List.of(
            new ServerGroup.Server("i1.a1.t1.us-north-1.prod", "hostport", true),
            new ServerGroup.Server("i2.a2.t2.us-north-1.prod", "hostport", false))));

    private final AkamaiHandler handler = new AkamaiHandler(ThreadedHttpRequestHandler.testContext(),
                                                            () -> Optional.of(makeRoutingTable()),
                                                            statusService,
                                                            healthStatus);

    @Test
    public void ok_endpoint() {
        assertResponse(ENDPOINT_OK, 200, AkamaiHandler.ROTATION_OK_MESSAGE);
        assertResponse(ENDPOINT_WITH_PORT_OK, 200, AkamaiHandler.ROTATION_OK_MESSAGE);
    }

    @Test
    public void unknown_endpoint() {
        assertResponse(ENDPOINT_UNKNOWN, 404, AkamaiHandler.ROTATION_UNKNOWN_MESSAGE);
    }

    @Test
    public void out_of_rotation_endpoint() {
        assertResponse(ENDPOINT_UNAVAILABLE, 404, AkamaiHandler.ROTATION_UNAVAILABLE_MESSAGE);
    }

    @Test
    public void unhealthy_endpoint() {
        assertResponse(ENDPOINT_UNHEALTHY, 502, AkamaiHandler.ROTATION_UNHEALTHY_MESSAGE);
    }

    @Test
    public void inactive_endpoint() {
        assertResponse(ENDPOINT_INACTIVE, 404, AkamaiHandler.ROTATION_INACTIVE_MESSAGE);
    }

    private void assertResponse(String rotation, int status, String message) {
        HttpRequest req = HttpRequestBuilder.create(com.yahoo.jdisc.http.HttpRequest.Method.GET, "/akamai/v1/status")
                                            .withHeader("Host", rotation)
                                            .build();
        HttpResponse response = handler.handle(req);
        assertEquals(status, response.getStatus());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Exceptions.uncheck(() -> response.render(out));
        String responseBody = out.toString();

        String expected = "\"message\":\"" + message + "\"";
        assertTrue("Contains expected message", responseBody.contains(expected));
    }

    private static RoutingTable makeRoutingTable() {
        return new RoutingTable(Map.of(
                new Endpoint(ENDPOINT_OK, RoutingMethod.sharedLayer4), createTarget("t1", "a1", "i1", "default", true),
                new Endpoint(ENDPOINT_UNAVAILABLE, RoutingMethod.sharedLayer4), createTarget("t3", "a3", "i3", "default", true),
                new Endpoint(ENDPOINT_UNHEALTHY, RoutingMethod.sharedLayer4), createTarget("t2", "a2", "i2", "default", true),
                new Endpoint(ENDPOINT_INACTIVE, RoutingMethod.sharedLayer4), createTarget("t1", "a1", "i1", "default", false)
        ), 42);
    }

    private static RoutingTable.Target createTarget(String tenantName, String applicationName, String instanceName, String clusterName, boolean routingActive) {
        ZoneId zone = ZoneId.from("prod", "us-north-1");
        ClusterSpec.Id cluster = ClusterSpec.Id.from(clusterName);
        return RoutingTable.Target.create(ApplicationId.from(tenantName, applicationName, instanceName), cluster, zone,
                                          List.of(new RoutingTable.Real("host", 8080, 1, routingActive)));
    }

}
