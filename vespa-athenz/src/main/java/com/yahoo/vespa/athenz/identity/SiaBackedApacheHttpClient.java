// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identity;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * A thread-safe http client wrapper on Apache's {@link HttpClient} that handles TLS client authentication using {@link ServiceIdentityProvider}.
 *
 * @author bjorncs
 */
public class SiaBackedApacheHttpClient extends CloseableHttpClient {

    /**
     * A factory that returns a new instance of {@link CloseableHttpClient}.
     * The implementor is responsible for configuring the {@link SSLContext}, e.g. using {@link HttpClientBuilder#setSSLContext(SSLContext)}.
     */
    @FunctionalInterface
    public interface HttpClientFactory {
        CloseableHttpClient createHttpClient(SSLContext sslContext);
    }

    private final Object clientLock = new Object();
    private final Supplier<SSLContext> sslContextSupplier;
    private final HttpClientFactory httpClientFactory;
    private HttpClientHolder client;
    private boolean closed = false;

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
        this.client = new HttpClientHolder(httpClientFactory, sslContextSupplier, clientLock);
        this.client.acquire(); // owner ref
    }

    @Override
    protected CloseableHttpResponse doExecute(HttpHost target, HttpRequest request, HttpContext context) throws IOException, ClientProtocolException {
        HttpClientHolder client = getClient();
        client.acquire(); // request ref
        try {
            CloseableHttpResponse response = client.apacheClient.execute(target, request, context);
            return new ResponseHolder(response, client);
        } finally {
            client.release(); // request ref
        }
    }

    /**
     * Thread-safe and idempotent.
     */
    @Override
    public void close() throws IOException {
        synchronized (clientLock) {
            if (closed) return;
            client.release(); // owner ref
            closed = true;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public org.apache.http.params.HttpParams getParams() {
        throw new UnsupportedOperationException();
    }

    @Override
    @SuppressWarnings("deprecation")
    public org.apache.http.conn.ClientConnectionManager getConnectionManager() {
        throw new UnsupportedOperationException();
    }

    private HttpClientHolder getClient() throws IOException {
        synchronized (clientLock) {
            if (closed) throw new IllegalStateException("Client has been closed!");
            if (sslContextSupplier.get() != client.sslContext) {
                client.release(); // owner ref
                client = new HttpClientHolder(httpClientFactory, sslContextSupplier, clientLock);
                client.acquire(); // owner ref
            }
            return client;
        }
    }

    private static class HttpClientHolder {
        final Object clientLock;
        final CloseableHttpClient apacheClient;
        final SSLContext sslContext;
        int referenceCount = 0;
        boolean closed = false;

        HttpClientHolder(HttpClientFactory httpClientFactory, Supplier<SSLContext> sslContextSupplier, Object clientLock) {
            SSLContext sslContext = sslContextSupplier.get();
            this.apacheClient = httpClientFactory.createHttpClient(sslContext);
            this.sslContext = sslContext;
            this.clientLock = clientLock;
        }

        void acquire() {
            synchronized (clientLock) {
                if (closed) throw new IllegalStateException("Client already closed!");
                ++referenceCount;
            }
        }

        void release() throws IOException {
            synchronized (clientLock) {
                if (closed) throw new IllegalStateException("Client already closed!");
                --referenceCount;
                if (referenceCount == 0) {
                    apacheClient.close();
                    closed = true;
                }
            }
        }
    }

    private static class ResponseHolder implements CloseableHttpResponse {
        final CloseableHttpResponse response;
        final HttpClientHolder clientHolder;

        ResponseHolder(CloseableHttpResponse response, HttpClientHolder clientHolder) {
            clientHolder.acquire(); // response ref
            this.response = response;
            this.clientHolder = clientHolder;
        }

        @Override
        public void close() throws IOException {
            response.close(); // response ref
            clientHolder.release();
        }

        // Proxy methods
        @Override public StatusLine getStatusLine() { return response.getStatusLine(); }
        @Override public void setStatusLine(StatusLine statusline) { response.setStatusLine(statusline); }
        @Override public void setStatusLine(ProtocolVersion ver, int code) { response.setStatusLine(ver, code); }
        @Override public void setStatusLine(ProtocolVersion ver, int code, String reason) { response.setStatusLine(ver, code, reason); }
        @Override public void setStatusCode(int code) throws IllegalStateException { response.setStatusCode(code);}
        @Override public void setReasonPhrase(String reason) throws IllegalStateException { response.setReasonPhrase(reason); }
        @Override public HttpEntity getEntity() { return response.getEntity(); }
        @Override public void setEntity(HttpEntity entity) { response.setEntity(entity); }
        @Override public Locale getLocale() { return response.getLocale(); }
        @Override public void setLocale(Locale loc) { response.setLocale(loc); }
        @Override public ProtocolVersion getProtocolVersion() { return response.getProtocolVersion(); }
        @Override public boolean containsHeader(String name) { return response.containsHeader(name); }
        @Override public Header[] getHeaders(String name) { return response.getHeaders(name); }
        @Override public Header getFirstHeader(String name) { return response.getFirstHeader(name); }
        @Override public Header getLastHeader(String name) { return response.getLastHeader(name); }
        @Override public Header[] getAllHeaders() { return response.getAllHeaders(); }
        @Override public void addHeader(Header header) { response.addHeader(header); }
        @Override public void addHeader(String name, String value) { response.addHeader(name, value); }
        @Override public void setHeader(Header header) { response.setHeader(header); }
        @Override public void setHeader(String name, String value) { response.setHeader(name, value); }
        @Override public void setHeaders(Header[] headers) { response.setHeaders(headers); }
        @Override public void removeHeader(Header header) { response.removeHeader(header); }
        @Override public void removeHeaders(String name) { response.removeHeaders(name); }
        @Override public HeaderIterator headerIterator() { return response.headerIterator(); }
        @Override public HeaderIterator headerIterator(String name) { return response.headerIterator(name); }
        @Override @SuppressWarnings("deprecation") public org.apache.http.params.HttpParams getParams() { return response.getParams(); }
        @Override @SuppressWarnings("deprecation") public void setParams(org.apache.http.params.HttpParams params) { response.setParams(params); }
    }
}
