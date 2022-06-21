// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.routing.nginx;

import com.yahoo.vespa.hosted.routing.mock.HttpClientMock;
import org.junit.Test;

import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author oyving
 * @author mpolden
 */
public class NginxHealthClientTest {

    @Test
    public void unknown_endpoint_is_down() {
        NginxHealthClient client = createClient("nginx-health-output.json");
        assertFalse(client.servers().isHealthy("no.such.endpoint"));
    }

    @Test
    public void all_down_endpoint_is_down() {
        NginxHealthClient service = createClient("nginx-health-output-all-down.json");
        assertFalse(service.servers().isHealthy("gateway.prod.music.vespa.us-east-2.prod"));
    }

    @Test
    public void all_up_endpoint_is_up() {
        NginxHealthClient service = createClient("nginx-health-output-all-up.json");
        assertTrue(service.servers().isHealthy("gateway.prod.music.vespa.us-east-2.prod"));
    }

    @Test
    public void two_down_endpoint_is_down() {
        NginxHealthClient service = createClient("nginx-health-output-policy-down.json");
        assertFalse(service.servers().isHealthy("gateway.prod.music.vespa.us-east-2.prod"));
    }

    @Test
    public void one_down_endpoint_is_up() {
        NginxHealthClient service = createClient("nginx-health-output-policy-up.json");
        assertTrue(service.servers().isHealthy("gateway.prod.music.vespa.us-east-2.prod"));
    }

    @Test
    public void all_up_but_other_endpoint_down() {
        NginxHealthClient service = createClient("nginx-health-output-all-up-but-other-down.json");
        assertTrue(service.servers().isHealthy("gateway.prod.music.vespa.us-east-2.prod"));
        assertFalse(service.servers().isHealthy("frog.prod.music.vespa.us-east-2.prod"));
    }

    private static NginxHealthClient createClient(String file) {
        HttpClientMock httpClient = new HttpClientMock().setResponse("GET",
                                                                     "http://localhost:4080/health-status/?format=json",
                                                                     response(file));
        return new NginxHealthClient(httpClient);
    }

    private static HttpClientMock.JsonResponse response(String file) {
        return new HttpClientMock.JsonResponse(Paths.get("src/test/resources/", file), 200);
    }

}
