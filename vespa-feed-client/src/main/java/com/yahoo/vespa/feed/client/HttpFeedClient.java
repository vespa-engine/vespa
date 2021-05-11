// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.feed.client;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * HTTP implementation of {@link FeedClient}
 *
 * @author bjorncs
 */
class HttpFeedClient implements FeedClient {

    private final CloseableHttpAsyncClient httpClient;
    private final URI endpoint;
    private final Map<String, Supplier<String>> requestHeaders;

    HttpFeedClient(FeedClientBuilder builder) {
        this.httpClient = createHttpClient(builder);
        this.endpoint = getEndpoint(builder);
        this.requestHeaders = new HashMap<>(builder.requestHeaders);
    }

    private static CloseableHttpAsyncClient createHttpClient(FeedClientBuilder builder) {
        HttpAsyncClientBuilder httpClientBuilder = HttpAsyncClientBuilder.create()
                .setUserAgent(String.format("vespa-feed-client/%s", Vespa.VERSION))
                .setDefaultHeaders(Collections.singletonList(new BasicHeader("Vespa-Client-Version", Vespa.VERSION)))
                .disableCookieManagement()
                .disableRedirectHandling()
                .disableConnectionState()
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(Timeout.ofSeconds(10))
                        .build())
                .setDefaultRequestConfig(
                        RequestConfig.custom()
                                .setConnectTimeout(Timeout.ofSeconds(10))
                                .setConnectionRequestTimeout(Timeout.DISABLED)
                                .setResponseTimeout(Timeout.ofMinutes(5))
                                .build())
                .setH2Config(H2Config.custom()
                        .setMaxConcurrentStreams(128)
                        .setPushEnabled(false)
                        .build());

        int maxConnections = builder.maxConnections != null ? builder.maxConnections : 4;
        PoolingAsyncClientConnectionManagerBuilder connectionManagerBuilder = PoolingAsyncClientConnectionManagerBuilder.create()
                .setConnectionTimeToLive(TimeValue.ofMinutes(10))
                .setMaxConnTotal(maxConnections)
                .setMaxConnPerRoute(maxConnections)
                .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.LAX);
        if (builder.sslContext != null) {
            ClientTlsStrategyBuilder tlsStrategyBuilder = ClientTlsStrategyBuilder.create()
                    .setSslContext(builder.sslContext);
            if (builder.hostnameVerifier != null) {
                tlsStrategyBuilder.setHostnameVerifier(builder.hostnameVerifier);
            }
            connectionManagerBuilder.setTlsStrategy(tlsStrategyBuilder.build());
        }
        httpClientBuilder.setConnectionManager(connectionManagerBuilder.build());
        return httpClientBuilder.build();
    }

    private static URI getEndpoint(FeedClientBuilder builder) {
        if (builder.endpoint == null) throw new IllegalArgumentException("Endpoint must be specified");
        return builder.endpoint;
    }

    @Override
    public Future<Result> put(String documentId, String documentJson, OperationParameters params, ResultCallback callback) {
        return null;
    }

    @Override
    public Future<Result> remove(String documentId, OperationParameters params, ResultCallback callback) {
        return null;
    }

    @Override
    public Future<Result> update(String documentId, String documentJson, OperationParameters params, ResultCallback callback) {
        return null;
    }

    @Override public void close() throws IOException { this.httpClient.close(); }

}
