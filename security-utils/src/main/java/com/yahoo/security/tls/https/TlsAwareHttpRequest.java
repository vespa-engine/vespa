// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls.https;

import com.yahoo.security.tls.MixedMode;
import com.yahoo.security.tls.TransportSecurityUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

/**
 * A {@link HttpRequest} where the scheme is either http or https based on the global Vespa TLS configuration.
 *
 * @author bjorncs
 */
class TlsAwareHttpRequest extends HttpRequest {

    private final URI rewrittenUri;
    private final HttpRequest wrappedRequest;
    private final HttpHeaders rewrittenHeaders;

    TlsAwareHttpRequest(HttpRequest wrappedRequest, String userAgent) {
        this.wrappedRequest = wrappedRequest;
        this.rewrittenUri = rewriteUri(wrappedRequest.uri());
        this.rewrittenHeaders = rewriteHeaders(wrappedRequest, userAgent);
    }

    @Override
    public Optional<BodyPublisher> bodyPublisher() {
        return wrappedRequest.bodyPublisher();
    }

    @Override
    public String method() {
        return wrappedRequest.method();
    }

    @Override
    public Optional<Duration> timeout() {
        return wrappedRequest.timeout();
    }

    @Override
    public boolean expectContinue() {
        return wrappedRequest.expectContinue();
    }

    @Override
    public URI uri() {
        return rewrittenUri;
    }

    @Override
    public Optional<HttpClient.Version> version() {
        return wrappedRequest.version();
    }

    @Override
    public HttpHeaders headers() {
        return rewrittenHeaders;
    }

    private static URI rewriteUri(URI uri) {
        if (!uri.getScheme().equals("http")) {
            return uri;
        }
        String rewrittenScheme =
                TransportSecurityUtils.getConfigFile().isPresent() && TransportSecurityUtils.getInsecureMixedMode() != MixedMode.PLAINTEXT_CLIENT_MIXED_SERVER ?
                        "https" :
                        "http";
        int port = uri.getPort();
        int rewrittenPort = port != -1 ? port : (rewrittenScheme.equals("http") ? 80 : 443);
        try {
            return new URI(rewrittenScheme, uri.getUserInfo(), uri.getHost(), rewrittenPort, uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static HttpHeaders rewriteHeaders(HttpRequest request, String userAgent) {
        HttpHeaders headers = request.headers();
        if (headers.firstValue("User-Agent").isPresent()) {
            return headers;
        }
        HashMap<String, List<String>> rewrittenHeaders = new HashMap<>(headers.map());
        rewrittenHeaders.put("User-Agent", List.of(userAgent));
        return HttpHeaders.of(rewrittenHeaders, (ignored1, ignored2) -> true);
    }

    @Override
    public String toString() {
        return "TlsAwareHttpRequest{" +
                "rewrittenUri=" + rewrittenUri +
                ", wrappedRequest=" + wrappedRequest +
                '}';
    }
}
