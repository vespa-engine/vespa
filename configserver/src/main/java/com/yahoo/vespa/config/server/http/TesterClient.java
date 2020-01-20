// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import ai.vespa.util.http.VespaHttpClientBuilder;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.log.LogLevel;
import com.yahoo.yolean.Exceptions;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;

/**
 * @author musum
 */
public class TesterClient {

    private final HttpClient httpClient = VespaHttpClientBuilder.create().build();
    private static final Logger logger = Logger.getLogger(TesterClient.class.getName());

    public HttpResponse getStatus(String testerHostname, int port) {
        URI testerUri;
        try {
            testerUri = new URIBuilder()
                    .setScheme("https")
                    .setHost(testerHostname)
                    .setPort(port)
                    .setPath("/tester/v1/status")
                    .build();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }

        return execute(new HttpGet(testerUri), "Failed to get tester status");
    }

    public HttpResponse getLog(String testerHostname, int port, Long after) {
        URI testerUri;
        try {
            testerUri = new URIBuilder()
                    .setScheme("https")
                    .setHost(testerHostname)
                    .setPort(port)
                    .setPath("/tester/v1/log")
                    .addParameter("after", String.valueOf(after))
                    .build();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }

        return execute(new HttpGet(testerUri), "Failed to get tester logs");
    }

    public HttpResponse startTests(String testerHostname, String suite, String config) {
        throw new UnsupportedOperationException("Not implemented yet");
    }


    private HttpResponse execute(HttpUriRequest request, String messageIfRequestFails) {
        // TODO: Change log level to DEBUG
        logger.log(LogLevel.INFO, "Sending request to tester container " + request.getURI().toString());
        try {
            return new ProxyResponse(httpClient.execute(request));
        } catch (IOException e) {
            logger.warning(messageIfRequestFails + ": " + Exceptions.toMessageString(e));
            return HttpErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

}
