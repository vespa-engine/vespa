// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import ai.vespa.util.http.hc4.VespaHttpClientBuilder;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;

/**
 * @author hakonhall
 */
class ApacheHttpClient implements AutoCloseable {
    private final URL url;
    private final CloseableHttpClient client;

    @FunctionalInterface
    interface Handler<T> {
        T handle(CloseableHttpResponse httpResponse) throws Exception;
    }

    static CloseableHttpClient makeCloseableHttpClient(Duration timeout, Duration keepAlive) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout((int) timeout.toMillis()) // establishment of connection
                .setConnectionRequestTimeout((int) timeout.toMillis())  // connection from connection manager
                .setSocketTimeout((int) timeout.toMillis()) // waiting for data
                .build();

        ConnectionKeepAliveStrategy keepAliveStrategy =
                new DefaultConnectionKeepAliveStrategy() {
                    @Override
                    public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
                        long keepAliveMillis = super.getKeepAliveDuration(response, context);
                        if (keepAliveMillis == -1) {
                            keepAliveMillis = keepAlive.toMillis();
                        }
                        return keepAliveMillis;
                    }
                };

        return VespaHttpClientBuilder.createWithBasicConnectionManager()
                .setUserAgent("service-monitor")
                .setKeepAliveStrategy(keepAliveStrategy)
                .disableAutomaticRetries()
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    ApacheHttpClient(URL url, Duration timeout, Duration keepAlive) {
        this(url, makeCloseableHttpClient(timeout, keepAlive));
    }

    ApacheHttpClient(URL url, CloseableHttpClient client) {
        this.url = url;
        this.client = client;
    }

    <T> T get(Handler<T> handler) throws Exception {
        try (CloseableHttpResponse httpResponse = client.execute(new HttpGet(url.toString()))) {
            return handler.handle(httpResponse);
        }
    }

    @Override
    public void close() throws IOException {
        client.close();
    }
}
