// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.websocket.WebSocket;
import com.ning.http.client.websocket.WebSocketUpgradeHandler;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * A content channel for interfacing with the web socket client. It accumulates the request data
 * before dispatching it to the remote endpoint.
 *
 * @author <a href="mailto:vikasp@yahoo-inc.com">Vikas Panwar</a>
 */
class WebSocketContent implements ContentChannel {

    private final AsyncHttpClient client;
    private final Request request;
    private final WebSocketUpgradeHandler handler;
    private final Object wsLock = new Object();
    private WebSocket websocket;

    WebSocketContent(AsyncHttpClient client, Request request, WebSocketUpgradeHandler handler) {
        this.client = client;
        this.request = request;
        this.handler = handler;
        this.websocket = null;
    }

    @Override
    public void write(ByteBuffer buf, CompletionHandler handler) {
        Objects.requireNonNull(buf, "buf");

        try {
            executeRequest(buf.array());
            if (handler != null) {
                handler.completed();
            }
        } catch (Exception e) {
            if (websocket != null) {
                websocket.close();
            }

            throw new RuntimeException(e);
        }
    }

    @Override
    public void close(CompletionHandler handler) {
        if (websocket != null) {
            websocket.close();
        }

        if (handler != null) {
            handler.completed();
        }
    }

    private void executeRequest(final byte[] content) throws Exception {
        RequestBuilder builder = new RequestBuilder();
        builder.setUrl(request.getUri().toString());

        synchronized (wsLock) {
            if (websocket == null) {
                websocket = client.executeRequest(builder.build(), handler).get();
            }
        }

        if (websocket.isOpen()) {
            websocket.sendMessage(content);
        }
    }
}
