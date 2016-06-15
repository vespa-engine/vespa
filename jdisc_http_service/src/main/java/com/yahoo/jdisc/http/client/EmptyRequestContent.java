// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.RequestBuilder;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.core.HeaderFieldsUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
class EmptyRequestContent implements ContentChannel {

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AsyncHttpClient client;
    private final AsyncResponseHandler handler;
    private final Request request;
    private final HttpRequest.Method method;

    public EmptyRequestContent(AsyncHttpClient client, Request request, HttpRequest.Method method,
                               AsyncResponseHandler handler) {
        this.client = client;
        this.request = request;
        this.method = method;
        this.handler = handler;
    }

    @Override
    public void write(ByteBuffer buf, CompletionHandler handler) {
        throw new UnsupportedOperationException("Request does not support a message-body.");
    }

    @Override
    public void close(CompletionHandler handler) {
        if (closed.getAndSet(true)) {
            if (handler != null) {
                handler.completed();
            }
            return;
        }
        try {
            executeRequest();
            handler.completed();
        } catch (Exception e) {
            try {
                handler.failed(e);
            } catch (Exception f) {
                // ignore
            }
        }
    }

    private void executeRequest() throws IOException {
        RequestBuilder builder = RequestBuilderFactory.newInstance(request, method);
        HeaderFieldsUtil.copyTrailers(request, builder);
        client.executeRequest(builder.build(), handler);
    }
}
