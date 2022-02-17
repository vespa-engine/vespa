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
        String statusUrl = "http://host/routing/v2/status";
        HttpClientMock httpClient = new HttpClientMock();
        RoutingStatusClient client = new RoutingStatusClient(httpClient, URI.create("http://host"));

        // Nothing is inactive
        httpClient.setResponse("GET", statusUrl, response(true));
        assertTrue(client.isActive("foo"));

        // Two upstreams are set inactive
        httpClient.setResponse("GET", statusUrl, response(true, "bar", "foo"));
        client.invalidateCache();
        assertFalse(client.isActive("foo"));
        assertFalse(client.isActive("bar"));
        assertTrue(client.isActive("baz"));

        // Bad response results in active status
        client.invalidateCache();
        httpClient.setResponse("GET", statusUrl, badRequest("something went wrong"));
        assertTrue(client.isActive("foo"));

        // Inactive zone overrides deployment status
        client.invalidateCache();
        httpClient.setResponse("GET", statusUrl, response(false, "bar"));
        assertFalse(client.isActive("foo"));
        assertFalse(client.isActive("bar"));

        // Zone is active again. Fall back to reading deployment status
        httpClient.setResponse("GET", statusUrl, response(true, "bar"));
        client.invalidateCache();
        assertTrue(client.isActive("foo"));
        assertFalse(client.isActive("bar"));
    }

    private static JsonResponse badRequest(String message) {
        return new JsonResponse("{\"message\":\"" + message + "\"}", 400);
    }

    private static JsonResponse response(boolean zoneActive, String... inactiveUpstreams) {
        String inactiveDeployments = "[" + Arrays.stream(inactiveUpstreams)
                                                 .map(d -> "{\"upstreamName\":\"" + d + "\"}")
                                                 .collect(Collectors.joining(",")) + "]";
        String json = "{\"inactiveDeployments\":" + inactiveDeployments + ",\"zoneActive\":" + zoneActive + "}";
        return new JsonResponse(json, 200);
    }

}
