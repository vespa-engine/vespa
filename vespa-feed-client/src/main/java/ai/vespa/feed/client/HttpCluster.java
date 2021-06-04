// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.H2AsyncClientBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.hc.core5.http.ssl.TlsCiphers.excludeH2Blacklisted;
import static org.apache.hc.core5.http.ssl.TlsCiphers.excludeWeak;

/**
 * @author jonmv
 */
class HttpCluster implements Cluster {

    private final List<Endpoint> endpoints = new ArrayList<>();

    public HttpCluster(FeedClientBuilder builder) throws IOException {
        for (URI endpoint : builder.endpoints)
            for (int i = 0; i < builder.connectionsPerEndpoint; i++)
                endpoints.add(new Endpoint(createHttpClient(builder), endpoint));
    }

    @Override
    public void dispatch(SimpleHttpRequest request, CompletableFuture<SimpleHttpResponse> vessel) {
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

    @Override
    public void close() {
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
                                                                                                        .setIoThreadCount(1)
                                                                                                        .setTcpNoDelay(true)
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

}
