// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.routing.mock;

import com.yahoo.yolean.Exceptions;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @author mpolden
 */
@SuppressWarnings("deprecation") // Deprecations in third-party interface
public class HttpClientMock extends CloseableHttpClient {

    private final Map<String, CloseableHttpResponse> responses = new HashMap<>();

    public HttpClientMock setResponse(String method, String url, CloseableHttpResponse response) {
        responses.put(requestKey(method, url), response);
        return this;
    }

    @Override
    protected CloseableHttpResponse doExecute(HttpHost httpHost, HttpRequest httpRequest, HttpContext httpContext) {
        String key = requestKey(httpRequest.getRequestLine().getMethod(), httpRequest.getRequestLine().getUri());
        CloseableHttpResponse response = responses.get(key);
        if (response == null) {
            throw new IllegalArgumentException("No response defined for " + key);
        }
        return response;
    }

    @Override
    public void close() {}

    @Override
    public HttpParams getParams() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ClientConnectionManager getConnectionManager() {
        throw new UnsupportedOperationException();
    }

    private static String requestKey(String method, String url) {
        return method.toUpperCase(Locale.ENGLISH) + " " + url;
    }

    public static class JsonResponse extends BasicHttpResponse implements CloseableHttpResponse {

        public JsonResponse(Path jsonFile, int code) {
            this(Exceptions.uncheck(() -> Files.readString(jsonFile)), code);
        }

        public JsonResponse(String json, int code) {
            super(HttpVersion.HTTP_1_1, code, null);
            setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
        }

        @Override
        public void close() {}

    }

}
