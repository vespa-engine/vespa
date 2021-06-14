// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jonmv
 */
public class OkCluster implements Cluster {

    private final List<Endpoint> endpoints = new ArrayList<>();

    OkCluster(FeedClientBuilder builder) {
        for (URI endpoint : builder.endpoints)
            for (int i = 0; i < builder.connectionsPerEndpoint; i++)
                endpoints.add(new Endpoint(createOkHttpClient(builder), endpoint));
    }

    private static OkHttpClient createOkHttpClient(FeedClientBuilder builder) {
        try {
            return new OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
                                             .callTimeout(5, TimeUnit.MINUTES)
                                             .readTimeout(30, TimeUnit.SECONDS)
                                             .writeTimeout(30, TimeUnit.SECONDS)
                                             .followRedirects(false)
                                             //.hostnameVerifier(builder.hostnameVerifier)
                                             .retryOnConnectionFailure(false)
                                             .sslSocketFactory(builder.constructSslContext().getSocketFactory(),
                                                               new X509ExtendedTrustManager() {
                                                                   @Override public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException { }
                                                                   @Override public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException { }
                                                                   @Override public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException { }
                                                                   @Override public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException { }
                                                                   @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException { }
                                                                   @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException { }
                                                                   @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; } })
                    .build();

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
            Request.Builder okRequest = new Request.Builder().method(request.method(),
                                                                     RequestBody.create(request.body(),
                                                                                        MediaType.parse("application/json")))
                                                             .url(endpoint.uri.resolve(request.path()).toString());
            request.headers().forEach((name, value) -> okRequest.header(name, value.get()));
            endpoint.client.newCall(okRequest.build()).enqueue(new Callback() {
                @Override public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    vessel.completeExceptionally(e);
                }
                @Override public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                    vessel.complete(HttpResponse.of(response.code(), response.body().bytes()));
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
                //endpoint.client.
            }
            catch (Throwable t) {
                if (thrown == null) thrown = t;
                else thrown.addSuppressed(t);
            }
        if (thrown != null) throw new RuntimeException(thrown);
    }


    private static class Endpoint {

        private final OkHttpClient client;
        private final AtomicInteger inflight = new AtomicInteger(0);
        private final URI uri;

        private Endpoint(OkHttpClient client, URI uri) {
            this.client = client;
            this.uri = uri;
        }
    }

}
