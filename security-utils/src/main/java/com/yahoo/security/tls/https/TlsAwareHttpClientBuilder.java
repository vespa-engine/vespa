// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls.https;

import com.yahoo.security.tls.TlsContext;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * A client builder for {@link HttpClient} which uses {@link TlsContext} for TLS configuration.
 * Intended for internal Vespa communication only.
 *
 * @author bjorncs
 */
public class TlsAwareHttpClientBuilder implements HttpClient.Builder {

    private final HttpClient.Builder wrappedBuilder;
    private final String userAgent;

    public TlsAwareHttpClientBuilder(TlsContext tlsContext, String userAgent) {
        this.wrappedBuilder = HttpClient.newBuilder()
                .sslContext(tlsContext.context())
                .sslParameters(tlsContext.parameters());
        this.userAgent = userAgent;
    }

    @Override
    public HttpClient.Builder cookieHandler(CookieHandler cookieHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpClient.Builder connectTimeout(Duration duration) {
        wrappedBuilder.connectTimeout(duration);
        return this;
    }

    @Override
    public HttpClient.Builder sslContext(SSLContext sslContext) {
        throw new UnsupportedOperationException("SSLContext is given from tls context");
    }

    @Override
    public HttpClient.Builder sslParameters(SSLParameters sslParameters) {
        throw new UnsupportedOperationException("SSLParameters is given from tls context");
    }

    @Override
    public HttpClient.Builder executor(Executor executor) {
        wrappedBuilder.executor(executor);
        return this;
    }

    @Override
    public HttpClient.Builder followRedirects(HttpClient.Redirect policy) {
        wrappedBuilder.followRedirects(policy);
        return this;
    }

    @Override
    public HttpClient.Builder version(HttpClient.Version version) {
        wrappedBuilder.version(version);
        return this;
    }

    @Override
    public HttpClient.Builder priority(int priority) {
        wrappedBuilder.priority(priority);
        return this;
    }

    @Override
    public HttpClient.Builder proxy(ProxySelector proxySelector) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpClient.Builder authenticator(Authenticator authenticator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpClient build() {
        // TODO Stop wrapping the client once TLS is mandatory
        return new TlsAwareHttpClient(wrappedBuilder.build(), userAgent);
    }
}
