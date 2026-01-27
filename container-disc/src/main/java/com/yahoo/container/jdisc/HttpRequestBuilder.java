// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.jdisc.http.HttpRequest.Method;

import java.io.InputStream;
import java.net.SocketAddress;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builder for creating a {@link HttpRequest} to be used in test context
 *
 * @author bjorncs
 */
public class HttpRequestBuilder {
    private final Method method;
    private final String path;
    private final Map<String, List<String>> queryParameters = new TreeMap<>();
    private final Map<String, String> headers = new TreeMap<>();
    private final Map<String, Object> attributes = new TreeMap<>();
    private String scheme;
    private String hostname;
    private InputStream content;
    private Principal principal;
    private SocketAddress socketAddress;
    private int port = -1;

    private HttpRequestBuilder(Method method, String path) {
        this.method = method;
        this.path = path;
    }

    public static HttpRequestBuilder create(Method method, String path) { return new HttpRequestBuilder(method, path); }

    public HttpRequestBuilder withQueryParameter(String name, String value) {
        this.queryParameters.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
        return this;
    }

    public HttpRequestBuilder withHeader(String name, String value) { this.headers.put(name, value); return this; }

    public HttpRequestBuilder withRequestContent(InputStream content) { this.content = content; return this; }

    public HttpRequestBuilder withScheme(String scheme) { this.scheme = scheme; return this; }

    public HttpRequestBuilder withHostname(String hostname) { this.hostname = hostname; return this; }

    public HttpRequestBuilder withPrincipal(Principal p) { principal = p; return this; }

    public HttpRequestBuilder withRemoteAddress(SocketAddress sa) { socketAddress = sa; return this; }

    public HttpRequestBuilder withAttribute(String name, Object value) { attributes.put(name, value); return this; }

    public HttpRequestBuilder withPort(int port) { this.port = port; return this; }

    public HttpRequest build() {
        String scheme = this.scheme != null ? this.scheme : "http";
        String hostname = this.hostname != null ? this.hostname : "localhost";
        StringBuilder uriBuilder = new StringBuilder(scheme).append("://").append(hostname);
        if (port > 0) uriBuilder.append(':').append(port);
        uriBuilder.append(path);
        if (queryParameters.size() > 0) {
            uriBuilder.append('?');
            queryParameters.forEach((name, values) -> {
                for (String value : values) {
                    uriBuilder.append(name).append('=').append(value).append('&');
                }
            });
            int lastIndex = uriBuilder.length() - 1;
            if (uriBuilder.charAt(lastIndex) == '&') {
                uriBuilder.setLength(lastIndex);
            }
        }
        HttpRequest request;
        if (content != null) {
            request = HttpRequest.createTestRequest(uriBuilder.toString(), method, content);
        } else {
            request = HttpRequest.createTestRequest(uriBuilder.toString(), method);
        }
        headers.forEach((name, value) -> request.getJDiscRequest().headers().put(name, value));
        if (principal != null) request.getJDiscRequest().setUserPrincipal(principal);
        if (socketAddress != null) request.getJDiscRequest().setRemoteAddress(socketAddress);
        request.getJDiscRequest().context().putAll(attributes);
        return request;
    }
}
