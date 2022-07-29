// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.proxy;

import ai.vespa.http.HttpURL.Path;
import com.yahoo.jdisc.http.HttpRequest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Haakon Dybdahl
 */
public class ProxyResponseTest {

    @Test
    void testRewriteUrl() throws Exception {
        ProxyRequest request = new ProxyRequest(HttpRequest.Method.GET, URI.create("http://domain.tld/zone/v2/dev/us-north-1/configserver"),
                Map.of(), null, List.of(URI.create("http://example.com")), Path.parse("configserver"));
        ProxyResponse proxyResponse = new ProxyResponse(
                request,
                "response link is http://configserver:4443/bla/bla/",
                200,
                URI.create("http://configserver:1234"),
                "application/json");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        proxyResponse.render(outputStream);
        String document = outputStream.toString(StandardCharsets.UTF_8);
        assertEquals("response link is http://domain.tld/zone/v2/dev/us-north-1/bla/bla/", document);
    }

    @Test
    void testRewriteSecureUrl() throws Exception {
        ProxyRequest request = new ProxyRequest(HttpRequest.Method.GET, URI.create("https://domain.tld/zone/v2/prod/eu-south-3/configserver"),
                Map.of(), null, List.of(URI.create("http://example.com")), Path.parse("configserver"));
        ProxyResponse proxyResponse = new ProxyResponse(
                request,
                "response link is http://configserver:4443/bla/bla/",
                200,
                URI.create("http://configserver:1234"),
                "application/json");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        proxyResponse.render(outputStream);
        String document = outputStream.toString(StandardCharsets.UTF_8);
        assertEquals("response link is https://domain.tld/zone/v2/prod/eu-south-3/bla/bla/", document);
    }

}
