// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.feed.client;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * HTTP implementation of {@link FeedClient}
 *
 * @author bjorncs
 * @author jonmv
 */
class HttpFeedClient implements FeedClient {

    private final CloseableHttpAsyncClient httpClient;
    private final URI endpoint;
    private final Map<String, Supplier<String>> requestHeaders;
    private final int maxPendingRequests;

    HttpFeedClient(FeedClientBuilder builder) {
        this.httpClient = createHttpClient(builder);
        this.endpoint = getEndpoint(builder);
        this.requestHeaders = new HashMap<>(builder.requestHeaders);
        this.maxPendingRequests = (builder.maxConnections != null ? builder.maxConnections : 4)
                                * (builder.maxStreamsPerConnection != null ? builder.maxStreamsPerConnection : 128);

        this.httpClient.start();
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
                        .setMaxConcurrentStreams(builder.maxStreamsPerConnection != null ? builder.maxStreamsPerConnection : 128)
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
    public CompletableFuture<Result> put(DocumentId documentId, String documentJson, OperationParameters params) {
        return send("POST", documentId, requireNonNull(documentJson), params);
    }

    @Override
    public CompletableFuture<Result> update(DocumentId documentId, String updateJson, OperationParameters params) {
        return send("PUT", documentId, requireNonNull(updateJson), params);
    }

    @Override
    public CompletableFuture<Result> remove(DocumentId documentId, OperationParameters params) {
        return send("DELETE", documentId, null, params);
    }

    @Override public void close() throws IOException { this.httpClient.close(); }

    private CompletableFuture<Result> send(String method, DocumentId documentId, String operationJson, OperationParameters params) {
        SimpleHttpRequest request = new SimpleHttpRequest(method, operationUrl(endpoint, documentId, params));
        requestHeaders.forEach(request::setHeader);
        if (operationJson != null)
            request.setBody(operationJson, ContentType.APPLICATION_JSON);

        CompletableFuture<Result> future = new CompletableFuture<>();
        httpClient.execute(new SimpleHttpRequest(method, endpoint),
                                  new FutureCallback<SimpleHttpResponse>() {
                                      @Override public void completed(SimpleHttpResponse response) {
                                          Result result = toResult(response, documentId);
                                          future.complete(result); // TODO: add retrying
                                      }
                                      @Override public void failed(Exception ex) {
                                          Result result = new Result(Result.Type.failure, documentId, ex.getMessage(), null);
                                          future.completeExceptionally(ex); // TODO: add retrying
                                      }
                                      @Override public void cancelled() {
                                          Result result = new Result(Result.Type.cancelled, documentId, null, null);
                                          future.cancel(false); // TODO: add retrying
                                      }
                                  });
        return future;
    }

    static Result toResult(SimpleHttpResponse response, DocumentId documentId) {
        return new Result(Result.Type.failure, documentId, null, null); // TODO: parse JSON and status code
    }

    static List<String> toPath(DocumentId documentId) {
        List<String> path = new ArrayList<>();
        path.add("document");
        path.add("v1");
        path.add(documentId.namespace());
        path.add(documentId.documentType());
        if (documentId.number().isPresent()) {
            path.add("number");
            path.add(Long.toUnsignedString(documentId.number().getAsLong()));
        }
        else if (documentId.group().isPresent()) {
            path.add("group");
            path.add(documentId.group().get());
        }
        else {
            path.add("docid");
        }
        path.add(documentId.userSpecific());

        return path;
    }

    static URI operationUrl(URI endpoint, DocumentId documentId, OperationParameters params) {
        URIBuilder url = new URIBuilder(endpoint);
        url.setPathSegments(toPath(documentId));

        if (params.createIfNonExistent()) url.addParameter("create", "true");
        params.testAndSetCondition().ifPresent(condition -> url.addParameter("condition", condition));
        params.timeout().ifPresent(timeout -> url.addParameter("timeout", timeout.toMillis() + "ms"));
        params.route().ifPresent(route -> url.addParameter("route", route));
        params.tracelevel().ifPresent(tracelevel -> url.addParameter("tracelevel", Integer.toString(tracelevel)));

        try {
            return url.build();
        }
        catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

}
