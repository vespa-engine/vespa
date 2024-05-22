// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import ai.vespa.http.DomainName;
import ai.vespa.http.HttpURL;
import ai.vespa.http.HttpURL.Path;
import ai.vespa.http.HttpURL.Query;
import ai.vespa.http.HttpURL.Scheme;
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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author musum
 */
public class TesterClient {

    private final CloseableHttpClient httpClient = VespaHttpClientBuilder.custom().buildClient();
    private static final Logger logger = Logger.getLogger(TesterClient.class.getName());

    public HttpResponse getStatus(String testerHostname, int port) {
        URI testerUri = testerUrl(testerHostname, port, "tester", "v1", "status").asURI();
        return execute(new HttpGet(testerUri), "Failed to get tester status");
    }

    public HttpResponse getLog(String testerHostname, int port, Long after) {
        URI testerUri = testerUrl(testerHostname, port, "tester", "v1", "log")
                .withQuery(Query.empty().set("after", Long.toString(after))).asURI();
        return execute(new HttpGet(testerUri), "Failed to get tester logs");
    }

    public HttpResponse startTests(String testerHostname, int port, String suite, byte[] config) {
        URI testerUri = testerUrl(testerHostname, port, "tester", "v1", "run", suite).asURI();
        HttpPost request = new HttpPost(testerUri);
        request.setEntity(new ByteArrayEntity(config, ContentType.DEFAULT_BINARY));
        return execute(request, "Failed to start tests");
    }

    public HttpResponse isTesterReady(String testerHostname, int port) {
        URI testerUri = testerUrl(testerHostname, port, "status.html").asURI();
        return execute(new HttpGet(testerUri), "/status.html did not return 200 OK");
    }

    public HttpResponse getReport(String testerHostname, int port) {
        URI testerUri = testerUrl(testerHostname, port, "tester", "v1", "report").asURI();
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

    private HttpURL testerUrl(String testerHostname, int port, String... path) {
        return HttpURL.create(Scheme.https, DomainName.of(testerHostname), port, Path.empty().append(List.of(path)).withoutTrailingSlash());
    }

}
