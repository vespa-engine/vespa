// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import ai.vespa.http.DomainName;
import ai.vespa.http.HttpURL;
import com.yahoo.container.jdisc.EmptyResponse;
import com.yahoo.container.jdisc.HttpResponse;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static com.yahoo.jdisc.http.HttpResponse.Status.SERVICE_UNAVAILABLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author hmusum
 */
public class LogRetrieverTest {

    private final LogRetriever logRetriever = new LogRetriever();

    @Test
    public void returns_service_unavailable_when_unable_to_connect() throws IOException {
        HttpResponse response = logRetriever.getLogs(unusedPortUri(), Optional.of(Instant.now().minus(Duration.ofHours(1))));
        assertEquals(SERVICE_UNAVAILABLE, response.getStatus());
        assertTrue(SessionHandlerTest.getRenderedString(response).contains("\"error-code\":\"SERVICE_UNAVAILABLE\""));

        response = logRetriever.getLogs(unusedPortUri(), Optional.empty());
        assertEquals(SERVICE_UNAVAILABLE, response.getStatus());
    }

    @Test
    public void returns_empty_response_when_unable_to_connect_shortly_after_deployment() throws IOException {
        HttpResponse response = logRetriever.getLogs(unusedPortUri(), Optional.of(Instant.now().minus(Duration.ofMinutes(1))));
        assertTrue(response instanceof EmptyResponse);
    }

    private static HttpURL unusedPortUri() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        return HttpURL.create(HttpURL.Scheme.http, DomainName.localhost, port, HttpURL.Path.parse("logs"));
    }

}
