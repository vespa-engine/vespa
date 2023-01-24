// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import ai.vespa.util.http.hc5.VespaHttpClientBuilder;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.yolean.Exceptions;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.http.client.utils.URIBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author musum
 */
public class TesterClient {

    private final CloseableHttpClient httpClient = VespaHttpClientBuilder.custom().buildClient();
    private static final Logger logger = Logger.getLogger(TesterClient.class.getName());

    public HttpResponse getStatus(String testerHostname, int port) {
        URI testerUri = createURI(testerHostname, port, "/tester/v1/status");

        return execute(new HttpGet(testerUri), "Failed to get tester status");
    }

    public HttpResponse getLog(String testerHostname, int port, Long after) {
        URI testerUri;
        try {
            testerUri = createBuilder(testerHostname, port, "/tester/v1/log")
                    .addParameter("after", String.valueOf(after))
                    .build();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }

        return execute(new HttpGet(testerUri), "Failed to get tester logs");
    }

    public HttpResponse startTests(String testerHostname, int port, String suite, byte[] config) {
        URI testerUri = createURI(testerHostname, port, "/tester/v1/run/" + suite);
        HttpPost request = new HttpPost(testerUri);
        request.setEntity(new ByteArrayEntity(config, ContentType.DEFAULT_BINARY));

        return execute(request, "Failed to start tests");
    }

    public HttpResponse isTesterReady(String testerHostname, int port) {
        URI testerUri = createURI(testerHostname, port, "/status.html");

        return execute(new HttpGet(testerUri), "/status.html did not return 200 OK");
    }

    public HttpResponse getReport(String testerHostname, int port) {
        URI testerUri = createURI(testerHostname, port, "/tester/v1/report");
        return execute(new HttpGet(testerUri), "Failed to get test report");
    }

    @SuppressWarnings("deprecation")
    private HttpResponse execute(HttpUriRequest request, String messageIfRequestFails) {
        logger.log(Level.FINE, () -> "Sending request to tester container " + request.getRequestUri());
        try {
            return new ProxyResponse(httpClient.execute(request));
        } catch (IOException e) {
            logger.warning(messageIfRequestFails + ": " + Exceptions.toMessageString(e));
            return HttpErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private URIBuilder createBuilder(String testerHostname, int port, String path) {
        return new URIBuilder()
                .setScheme("https")
                .setHost(testerHostname)
                .setPort(port)
                .setPath(path);
    }

    private URI createURI(String testerHostname, int port, String path) {
        try {
            return createBuilder(testerHostname, port, path).build();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

}
