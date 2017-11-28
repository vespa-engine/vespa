// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.proxy;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author Haakon Dybdahl
 */
public class ProxyResponseTest {

    @Test
    public void testRewriteUrl() throws Exception {
        String controllerPrefix = "/zone/v2/";
        URI configServer = URI.create("http://configserver:1234");
        ProxyRequest request = new ProxyRequest(URI.create("http://foo/zone/v2/env/region/configserver"),
                                                headers("controller:49152"), null, "GET",
                                                controllerPrefix);
        ProxyResponse proxyResponse = new ProxyResponse(
                request,
                "response link is http://configserver:1234/bla/bla/",
                200,
                Optional.of(configServer),
                "application/json");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        proxyResponse.render(outputStream);
        String document = new String(outputStream.toByteArray(),"UTF-8");
        assertEquals("response link is http://controller:49152/zone/v2/env/region/bla/bla/", document);
    }

    @Test
    public void testRewriteSecureUrl() throws Exception {
        String controllerPrefix = "/zone/v2/";
        URI configServer = URI.create("http://configserver:1234");
        ProxyRequest request = new ProxyRequest(URI.create("https://foo/zone/v2/env/region/configserver"),
                                                headers("controller:49152"), null, "GET",
                                                controllerPrefix);
        ProxyResponse proxyResponse = new ProxyResponse(
                request,
                "response link is http://configserver:1234/bla/bla/",
                200,
                Optional.of(configServer),
                "application/json");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        proxyResponse.render(outputStream);
        String document = new String(outputStream.toByteArray(),"UTF-8");
        assertEquals("response link is https://controller:49152/zone/v2/env/region/bla/bla/", document);
    }

    private static Map<String, List<String>> headers(String hostPort) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("host", Collections.singletonList(hostPort));
        return Collections.unmodifiableMap(headers);
    }

}
