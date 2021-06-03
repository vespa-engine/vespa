// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.H2AsyncClientBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.apache.hc.core5.http.ssl.TlsCiphers.excludeH2Blacklisted;
import static org.apache.hc.core5.http.ssl.TlsCiphers.excludeWeak;

/**
 * HTTP implementation of {@link FeedClient}
 *
 * @author bjorncs
 * @author jonmv
 */
class HttpFeedClient implements FeedClient {

    private final Map<String, Supplier<String>> requestHeaders;
    private final RequestStrategy requestStrategy;
    private final List<Endpoint> endpoints = new ArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    HttpFeedClient(FeedClientBuilder builder) throws IOException {
        this.requestHeaders = new HashMap<>(builder.requestHeaders);
        this.requestStrategy = new HttpRequestStrategy(builder);
        for (URI endpoint : builder.endpoints)
            for (int i = 0; i < builder.connectionsPerEndpoint; i++)
                endpoints.add(new Endpoint(createHttpClient(builder), endpoint));
    }

    private static class Endpoint {

        private final CloseableHttpAsyncClient client;
        private final AtomicInteger inflight = new AtomicInteger(0);
        private final URI url;

        private Endpoint(CloseableHttpAsyncClient client, URI url) {
            this.client = client;
            this.url = url;

            this.client.start();
        }

    }

    private static CloseableHttpAsyncClient createHttpClient(FeedClientBuilder builder) throws IOException {
        H2AsyncClientBuilder httpClientBuilder = H2AsyncClientBuilder.create()
                                                                     .setUserAgent(String.format("vespa-feed-client/%s", Vespa.VERSION))
                                                                     .setDefaultHeaders(Collections.singletonList(new BasicHeader("Vespa-Client-Version", Vespa.VERSION)))
                                                                     .disableCookieManagement()
                                                                     .disableRedirectHandling()
                                                                     .disableAutomaticRetries()
                                                                     .setIOReactorConfig(IOReactorConfig.custom()
                                                                                                        .setSoTimeout(Timeout.ofSeconds(10))
                                                                                                        .build())
                                                                     .setDefaultRequestConfig(
                                                                             RequestConfig.custom()
                                                                                          .setConnectTimeout(Timeout.ofSeconds(10))
                                                                                          .setConnectionRequestTimeout(Timeout.DISABLED)
                                                                                          .setResponseTimeout(Timeout.ofMinutes(5))
                                                                                          .build())
                                                                     .setH2Config(H2Config.initial()
                                                                                          .setMaxConcurrentStreams(builder.maxStreamsPerConnection)
                                                                                          .setCompressionEnabled(true)
                                                                                          .setPushEnabled(false)
                                                                                          .build());

        SSLContext sslContext = constructSslContext(builder);
        String[] allowedCiphers = excludeH2Blacklisted(excludeWeak(sslContext.getSupportedSSLParameters().getCipherSuites()));
        if (allowedCiphers.length == 0)
            throw new IllegalStateException("No adequate SSL cipher suites supported by the JVM");

        ClientTlsStrategyBuilder tlsStrategyBuilder = ClientTlsStrategyBuilder.create()
                                                                              .setCiphers(allowedCiphers)
                                                                              .setSslContext(sslContext);
        if (builder.hostnameVerifier != null) {
            tlsStrategyBuilder.setHostnameVerifier(builder.hostnameVerifier);
        }
        return httpClientBuilder.setTlsStrategy(tlsStrategyBuilder.build())
                                .build();
    }

    private static SSLContext constructSslContext(FeedClientBuilder builder) throws IOException {
        if (builder.sslContext != null) return builder.sslContext;
        SslContextBuilder sslContextBuilder = new SslContextBuilder();
        if (builder.certificate != null && builder.privateKey != null) {
            sslContextBuilder.withCertificateAndKey(builder.certificate, builder.privateKey);
        }
        if (builder.caCertificates != null) {
            sslContextBuilder.withCaCertificates(builder.caCertificates);
        }
        return sslContextBuilder.build();
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
    public void close(boolean graceful) {
        closed.set(true);
        if (graceful)
            requestStrategy.await();

        requestStrategy.destroy();
        Throwable thrown = null;
        for (Endpoint endpoint : endpoints)
            try {
                endpoint.client.close();
            }
            catch (Throwable t) {
                if (thrown == null) thrown = t;
                else thrown.addSuppressed(t);
            }
        if (thrown != null) throw new RuntimeException(thrown);
    }

    private void ensureOpen() {
        if (requestStrategy.hasFailed())
            close();

        if (closed.get())
            throw new IllegalStateException("Client is closed, no further operations may be sent");
    }

    private CompletableFuture<Result> send(String method, DocumentId documentId, String operationJson, OperationParameters params) {
        ensureOpen();

        String path = operationPath(documentId, params).toString();
        SimpleHttpRequest request = new SimpleHttpRequest(method, path);
        requestHeaders.forEach((name, value) -> request.setHeader(name, value.get()));
        if (operationJson != null)
            request.setBody(operationJson, ContentType.APPLICATION_JSON);

        return requestStrategy.enqueue(documentId, request, this::send)
                              .handle((response, thrown) -> {
                                  if (thrown != null) {
                                      // TODO: What to do with exceptions here? Ex on 400, 401, 403, etc, and wrap and throw?
                                      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                                      thrown.printStackTrace(new PrintStream(buffer));
                                      return new Result(Result.Type.failure, documentId, buffer.toString(), null);
                                  }
                                  return toResult(response, documentId);
                              });
    }

    /** Sends the given request to the client with the least current inflight requests, completing the given vessel when done. */
    private void send(SimpleHttpRequest request, CompletableFuture<SimpleHttpResponse> vessel) {
        int index = 0;
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < endpoints.size(); i++)
            if (endpoints.get(i).inflight.get() < min) {
                index = i;
                min = endpoints.get(i).inflight.get();
            }

        Endpoint endpoint = endpoints.get(index);
        endpoint.inflight.incrementAndGet();
        try {
            request.setScheme(endpoint.url.getScheme());
            request.setAuthority(new URIAuthority(endpoint.url.getHost(), endpoint.url.getPort()));
            endpoint.client.execute(request,
                                    new FutureCallback<SimpleHttpResponse>() {
                                        @Override public void completed(SimpleHttpResponse response) { vessel.complete(response); }
                                        @Override public void failed(Exception ex) { vessel.completeExceptionally(ex); }
                                        @Override public void cancelled() { vessel.cancel(false); }
                                    });
        }
        catch (Throwable thrown) {
            vessel.completeExceptionally(thrown);
        }
        vessel.whenComplete((__, ___) -> endpoint.inflight.decrementAndGet());
    }

    static Result toResult(SimpleHttpResponse response, DocumentId documentId) {
        Result.Type type;
        switch (response.getCode()) {
            case 200: type = Result.Type.success; break;
            case 412: type = Result.Type.conditionNotMet; break;
            default:  type = Result.Type.failure;
        }
        Map<String, String> responseJson = null; // TODO: parse JSON on error.
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

    static URI operationPath(DocumentId documentId, OperationParameters params) {
        URIBuilder url = new URIBuilder();
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
