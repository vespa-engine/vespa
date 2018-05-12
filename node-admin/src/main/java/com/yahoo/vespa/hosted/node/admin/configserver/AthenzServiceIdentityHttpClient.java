// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver;

import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.identity.SiaIdentityProvider;
import com.yahoo.vespa.athenz.tls.AthenzIdentityVerifier;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultHttpClientConnectionOperator;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Http Client that verifies server hostname based of their expected {@link AthenzIdentity}
 * and that uses {@link SSLContext} provided by {@link SiaIdentityProvider}.
 *
 * This client also supports live updates of credentials by implementing
 * {@link ServiceIdentityProvider.Listener}.
 *
 * @author freva
 */
class AthenzServiceIdentityHttpClient implements AutoCloseable, ServiceIdentityProvider.Listener {
    private final Map<String, ConnectionSocketFactory> socketFactoryRegistry = new ConcurrentHashMap<>();
    private final AthenzIdentityVerifier identityVerifier;
    private final CloseableHttpClient httpClient;

    AthenzServiceIdentityHttpClient(Set<AthenzIdentity> allowedIdentities) {
        DefaultHttpClientConnectionOperator httpClientConnectionOperator =
                new DefaultHttpClientConnectionOperator(socketFactoryRegistry::get, null, null);
        PoolingHttpClientConnectionManager cm =
                new PoolingHttpClientConnectionManager(httpClientConnectionOperator, null, -1, TimeUnit.MILLISECONDS);
        cm.setMaxTotal(200); // Increase max total connections to 200, which should be enough

        // Have experienced hang in socket read, which may have been because of
        // system defaults, therefore set explicit timeouts. Set arbitrarily to
        // 15s > 10s used by Orchestrator lock timeout.
        int timeoutMs = 15_000;
        RequestConfig requestBuilder = RequestConfig.custom()
                .setConnectTimeout(timeoutMs) // establishment of connection
                .setConnectionRequestTimeout(timeoutMs) // connection from connection manager
                .setSocketTimeout(timeoutMs) // waiting for data
                .build();

        this.socketFactoryRegistry.put("http", PlainConnectionSocketFactory.getSocketFactory());
        this.identityVerifier = new AthenzIdentityVerifier(allowedIdentities);
        this.httpClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestBuilder)
                .disableAutomaticRetries()
                .setUserAgent("node-admin")
                .setConnectionManager(cm).build();
    }

    AthenzServiceIdentityHttpClient(SiaIdentityProvider identityProvider, Set<AthenzIdentity> allowedIdentities) {
        this(allowedIdentities);
        onCredentialsUpdate(identityProvider.getIdentitySslContext(), null);
    }

    public CloseableHttpResponse execute(HttpUriRequest request) throws IOException {
        return httpClient.execute(request);
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }

    @Override
    public void onCredentialsUpdate(SSLContext sslContext, AthenzService identity) {
        SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext, identityVerifier);
        setSslConnectionSocketFactory(sslConnectionSocketFactory);
    }

    private void setSslConnectionSocketFactory(SSLConnectionSocketFactory sslConnectionSocketFactory) {
        socketFactoryRegistry.put("https", sslConnectionSocketFactory);
    }
}
