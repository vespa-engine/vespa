// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.proxy;

import ai.vespa.http.HttpURL.Path;
import com.yahoo.jdisc.http.HttpRequest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Haakon Dybdahl
 */
public class ProxyRequestTest {

    @Test
    void testBadUri() {
        assertEquals("Request path '/path' does not end with proxy path '/zone/v2/'",
                assertThrows(IllegalArgumentException.class,
                        () -> testRequest("http://domain.tld/path", "/zone/v2/")).getMessage());
    }

    @Test
    void testUris() {
        {
            // Root request
            ProxyRequest request = testRequest("http://controller.domain.tld/my/path", "");
            assertEquals(URI.create("http://controller.domain.tld/my/path/"), request.getControllerPrefixUri());
            assertEquals(URI.create("https://cfg.prod.us-north-1.domain.tld:1234"),
                    request.createConfigServerRequestUri(URI.create("https://cfg.prod.us-north-1.domain.tld:1234/")));
        }

        {
            // Root request with trailing /
            ProxyRequest request = testRequest("http://controller.domain.tld/my/path/", "/");
            assertEquals(URI.create("http://controller.domain.tld/my/path/"), request.getControllerPrefixUri());
            assertEquals(URI.create("https://cfg.prod.us-north-1.domain.tld:1234/"),
                    request.createConfigServerRequestUri(URI.create("https://cfg.prod.us-north-1.domain.tld:1234/")));
        }

        {
            // API path test
            ProxyRequest request = testRequest("http://controller.domain.tld:1234/my/path/nodes/v2", "/nodes/v2");
            assertEquals(URI.create("http://controller.domain.tld:1234/my/path/"), request.getControllerPrefixUri());
            assertEquals(URI.create("https://cfg.prod.us-north-1.domain.tld/nodes/v2"),
                    request.createConfigServerRequestUri(URI.create("https://cfg.prod.us-north-1.domain.tld")));
        }

        {
            // API path test with query
            ProxyRequest request = testRequest("http://controller.domain.tld:1234/my/path/nodes/v2/?some=thing", "/nodes/v2/");
            assertEquals(URI.create("http://controller.domain.tld:1234/my/path/"), request.getControllerPrefixUri());
            assertEquals(URI.create("https://cfg.prod.us-north-1.domain.tld/nodes/v2/?some=thing"),
                    request.createConfigServerRequestUri(URI.create("https://cfg.prod.us-north-1.domain.tld")));
        }
    }

    private static ProxyRequest testRequest(String url, String pathPrefix) {
        return new ProxyRequest(HttpRequest.Method.GET, URI.create(url), Map.of(), null,
                                List.of(URI.create("http://example.com")), Path.parse(pathPrefix));
    }

}
