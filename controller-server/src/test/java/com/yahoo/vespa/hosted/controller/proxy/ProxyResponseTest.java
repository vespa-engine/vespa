// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.proxy;

import com.yahoo.jdisc.http.HttpRequest;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author Haakon Dybdahl
 */
public class ProxyResponseTest {

    @Test
    public void testRewriteUrl() throws Exception {
        ProxyRequest request = new ProxyRequest(HttpRequest.Method.GET, URI.create("http://domain.tld/zone/v2/dev/us-north-1/configserver"),
                Map.of(), null, List.of(), null, "configserver");
        ProxyResponse proxyResponse = new ProxyResponse(
                request,
                "response link is http://configserver:1234/bla/bla/",
                200,
                URI.create("http://configserver:1234"),
                "application/json");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        proxyResponse.render(outputStream);
        String document = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        assertEquals("response link is http://domain.tld/zone/v2/dev/us-north-1/bla/bla/", document);
    }

    @Test
    public void testRewriteSecureUrl() throws Exception {
        ProxyRequest request = new ProxyRequest(HttpRequest.Method.GET, URI.create("https://domain.tld/zone/v2/prod/eu-south-3/configserver"),
                Map.of(), null, List.of(), null, "configserver");
        ProxyResponse proxyResponse = new ProxyResponse(
                request,
                "response link is http://configserver:1234/bla/bla/",
                200,
                URI.create("http://configserver:1234"),
                "application/json");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        proxyResponse.render(outputStream);
        String document = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        assertEquals("response link is https://domain.tld/zone/v2/prod/eu-south-3/bla/bla/", document);
    }
}
