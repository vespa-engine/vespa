// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.jdisc.http.HttpRequest.Method;

import java.io.InputStream;
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
    private String scheme;
    private String hostname;
    private InputStream content;

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

    public HttpRequest build() {
        String scheme = this.scheme != null ? this.scheme : "http";
        String hostname = this.hostname != null ? this.hostname : "localhost";
        StringBuilder uriBuilder = new StringBuilder(scheme).append("://").append(hostname).append(path);
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
        return request;
    }
}
