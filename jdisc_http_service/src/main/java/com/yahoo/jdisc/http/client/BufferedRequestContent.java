// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.RequestBuilder;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.core.HeaderFieldsUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 * @since 2.0
 */
class BufferedRequestContent implements ContentChannel {

    private final AsyncHttpClient client;
    private final AsyncResponseHandler handler;
    private final Request request;
    private final HttpRequest.Method method;
    private final List<CompletionHandler> writeCompletions = new LinkedList<>();
    private final Object contentLock = new Object();
    private ByteArrayOutputStream content = new ByteArrayOutputStream();

    public BufferedRequestContent(AsyncHttpClient client, Request request, HttpRequest.Method method,
                                  AsyncResponseHandler handler) {
        this.client = client;
        this.request = request;
        this.method = method;
        this.handler = handler;
    }

    @Override
    public void write(ByteBuffer buf, CompletionHandler writeCompletion) {
        Objects.requireNonNull(buf, "buf");
        synchronized (contentLock) {
            if (content == null) {
                throw new IllegalStateException("ContentChannel closed.");
            }
            for (int i = 0, len = buf.remaining(); i < len; ++i) {
                content.write(buf.get());
            }
            if (writeCompletion != null) {
                writeCompletions.add(writeCompletion);
            }
        }
    }

    @Override
    public void close(CompletionHandler closeCompletion) {
        byte[] content;
        synchronized (contentLock) {
            content = this.content.toByteArray();
            this.content = null;
        }
        try {
            executeRequest(content);
            for (CompletionHandler writeCompletion : writeCompletions) {
                writeCompletion.completed();
            }
            if (closeCompletion != null) {
                closeCompletion.completed();
            }
        } catch (Exception e) {
            for (CompletionHandler writeCompletion : writeCompletions) {
                tryFail(writeCompletion, e);
            }
            if (closeCompletion != null) {
                tryFail(closeCompletion, e);
            }
        }
    }

    private void tryFail(CompletionHandler handler, Throwable t) {
        try {
            handler.failed(t);
        } catch (Exception e) {
            // ignore
        }
    }

    private void executeRequest(final byte[] body) throws IOException {
        RequestBuilder builder = RequestBuilderFactory.newInstance(request, method);
        HeaderFieldsUtil.copyTrailers(request, builder);
        if (body.length > 0) {
            builder.setContentLength(body.length);
            builder.setBody(body);
        }
        client.executeRequest(builder.build(), handler);
    }
}