// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apputil.communication.http;

import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.jdisc.HeaderFields;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.clustercontroller.utils.communication.http.HttpRequest;
import com.yahoo.vespa.clustercontroller.utils.communication.http.HttpRequestHandler;
import com.yahoo.vespa.clustercontroller.utils.communication.http.HttpResult;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Note. This class is tested through apache http instance test, using this as other endpoint.
 *
 * @author Haakon Humberset
 * @author Harald Musum
 * @author Vegard Sjonfjell
 */
public class JDiscHttpRequestHandler extends ThreadedHttpRequestHandler {

    private static final Logger log = Logger.getLogger(JDiscHttpRequestHandler.class.getName());
    private final HttpRequestHandler requestHandler;

    public JDiscHttpRequestHandler(HttpRequestHandler handler, ThreadedHttpRequestHandler.Context parentCtx) {
        super(parentCtx);
        this.requestHandler = handler;
    }

    static class EmptyCompletionHandler implements CompletionHandler {
        @Override
        public void completed() { }
        @Override
        public void failed(Throwable throwable) { }
    }

    @Override
    public com.yahoo.container.jdisc.HttpResponse handle(com.yahoo.container.jdisc.HttpRequest request) {
        final HttpRequest legacyRequest = new HttpRequest();
        final com.yahoo.jdisc.http.HttpRequest jDiscRequest = request.getJDiscRequest();

        legacyRequest.setScheme(request.getUri().getScheme());
        legacyRequest.setHost(request.getUri().getHost());
        setOperation(legacyRequest, request.getMethod());
        legacyRequest.setPort(request.getUri().getPort());
        legacyRequest.setPath(request.getUri().getPath());
        copyPostData(request, legacyRequest);
        copyRequestHeaders(legacyRequest, jDiscRequest);
        copyParameters(legacyRequest, jDiscRequest);
        legacyRequest.setTimeout(Duration.ofMinutes(60).toMillis());

        try {
            final HttpResult result = requestHandler.handleRequest(legacyRequest);
            log.fine("Got result " + result.toString(true));
            return copyResponse(result);
        } catch (Exception e) {
            log.warning("Caught exception while handling request: " + e.getMessage());
            return new com.yahoo.container.jdisc.HttpResponse(500) {
                @Override
                public void render(OutputStream outputStream) throws IOException {
                    outputStream.write(Utf8.toBytes(e.getMessage()));
                }
            };
        }
    }

    static HttpRequest setOperation(HttpRequest request, com.yahoo.jdisc.http.HttpRequest.Method method) {
        switch (method) {
            case GET: return request.setHttpOperation(HttpRequest.HttpOp.GET);
            case POST: return request.setHttpOperation(HttpRequest.HttpOp.POST);
            case PUT: return request.setHttpOperation(HttpRequest.HttpOp.PUT);
            case DELETE: return request.setHttpOperation(HttpRequest.HttpOp.DELETE);
            default: throw new IllegalStateException("Unhandled method " + method);
        }
    }

    private com.yahoo.container.jdisc.HttpResponse copyResponse(final HttpResult result) {
        return new com.yahoo.container.jdisc.HttpResponse(result.getHttpReturnCode()) {
            @Override
            public void render(OutputStream outputStream) throws IOException {
                outputStream.write(Utf8.toBytes(result.getContent().toString()));
            }

            @Override
            public void complete(){
                copyResponseHeaders(result, getJdiscResponse());
            }
        };
    }

    private void copyPostData(com.yahoo.container.jdisc.HttpRequest request, HttpRequest legacyRequest) {
        try {
            legacyRequest.setPostContent(new String(request.getData().readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void copyParameters(HttpRequest legacyRequest, com.yahoo.jdisc.http.HttpRequest jDiscRequest) {
        for (String key : jDiscRequest.parameters().keySet()) {
            for (String value : jDiscRequest.parameters().get(key)) {
                legacyRequest.addUrlOption(key, value);
            }
        }
    }

    private static void copyRequestHeaders(HttpRequest legacyRequest, com.yahoo.jdisc.http.HttpRequest jDiscRequest) {
        for (String key : jDiscRequest.headers().keySet()) {
            for (String value : jDiscRequest.headers().get(key)) {
                legacyRequest.addHttpHeader(key, value);
            }
        }
    }

    private static HeaderFields copyResponseHeaders(HttpResult result, Response response) {
        HeaderFields headers = new HeaderFields();
        for (HttpRequest.KeyValuePair keyValuePair : result.getHeaders()) {
            response.headers().put((keyValuePair.getKey()), keyValuePair.getValue());
        }
        return headers;
    }

}
