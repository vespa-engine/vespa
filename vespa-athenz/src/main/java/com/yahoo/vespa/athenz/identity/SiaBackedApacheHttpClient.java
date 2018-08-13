// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identity;

import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Supplier;

/**
 * A thread-safe http client wrapper on Apache's {@link HttpClient} that handles TLS client authentication using {@link ServiceIdentityProvider}.
 *
 * @author bjorncs
 */
public class SiaBackedApacheHttpClient implements AutoCloseable {

    private final Object clientLock = new Object();
    private final Supplier<SSLContext> sslContextSupplier;
    private final HttpClientFactory httpClientFactory;
    private HttpClientHolder client;

    public SiaBackedApacheHttpClient(ServiceIdentityProvider identityProvider,
                                     HttpClientFactory httpClientFactory) {
        this(identityProvider::getIdentitySslContext, httpClientFactory);
    }

    public SiaBackedApacheHttpClient(SSLContext sslContext,
                                     HttpClientFactory httpClientFactory) {
        this(() -> sslContext, httpClientFactory);
    }

    public SiaBackedApacheHttpClient(Supplier<SSLContext> sslContextSupplier,
                                     HttpClientFactory httpClientFactory) {
        this.sslContextSupplier = sslContextSupplier;
        this.httpClientFactory = httpClientFactory;
        this.client = new HttpClientHolder(httpClientFactory, sslContextSupplier);
    }

    public <T> T execute(HttpUriRequest request, ResponseHandler<T> responseHandler) {
        HttpClientHolder client = getClient();
        try {
            // Note: Apache http client will handle response/connection cleanup for this overload of execute().
            return client.apacheClient.execute(request, responseHandler);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            synchronized (clientLock) {
                client.release();
            }
        }
    }

    private HttpClientHolder getClient() {
        synchronized (clientLock) {
            if (sslContextSupplier.get() != client.sslContext) {
                client.release();
                client = new HttpClientHolder(httpClientFactory, sslContextSupplier);
            }
            return client.refer();
        }
    }

    @Override
    public void close() {
        synchronized (clientLock) {
            client.release();
        }
    }

    private static class HttpClientHolder {
        final Supplier<SSLContext> sslContextSupplier;
        final CloseableHttpClient apacheClient;
        final SSLContext sslContext;
        int referenceCount = 1; // Owner's reference implicitly counted

        HttpClientHolder(HttpClientFactory clientBuilder, Supplier<SSLContext> sslContextSupplier) {
            SSLContext sslContext = sslContextSupplier.get();
            this.sslContextSupplier = sslContextSupplier;
            this.apacheClient = clientBuilder.createHttpClient(sslContext);
            this.sslContext = sslContext;
        }

        HttpClientHolder refer() {
            if (referenceCount == 0) throw new IllegalStateException("Client already closed!");
            ++referenceCount;
            return this;
        }

        void release() {
            --referenceCount;
            if (referenceCount == 0) {
                try {
                    apacheClient.close();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }

    /**
     * A factory that returns a new instance of {@link CloseableHttpClient}. The implementor is responsible for configuring the {@link SSLContext}, e.g. using {@link HttpClientBuilder#setSSLContext(SSLContext)}.
     */
    @FunctionalInterface
    public interface HttpClientFactory {
        CloseableHttpClient createHttpClient(SSLContext sslContext);
    }
}
