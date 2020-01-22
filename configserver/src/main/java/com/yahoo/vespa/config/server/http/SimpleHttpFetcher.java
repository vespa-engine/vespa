// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import ai.vespa.util.http.VespaHttpClientBuilder;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.log.LogLevel;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.logging.Logger;

public class SimpleHttpFetcher implements HttpFetcher {
    private static final Logger logger = Logger.getLogger(SimpleHttpFetcher.class.getName());

    private final CloseableHttpClient client = VespaHttpClientBuilder.create().build();

    @Override
    public HttpResponse get(Params params, URL url) {
        try {
            HttpGet request = new HttpGet(url.toURI());
            request.addHeader("Connection", "Close");
            request.setConfig(
                    RequestConfig.custom()
                            .setConnectTimeout(params.readTimeoutMs)
                            .setSocketTimeout(params.readTimeoutMs)
                            .build());
            try (CloseableHttpResponse response = client.execute(request)) {
                HttpEntity entity = response.getEntity();
                return new StaticResponse(
                        response.getStatusLine().getStatusCode(),
                        entity.getContentType().getValue(),
                        EntityUtils.toString(entity));
            }
        } catch (ConnectTimeoutException | SocketTimeoutException e) {
            String message = "Timed out after " + params.readTimeoutMs + " ms reading response from " + url;
            logger.log(LogLevel.WARNING, message, e);
            throw new RequestTimeoutException(message);
        } catch (IOException e) {
            String message = "Failed to get response from " + url;
            logger.log(LogLevel.WARNING, message, e);
            throw new InternalServerException(message);
        } catch (URISyntaxException e) {
            String message = "Invalid URL: " + e.getMessage();
            logger.log(LogLevel.WARNING, message, e);
            throw new InternalServerException(message, e);
        }
    }
}
