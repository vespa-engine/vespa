// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import ai.vespa.http.HttpURL;
import ai.vespa.util.http.hc5.VespaHttpClientBuilder;
import com.yahoo.container.jdisc.EmptyResponse;
import com.yahoo.container.jdisc.HttpResponse;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
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

    /**
     * Fetches logs from the log server for a given application.
     * An empty response will be returned if we are unable to fetch logs and
     * the deployment is less than 5 minutes old
     */
    @SuppressWarnings("deprecation")
    public HttpResponse getLogs(HttpURL logServerUri, Optional<Instant> deployTime) {
        HttpGet get = new HttpGet(logServerUri.asURI());
        try {
            return new ProxyResponse(httpClient.execute(get));
        } catch (ConnectionRequestTimeoutException | ConnectException e) {
            return new GatewayTimeoutResponse(504);
        } catch (IOException e) {
            if (deployTime.isPresent() && Instant.now().isBefore(deployTime.get().plus(Duration.ofMinutes(5))))
                return new EmptyResponse();

            throw new RuntimeException("Failed to get logs from " + logServerUri, e);
        }
    }

    private static class GatewayTimeoutResponse extends HttpResponse {

        public GatewayTimeoutResponse(int status) { super(status); }

        public GatewayTimeoutResponse() { this(504); }

        @Override
        public void render(OutputStream outputStream) {
            // NOP
        }

    }

}
