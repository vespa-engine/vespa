// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.yahoo.jdisc.Response;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class ResponseDispatchTestCase {

    @Test
    void requireThatFactoryMethodsWork() throws Exception {
        {
            FutureResponse handler = new FutureResponse();
            ResponseDispatch.newInstance(69).dispatch(handler);
            Response response = handler.get(600, TimeUnit.SECONDS);
            assertNotNull(response);
            assertEquals(69, response.getStatus());
        }
        {
            FutureResponse handler = new FutureResponse();
            Response sentResponse = new Response(69);
            ResponseDispatch.newInstance(sentResponse).dispatch(handler);
            Response receivedResponse = handler.get(600, TimeUnit.SECONDS);
            assertSame(sentResponse, receivedResponse);
        }
        {
            ReadableContentChannel content = new ReadableContentChannel();
            FutureResponse handler = new FutureResponse(content);
            ByteBuffer buf = ByteBuffer.allocate(69);
            ResponseDispatch.newInstance(69, Arrays.asList(buf)).dispatch(handler);
            Response response = handler.get(600, TimeUnit.SECONDS);
            assertNotNull(response);
            assertEquals(69, response.getStatus());
            assertSame(buf, content.read());
            assertNull(content.read());
        }
        {
            ReadableContentChannel content = new ReadableContentChannel();
            FutureResponse handler = new FutureResponse(content);
            ByteBuffer buf = ByteBuffer.allocate(69);
            ResponseDispatch.newInstance(69, Arrays.asList(buf)).dispatch(handler);
            Response response = handler.get(600, TimeUnit.SECONDS);
            assertNotNull(response);
            assertEquals(69, response.getStatus());
            assertSame(buf, content.read());
            assertNull(content.read());
        }
        {
            ReadableContentChannel content = new ReadableContentChannel();
            FutureResponse handler = new FutureResponse(content);
            ByteBuffer buf = ByteBuffer.allocate(69);
            Response sentResponse = new Response(69);
            ResponseDispatch.newInstance(sentResponse, Arrays.asList(buf)).dispatch(handler);
            Response receivedResponse = handler.get(600, TimeUnit.SECONDS);
            assertSame(sentResponse, receivedResponse);
            assertSame(buf, content.read());
            assertNull(content.read());
        }
    }

    @Test
    void requireThatResponseCanBeDispatched() throws Exception {
        final Response response = new Response(Response.Status.OK);
        final List<ByteBuffer> writtenContent = Arrays.asList(ByteBuffer.allocate(6), ByteBuffer.allocate(9));
        ResponseDispatch dispatch = new ResponseDispatch() {

            @Override
            protected Response newResponse() {
                return response;
            }

            @Override
            protected Iterable<ByteBuffer> responseContent() {
                return writtenContent;
            }
        };
        ReadableContentChannel receivedContent = new ReadableContentChannel();
        MyResponseHandler responseHandler = new MyResponseHandler(receivedContent);
        dispatch.dispatch(responseHandler);
        assertFalse(dispatch.isDone());
        assertSame(response, responseHandler.response);
        assertSame(writtenContent.get(0), receivedContent.read());
        assertFalse(dispatch.isDone());
        assertSame(writtenContent.get(1), receivedContent.read());
        assertFalse(dispatch.isDone());
        assertNull(receivedContent.read());
        assertTrue(dispatch.isDone());
        assertTrue(dispatch.get(600, TimeUnit.SECONDS));
        assertTrue(dispatch.get());
    }

    @Test
    void requireThatStreamCanBeConnected() throws IOException {
        ReadableContentChannel responseContent = new ReadableContentChannel();
        OutputStream out = new FastContentOutputStream(new ResponseDispatch() {

            @Override
            protected Response newResponse() {
                return new Response(Response.Status.OK);
            }
        }.connect(new MyResponseHandler(responseContent)));
        out.write(6);
        out.write(9);
        out.close();

        InputStream in = responseContent.toStream();
        assertEquals(6, in.read());
        assertEquals(9, in.read());
        assertEquals(-1, in.read());
    }

    @Test
    void requireThatCancelIsUnsupported() {
        ResponseDispatch dispatch = ResponseDispatch.newInstance(69);
        assertFalse(dispatch.isCancelled());
        try {
            dispatch.cancel(true);
            fail();
        } catch (UnsupportedOperationException e) {

        }
        assertFalse(dispatch.isCancelled());
        try {
            dispatch.cancel(false);
            fail();
        } catch (UnsupportedOperationException e) {

        }
        assertFalse(dispatch.isCancelled());
    }

    @Test
    void requireThatDispatchClosesContentIfWriteThrowsException() {
        final AtomicBoolean closed = new AtomicBoolean(false);
        try {
            ResponseDispatch.newInstance(6, ByteBuffer.allocate(9)).dispatch(
                    new MyResponseHandler(new ContentChannel() {

                        @Override
                        public void write(ByteBuffer buf, CompletionHandler handler) {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public void close(CompletionHandler handler) {
                            closed.set(true);
                        }
                    }));
            fail();
        } catch (UnsupportedOperationException e) {

        }
        assertTrue(closed.get());
    }

    @Test
    void requireThatDispatchCanBeListenedTo() throws InterruptedException {
        RunnableLatch listener = new RunnableLatch();
        ReadableContentChannel responseContent = new ReadableContentChannel();
        ResponseDispatch.newInstance(6, ByteBuffer.allocate(9))
                .dispatch(new MyResponseHandler(responseContent))
                .whenComplete((__, ___) -> listener.run());
        assertFalse(listener.await(100, TimeUnit.MILLISECONDS));
        assertNotNull(responseContent.read());
        assertFalse(listener.await(100, TimeUnit.MILLISECONDS));
        assertNull(responseContent.read());
        assertTrue(listener.await(600, TimeUnit.SECONDS));
    }

    private static class MyResponseHandler implements ResponseHandler {

        final ContentChannel content;
        Response response;

        MyResponseHandler(ContentChannel content) {
            this.content = content;
        }

        @Override
        public ContentChannel handleResponse(Response response) {
            this.response = response;
            return content;
        }
    }

}
