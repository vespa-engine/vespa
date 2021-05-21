// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

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
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * HTTP implementation of {@link FeedClient}
 *
 * @author bjorncs
 * @author jonmv
 */
class HttpFeedClient implements FeedClient {

    private final URI endpoint;
    private final Map<String, Supplier<String>> requestHeaders;
    private final HttpRequestStrategy requestStrategy;
    private final CloseableHttpAsyncClient httpClient;
    private final AtomicBoolean closed = new AtomicBoolean();

    HttpFeedClient(FeedClientBuilder builder) {
        this.endpoint = builder.endpoint;
        this.requestHeaders = new HashMap<>(builder.requestHeaders);

        this.requestStrategy = new HttpRequestStrategy(builder);
        this.httpClient = createHttpClient(builder, requestStrategy);
        this.httpClient.start();
    }

    private static CloseableHttpAsyncClient createHttpClient(FeedClientBuilder builder, HttpRequestStrategy retryStrategy) {
        HttpAsyncClientBuilder httpClientBuilder = HttpAsyncClientBuilder.create()
                .setUserAgent(String.format("vespa-feed-client/%s", Vespa.VERSION))
                .setDefaultHeaders(Collections.singletonList(new BasicHeader("Vespa-Client-Version", Vespa.VERSION)))
                .disableCookieManagement()
                .disableRedirectHandling()
                .disableConnectionState()
                .setRetryStrategy(retryStrategy)
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(Timeout.ofSeconds(10))
                        .build())
                .setDefaultRequestConfig(
                        RequestConfig.custom()
                                .setConnectTimeout(Timeout.ofSeconds(10))
                                .setConnectionRequestTimeout(Timeout.DISABLED)
                                .setResponseTimeout(Timeout.ofMinutes(5))
                                .build())
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                .setH2Config(H2Config.initial()
                        .setMaxConcurrentStreams(builder.maxStreamsPerConnection)
                        .setCompressionEnabled(true)
                        .setPushEnabled(false)
                        .build());

        PoolingAsyncClientConnectionManagerBuilder connectionManagerBuilder = PoolingAsyncClientConnectionManagerBuilder.create()
                .setConnectionTimeToLive(TimeValue.ofMinutes(10))
                .setMaxConnTotal(builder.maxConnections)
                .setMaxConnPerRoute(builder.maxConnections)
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

    @Override
    public void close() throws IOException {
        if ( ! closed.getAndSet(true))
            httpClient.close();
    }

    private CompletableFuture<Result> send(String method, DocumentId documentId, String operationJson, OperationParameters params) {
        SimpleHttpRequest request = new SimpleHttpRequest(method, operationUrl(endpoint, documentId, params));
        requestHeaders.forEach((name, value) -> request.setHeader(name, value.get()));
        if (operationJson != null)
            request.setBody(operationJson, ContentType.APPLICATION_JSON);

        return requestStrategy.enqueue(documentId, future -> {
            httpClient.execute(request,
                               new FutureCallback<SimpleHttpResponse>() {
                                   @Override public void completed(SimpleHttpResponse response) { future.complete(response); }
                                   @Override public void failed(Exception ex) { future.completeExceptionally(ex); }
                                   @Override public void cancelled() { future.cancel(false); }
                               });
        }).handle((response, thrown) -> {
            if (thrown != null) {
                if (requestStrategy.hasFailed()) {
                    try { close(); }
                    catch (IOException exception) { throw new UncheckedIOException(exception); }
                }
                return new Result(Result.Type.failure, documentId, thrown.getMessage(), null);
            }
            return toResult(response, documentId);
        });
    }

    static Result toResult(SimpleHttpResponse response, DocumentId documentId) {
        Result.Type type;
        switch (response.getCode()) {
            case 200: type = Result.Type.success; break;
            case 412: type = Result.Type.conditionNotMet; break;
            default:  type = Result.Type.failure;
        }
        Map<String, String> responseJson = null; // TODO: parse JSON.
        return new Result(type, documentId, response.getBodyText(), "trace");
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
