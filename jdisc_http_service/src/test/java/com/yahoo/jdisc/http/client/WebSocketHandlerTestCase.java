// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client;

import com.ning.http.client.websocket.WebSocket;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.FutureResponse;
import com.yahoo.jdisc.handler.ReadableContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpResponse;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import java.net.ConnectException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.fail;

/**
 * @author <a href="mailto:vikasp@yahoo-inc.com">Vikas Panwar</a>
 */
public class WebSocketHandlerTestCase {

    @Test(enabled = false)
    public void requireThatOnOpenDoesNothing() {
        ResponseHandler responseHandler = Mockito.mock(ResponseHandler.class);
        newSocketHandler(responseHandler).onOpen(Mockito.mock(WebSocket.class));
        Mockito.verifyZeroInteractions(responseHandler);
    }

    @Test(enabled = false)
    public void requireThatOnFragmentDoesNothing() {
        ResponseHandler responseHandler = Mockito.mock(ResponseHandler.class);
        newSocketHandler(responseHandler).onFragment(new byte[] { 6, 9 }, false);
        Mockito.verifyZeroInteractions(responseHandler);
    }

    @Test(enabled = false)
    public void requireThatOnLastFragmentDoesNothing() {
        ResponseHandler responseHandler = Mockito.mock(ResponseHandler.class);
        newSocketHandler(responseHandler).onFragment(new byte[] { 6, 9 }, true);
        Mockito.verifyZeroInteractions(responseHandler);
    }

    @Test(enabled = false)
    public void requireThatResponseIsDispatchedOnFirstMessage() throws Exception {
        ReadableContentChannel content = new ReadableContentChannel();
        FutureResponse responseHandler = new FutureResponse(content);
        try {
            responseHandler.get(100, TimeUnit.MILLISECONDS);
            fail();
        } catch (TimeoutException e) {

        }
        newSocketHandler(responseHandler).onMessage(new byte[] { 6, 9 });
        Response response = responseHandler.get(60, TimeUnit.SECONDS);
        assertTrue(response instanceof HttpResponse);
        assertEquals(Response.Status.OK, response.getStatus());
    }

    @Test(enabled = false)
    public void requireThatResponseBytesAreWritten() {
        ReadableContentChannel content = new ReadableContentChannel();
        newSocketHandler(content).onMessage(new byte[] { 6, 9 });
        ByteBuffer buf = content.read();
        assertEquals(2, buf.remaining());
        assertEquals(6, buf.get());
        assertEquals(9, buf.get());
    }

    @Test(enabled = false)
    public void requireThatEmptyResponsesCanBeSent() throws Exception {
        ReadableContentChannel content = new ReadableContentChannel();
        FutureResponse responseHandler = new FutureResponse(content);
        newSocketHandler(responseHandler).onClose(Mockito.mock(WebSocket.class));
        assertResponse(responseHandler, Response.Status.OK);
        assertNull(content.read());
    }

    @Test(enabled = false)
    public void requireThatEarlyErrorRespondsWithError() throws Exception {
        assertErrorResponse(new ConnectException(), Response.Status.SERVICE_UNAVAILABLE);
        assertErrorResponse(new TimeoutException(), Response.Status.REQUEST_TIMEOUT);
        assertErrorResponse(new Throwable(), Response.Status.BAD_REQUEST);
    }

    @Test(enabled = false)
    public void requireThatWriteCompletionFailureClosesResponseContent() {
        CloseableContentChannel content = new CloseableContentChannel();
        FutureResponse responseHandler = new FutureResponse(content);
        WebSocketHandler socketHandler = newSocketHandler(responseHandler);
        socketHandler.onMessage(new byte[] { 6, 9 });
        assertFalse(content.closed);
        content.handler.failed(new Throwable());
        assertTrue(content.closed);
    }

    private static void assertErrorResponse(Throwable t, int expectedStatus) throws Exception {
        ReadableContentChannel content = new ReadableContentChannel();
        FutureResponse responseHandler = new FutureResponse(content);
        newSocketHandler(responseHandler).onError(t);
        assertResponse(responseHandler, expectedStatus);
        assertNull(content.read());
    }

    private static void assertResponse(FutureResponse responseHandler, int expectedStatus) throws Exception {
        Response response = responseHandler.get(60, TimeUnit.SECONDS);
        assertTrue(response instanceof HttpResponse);
        assertEquals(expectedStatus, response.getStatus());
    }

    private static WebSocketHandler newSocketHandler(ResponseHandler responseHandler) {
        return new WebSocketHandler(Mockito.mock(Request.class), responseHandler, Mockito.mock(Metric.class),
                                    Mockito.mock(Metric.Context.class));
    }

    private static WebSocketHandler newSocketHandler(ContentChannel responseContent) {
        ResponseHandler responseHandler = Mockito.mock(ResponseHandler.class);
        Mockito.when(responseHandler.handleResponse(Mockito.any(Response.class))).thenReturn(responseContent);
        return new WebSocketHandler(Mockito.mock(Request.class), responseHandler, Mockito.mock(Metric.class),
                                    Mockito.mock(Metric.Context.class));
    }

    private static class CloseableContentChannel implements ContentChannel {

        CompletionHandler handler;
        boolean closed = false;

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            this.handler = handler;
        }

        @Override
        public void close(CompletionHandler handler) {
            closed = true;
        }
    }
}
