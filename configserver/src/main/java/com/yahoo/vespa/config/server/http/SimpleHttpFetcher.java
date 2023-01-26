// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import ai.vespa.util.http.hc5.VespaHttpClientBuilder;
import com.yahoo.config.provision.security.NodeHostnameVerifier;
import com.yahoo.container.jdisc.HttpResponse;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimpleHttpFetcher implements HttpFetcher {
    private static final Logger logger = Logger.getLogger(SimpleHttpFetcher.class.getName());

    private final CloseableHttpClient client;

    public SimpleHttpFetcher() { this(null); }

    public SimpleHttpFetcher(NodeHostnameVerifier verifier) {
        HttpClientBuilder b = verifier != null
                ? VespaHttpClientBuilder.create(PoolingHttpClientConnectionManager::new, verifier::verify)
                : VespaHttpClientBuilder.create();
        this.client = b.build();
    }

    @Override
    public HttpResponse get(Params params, URI url) {
        try {
            HttpGet request = new HttpGet(url);
            request.addHeader("Connection", "Close");
            request.setConfig(
                    RequestConfig.custom()
                            .setConnectTimeout(Timeout.ofMilliseconds(params.readTimeoutMs))
                            .setResponseTimeout(Timeout.ofMilliseconds(params.readTimeoutMs))
                            .build());
            try (CloseableHttpResponse response = client.execute(request)) {
                HttpEntity entity = response.getEntity();
                return new StaticResponse(
                        response.getCode(),
                        entity.getContentType(),
                        entity.getContent().readAllBytes());
            }
        } catch (SocketTimeoutException e) {
            String message = "Timed out after " + params.readTimeoutMs + " ms reading response from " + url;
            logger.log(Level.WARNING, message, e);
            throw new RequestTimeoutException(message);
        } catch (IOException e) {
            String message = "Failed to get response from " + url;
            logger.log(Level.WARNING, message, e);
            throw new InternalServerException(message);
        }
    }

}
