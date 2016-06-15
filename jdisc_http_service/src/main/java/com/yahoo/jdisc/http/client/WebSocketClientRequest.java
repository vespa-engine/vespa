// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.websocket.WebSocketUpgradeHandler;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;

/**
 * @author <a href="mailto:vikasp@yahoo-inc.com">Vikas Panwar</a>
 */
final class WebSocketClientRequest {

    private WebSocketClientRequest() {
        // hide
    }

    public static ContentChannel executeRequest(AsyncHttpClient client, Request request,
                                                ResponseHandler responseHandler, Metric metric, Metric.Context ctx) {
        return new WebSocketContent(client, request, new WebSocketUpgradeHandler.Builder()
                .addWebSocketListener(new WebSocketHandler(request, responseHandler, metric, ctx))
                .build());
    }
}
