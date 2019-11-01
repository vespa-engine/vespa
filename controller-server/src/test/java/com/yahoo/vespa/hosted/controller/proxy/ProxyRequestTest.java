// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.proxy;

import com.yahoo.jdisc.http.HttpRequest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author Haakon Dybdahl
 */
public class ProxyRequestTest {

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void testEmpty() throws Exception {
        exception.expectMessage("Request must be non-null");
        new ProxyRequest(HttpRequest.Method.GET, null, Map.of(), null, List.of(), "/zone/v2");
    }

    @Test
    public void testBadUri() throws Exception {
        exception.expectMessage("Request path '/path' does not end with proxy path '/zone/v2/'");
        testRequest("http://domain.tld/path", "/zone/v2/");
    }

    @Test
    public void testUris() throws Exception {
        {
            // Root request
            ProxyRequest request = testRequest("http://controller.domain.tld/my/path", "");
            assertEquals(URI.create("http://controller.domain.tld/my/path/"), request.getControllerPrefixUri());
            assertEquals(URI.create("https://cfg.prod.us-north-1.domain.tld:1234/"),
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

    private static ProxyRequest testRequest(String url, String pathPrefix) throws ProxyException {
        return new ProxyRequest(
                HttpRequest.Method.GET, URI.create(url), Map.of(), null, List.of(), pathPrefix);
    }
}
