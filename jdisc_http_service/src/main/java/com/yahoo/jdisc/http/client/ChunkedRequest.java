// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.RequestBuilder;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpRequest;

import java.io.IOException;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
final class ChunkedRequest {

    private ChunkedRequest() {
        // hide
    }

    public static ContentChannel executeRequest(AsyncHttpClient ningClient, Request request, HttpRequest.Method method,
                                                ResponseHandler handler, Metric metric, Metric.Context ctx)
    {
        RequestBuilder builder = RequestBuilderFactory.newInstance(request, method);
        ChunkedRequestContent content = new ChunkedRequestContent(request);
        builder.setBody(content);
        ningClient.executeRequest(builder.build(), new AsyncResponseHandler(request, handler, metric, ctx));
        return content;
    }
}
