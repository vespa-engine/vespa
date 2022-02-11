// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.routing.status;

import com.yahoo.vespa.hosted.routing.mock.HttpClientMock;
import com.yahoo.vespa.hosted.routing.mock.HttpClientMock.JsonResponse;
import org.junit.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class RoutingStatusClientTest {

    @Test
    public void client() {
        String deploymentUrl = "http://host/routing/v1/status";
        String zoneUrl = "http://host/routing/v1/status/zone";
        HttpClientMock httpClient = new HttpClientMock();
        RoutingStatusClient client = new RoutingStatusClient(httpClient, URI.create("http://host"));

        // Nothing is inactive
        httpClient.setResponse("GET", deploymentUrl, inactiveDeployments())
                  .setResponse("GET", zoneUrl, zoneActive(true));
        assertTrue(client.isActive("foo"));

        // Two upstreams are set inactive
        httpClient.setResponse("GET", deploymentUrl, inactiveDeployments("bar", "foo"));
        client.invalidateCache();
        assertFalse(client.isActive("foo"));
        assertFalse(client.isActive("bar"));
        assertTrue(client.isActive("baz"));

        // Bad response results in active status
        client.invalidateCache();
        httpClient.setResponse("GET", deploymentUrl, badRequest("something went wrong"));
        assertTrue(client.isActive("foo"));

        // Inactive zone overrides deployment status
        client.invalidateCache();
        httpClient.setResponse("GET", deploymentUrl, inactiveDeployments("bar"));
        httpClient.setResponse("GET", zoneUrl, zoneActive(false));
        assertFalse(client.isActive("foo"));
        assertFalse(client.isActive("bar"));

        // Zone is active again. Fall back to reading deployment status
        httpClient.setResponse("GET", zoneUrl, zoneActive(true));
        client.invalidateCache();
        assertTrue(client.isActive("foo"));
        assertFalse(client.isActive("bar"));
    }

    private static JsonResponse badRequest(String message) {
        return new JsonResponse("{\"message\":\"" + message + "\"}", 400);
    }

    private static JsonResponse zoneActive(boolean active) {
        return new JsonResponse("{\"status\":\"" + (active ? "IN" : "OUT") + "\"}", 200);
    }

    private static JsonResponse inactiveDeployments(String... deployments) {
        return new JsonResponse("[" + Arrays.stream(deployments)
                                            .map(d -> "\"" + d + "\"")
                                            .collect(Collectors.joining(",")) + "]", 200);
    }

}
