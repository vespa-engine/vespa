// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.proxy;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
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
        exception.expectMessage("Request not set.");
        testRequest(null, "/zone/v2/");
    }

    @Test
    public void testBadUri() throws Exception {
        exception.expectMessage("Request not starting with /zone/v2/");
        testRequest(URI.create("http://foo"), "/zone/v2/");
    }

    @Test
    public void testProxyRequest() throws Exception {
        ProxyRequest proxyRequest = testRequest(URI.create("http://foo/zone/v2/foo/bar/bla/bla/v1/something"),
                                                "/zone/v2/");
        assertEquals("foo", proxyRequest.getEnvironment());
        assertEquals("/bla/bla/v1/something", proxyRequest.getConfigServerRequest());
    }

    @Test
    public void testProxyRequestWithParameters() throws Exception {
        ProxyRequest proxyRequest = testRequest(URI.create("http://foo/zone/v2/foo/bar/something?p=v&q=y"),
                                                "/zone/v2/");
        assertEquals("foo", proxyRequest.getEnvironment());
        assertEquals("/something?p=v&q=y", proxyRequest.getConfigServerRequest());
    }

    private static ProxyRequest testRequest(URI url, String pathPrefix) throws IOException, ProxyException {
        return new ProxyRequest(url, headers("controller:49152"), null, "GET", pathPrefix);
    }

    private static Map<String, List<String>> headers(String hostPort) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("host", Collections.singletonList(hostPort));
        return Collections.unmodifiableMap(headers);
    }

}
