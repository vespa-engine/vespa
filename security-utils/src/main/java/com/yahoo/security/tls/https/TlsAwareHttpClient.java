// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls.https;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * A {@link HttpClient} that uses either http or https based on the global Vespa TLS configuration.
 *
 * @author bjorncs
 */
class TlsAwareHttpClient extends HttpClient {

    private final HttpClient wrappedClient;
    private final String userAgent;

    TlsAwareHttpClient(HttpClient wrappedClient, String userAgent) {
        this.wrappedClient = wrappedClient;
        this.userAgent = userAgent;
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return wrappedClient.cookieHandler();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return wrappedClient.connectTimeout();
    }

    @Override
    public Redirect followRedirects() {
        return wrappedClient.followRedirects();
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return wrappedClient.proxy();
    }

    @Override
    public SSLContext sslContext() {
        return wrappedClient.sslContext();
    }

    @Override
    public SSLParameters sslParameters() {
        return wrappedClient.sslParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return wrappedClient.authenticator();
    }

    @Override
    public Version version() {
        return wrappedClient.version();
    }

    @Override
    public Optional<Executor> executor() {
        return wrappedClient.executor();
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
        return wrappedClient.send(wrapRequest(request), responseBodyHandler);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
        return wrappedClient.sendAsync(wrapRequest(request), responseBodyHandler);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        return wrappedClient.sendAsync(wrapRequest(request), responseBodyHandler, pushPromiseHandler);
    }

    @Override
    public String toString() {
        return wrappedClient.toString();
    }

    private HttpRequest wrapRequest(HttpRequest request) {
        return new TlsAwareHttpRequest(request, userAgent);
    }
}
