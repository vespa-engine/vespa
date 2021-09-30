// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.hc.core5.http.ssl.TlsCiphers.excludeH2Blacklisted;
import static org.apache.hc.core5.http.ssl.TlsCiphers.excludeWeak;

/**
 * @author jonmv
 */
class ApacheCluster implements Cluster {

    private final List<Endpoint> endpoints = new ArrayList<>();
    private final List<BasicHeader> defaultHeaders = Arrays.asList(new BasicHeader("User-Agent", String.format("vespa-feed-client/%s", Vespa.VERSION)),
                                                                   new BasicHeader("Vespa-Client-Version", Vespa.VERSION));
    private final RequestConfig defaultConfig = RequestConfig.custom()
                                                             .setConnectTimeout(Timeout.ofSeconds(10))
                                                             .setConnectionRequestTimeout(Timeout.DISABLED)
                                                             .setResponseTimeout(Timeout.ofMinutes(5))
                                                             .build();

    ApacheCluster(FeedClientBuilder builder) throws IOException {
        for (URI endpoint : builder.endpoints)
            for (int i = 0; i < builder.connectionsPerEndpoint; i++)
                endpoints.add(new Endpoint(createHttpClient(builder), endpoint));
    }

    @Override
    public void dispatch(HttpRequest wrapped, CompletableFuture<HttpResponse> vessel) {
        int index = 0;
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < endpoints.size(); i++)
            if (endpoints.get(i).inflight.get() < min) {
                index = i;
                min = endpoints.get(i).inflight.get();
            }
        Endpoint endpoint = endpoints.get(index);

        try {
            SimpleHttpRequest request = new SimpleHttpRequest(wrapped.method(), wrapped.path());
            request.setScheme(endpoint.url.getScheme());
            request.setAuthority(new URIAuthority(endpoint.url.getHost(), portOf(endpoint.url)));
            request.setConfig(defaultConfig);
            defaultHeaders.forEach(request::setHeader);
            wrapped.headers().forEach((name, value) -> request.setHeader(name, value.get()));
            if (wrapped.body() != null)
                request.setBody(wrapped.body(), ContentType.APPLICATION_JSON);

            endpoint.inflight.incrementAndGet();
            endpoint.client.execute(request,
                                    new FutureCallback<SimpleHttpResponse>() {
                                        @Override public void completed(SimpleHttpResponse response) { vessel.complete(new ApacheHttpResponse(response)); }
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
        SSLContext sslContext = builder.constructSslContext();
        String[] allowedCiphers = excludeH2Blacklisted(excludeWeak(sslContext.getSupportedSSLParameters().getCipherSuites()));
        if (allowedCiphers.length == 0)
            throw new IllegalStateException("No adequate SSL cipher suites supported by the JVM");

        ClientTlsStrategyBuilder tlsStrategyBuilder = ClientTlsStrategyBuilder.create()
                                                                              .setCiphers(allowedCiphers)
                                                                              .setSslContext(sslContext);
        if (builder.hostnameVerifier != null)
            tlsStrategyBuilder.setHostnameVerifier(builder.hostnameVerifier);

        return HttpAsyncClients.createHttp2Minimal(H2Config.custom()
                                                           .setMaxConcurrentStreams(builder.maxStreamsPerConnection)
                                                           .setCompressionEnabled(true)
                                                           .setPushEnabled(false)
                                                           .setInitialWindowSize(Integer.MAX_VALUE)
                                                           .build(),
                                                   IOReactorConfig.custom()
                                                                  .setIoThreadCount(2)
                                                                  .setTcpNoDelay(true)
                                                                  .setSoTimeout(Timeout.ofSeconds(10))
                                                                  .build(),
                                                   tlsStrategyBuilder.build());
    }

    private static int portOf(URI url) {
        return url.getPort() == -1 ? url.getScheme().equals("http") ? 80 : 443
                                   : url.getPort();
    }

    private static class ApacheHttpResponse implements HttpResponse {

        private final SimpleHttpResponse wrapped;

        private ApacheHttpResponse(SimpleHttpResponse wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public int code() {
            return wrapped.getCode();
        }

        @Override
        public byte[] body() {
            return wrapped.getBodyBytes();
        }

        @Override
        public String toString() {
            return "HTTP response with code " + code() +
                   (body() != null ? " and body '" + new String(body(), UTF_8) + "'" : "");
        }

    }

}
