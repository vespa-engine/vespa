// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client;

import com.ning.http.client.AsyncHttpClient;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertTrue;

/**
 * @author <a href="mailto:vikasp@yahoo-inc.com">Vikas Panwar</a>
 */
public class WebSocketClientRequestTestCase {

    @Test(enabled = false)
    public void testWebSocketRequestReturnsCorrectContentChannel() {
        AsyncHttpClient client = Mockito.mock(AsyncHttpClient.class);
        Request request = Mockito.mock(Request.class);
        ResponseHandler respHandler = Mockito.mock(ResponseHandler.class);
        Metric metric = Mockito.mock(Metric.class);
        Metric.Context ctx = Mockito.mock(Metric.Context.class);

        ContentChannel cc = WebSocketClientRequest.executeRequest(client, request, respHandler, metric, ctx);
        assertTrue(cc instanceof WebSocketContent);
    }
}
