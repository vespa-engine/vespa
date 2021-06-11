// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.http.HttpClientTransportOverHTTP2;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jonmv
 */
class JettyCluster implements Cluster {

    private final List<Endpoint> endpoints = new ArrayList<>();

    JettyCluster(FeedClientBuilder builder) {
        for (URI endpoint : builder.endpoints)
            endpoints.add(new Endpoint(createJettyHttpClient(builder), endpoint));
    }

    private static HttpClient createJettyHttpClient(FeedClientBuilder builder) {
        try {
            SslContextFactory.Client clientSslCtxFactory = new SslContextFactory.Client();
            clientSslCtxFactory.setSslContext(builder.constructSslContext());
            clientSslCtxFactory.setHostnameVerifier(builder.hostnameVerifier);

            HTTP2Client wrapped = new HTTP2Client();
            wrapped.setSelectors(8);
            wrapped.setMaxConcurrentPushedStreams(builder.maxStreamsPerConnection);
            HttpClientTransport transport = new HttpClientTransportOverHTTP2(wrapped);
            HttpClient client = new HttpClient(transport, clientSslCtxFactory);
            client.setUserAgentField(new HttpField("User-Agent", String.format("vespa-feed-client/%s", Vespa.VERSION)));
            client.setDefaultRequestContentType("application/json");
            client.setFollowRedirects(false);
            client.setMaxRequestsQueuedPerDestination(builder.connectionsPerEndpoint * builder.maxStreamsPerConnection);
            client.setIdleTimeout(10000);
            client.setMaxConnectionsPerDestination(builder.connectionsPerEndpoint);
            client.setRequestBufferSize(1 << 16);

            client.start();
            return client;
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }


    @Override
    public void dispatch(HttpRequest request, CompletableFuture<HttpResponse> vessel) {
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
            Request jettyRequest = endpoint.client.newRequest(endpoint.uri.resolve(request.path()))
                                                  .method(request.method())
                                                  .timeout(5, TimeUnit.MINUTES)
                                                  .content(request.body() == null ? null : new BytesContentProvider("application/json", request.body()));
            request.headers().forEach((name, value) -> jettyRequest.header(name, value.get()));
            jettyRequest.send(new BufferingResponseListener() {
                @Override public void onComplete(Result result) {
                    if (result.isSucceeded())
                        vessel.complete(HttpResponse.of(result.getResponse().getStatus(),
                                                        getContent()));
                    else
                        vessel.completeExceptionally(result.getFailure());
                }
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
                endpoint.client.stop();
            }
            catch (Throwable t) {
                if (thrown == null) thrown = t;
                else thrown.addSuppressed(t);
            }
        if (thrown != null) throw new RuntimeException(thrown);
    }


    private static class Endpoint {

        private final HttpClient client;
        private final AtomicInteger inflight = new AtomicInteger(0);
        private final URI uri;

        private Endpoint(HttpClient client, URI uri) {
            this.client = client;
            this.uri = uri;
        }

    }
}
