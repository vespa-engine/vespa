// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import ai.vespa.http.HttpURL;
import ai.vespa.util.http.hc5.VespaHttpClientBuilder;
import com.yahoo.container.jdisc.EmptyResponse;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.yolean.Exceptions;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * @author olaaun
 */
public class LogRetriever {

    private final CloseableHttpClient httpClient = VespaHttpClientBuilder.custom()
                                                                         .connectTimeout(Timeout.ofSeconds(5))
                                                                         .socketTimeout(Timeout.ofSeconds(45))
                                                                         .buildClient();

    @SuppressWarnings("deprecation")
    public HttpResponse getLogs(HttpURL logServerUri, Optional<Instant> deployTime) {
        HttpGet get = new HttpGet(logServerUri.asURI());
        try {
            return new ProxyResponse(httpClient.execute(get));
        } catch (IOException e) {
            // It takes some time before nodes are up after first-time deployment, return empty log for up to 2 minutes
            // if getting logs fail
            if (deployTime.isPresent() && Instant.now().isBefore(deployTime.get().plus(Duration.ofMinutes(2))))
                return new EmptyResponse();

            return HttpErrorResponse.internalServerError("Failed to get logs: " + Exceptions.toMessageString(e));
        }
    }

}
