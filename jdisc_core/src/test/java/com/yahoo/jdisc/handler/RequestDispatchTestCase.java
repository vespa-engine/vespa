// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class RequestDispatchTestCase {

    @Test
    void requireThatRequestCanBeDispatched() throws Exception {
        final TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        final List<ByteBuffer> writtenContent = Arrays.asList(ByteBuffer.allocate(6), ByteBuffer.allocate(9));
        ReadableContentChannel receivedContent = new ReadableContentChannel();
        ContainerBuilder builder = driver.newContainerBuilder();
        Response response = new Response(Response.Status.OK);
        builder.serverBindings().bind("http://localhost/", new MyRequestHandler(receivedContent, response));
        driver.activateContainer(builder);
        RequestDispatch dispatch = new RequestDispatch() {

            @Override
            protected Request newRequest() {
                return new Request(driver, URI.create("http://localhost/"));
            }

            @Override
            protected Iterable<ByteBuffer> requestContent() {
                return writtenContent;
            }
        };
        dispatch.dispatch();
        assertFalse(dispatch.isDone());
        assertSame(writtenContent.get(0), receivedContent.read());
        assertFalse(dispatch.isDone());
        assertSame(writtenContent.get(1), receivedContent.read());
        assertFalse(dispatch.isDone());
        assertNull(receivedContent.read());
        assertTrue(dispatch.isDone());
        assertSame(response, dispatch.get(600, TimeUnit.SECONDS));
        assertTrue(driver.close());
    }

    @Test
    void requireThatStreamCanBeConnected() throws IOException {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        ReadableContentChannel content = new ReadableContentChannel();
        MyRequestHandler requestHandler = new MyRequestHandler(content, new Response(Response.Status.OK));
        builder.serverBindings().bind("http://localhost/", requestHandler);
        driver.activateContainer(builder);

        OutputStream out = new FastContentOutputStream(driver.newRequestDispatch("http://localhost/", new FutureResponse()).connect());
        out.write(6);
        out.write(9);
        out.close();

        InputStream in = content.toStream();
        assertEquals(6, in.read());
        assertEquals(9, in.read());
        assertEquals(-1, in.read());

        assertTrue(driver.close());
    }

    @Test
    void requireThatCancelIsUnsupported() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        RequestDispatch dispatch = driver.newRequestDispatch("http://localhost/", new FutureResponse());
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
        assertTrue(driver.close());
    }

    @Test
    void requireThatDispatchHandlesConnectException() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.serverBindings().bind("http://localhost/", new AbstractRequestHandler() {

            @Override
            public ContentChannel handleRequest(Request request, ResponseHandler handler) {
                throw new RuntimeException();
            }
        });
        driver.activateContainer(builder);
        try {
            driver.newRequestDispatch("http://localhost/", new FutureResponse()).dispatch();
            fail();
        } catch (RuntimeException e) {

        }
        assertTrue(driver.close());
    }

    @Test
    void requireThatDispatchHandlesWriteException() {
        final TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        Response response = new Response(Response.Status.OK);
        builder.serverBindings().bind("http://localhost/", new MyRequestHandler(new ContentChannel() {

            @Override
            public void write(ByteBuffer buf, CompletionHandler handler) {
                throw new RuntimeException();
            }

            @Override
            public void close(CompletionHandler handler) {
                handler.completed();
            }
        }, response));
        driver.activateContainer(builder);
        try {
            new RequestDispatch() {

                @Override
                protected Request newRequest() {
                    return new Request(driver, URI.create("http://localhost/"));
                }

                @Override
                protected Iterable<ByteBuffer> requestContent() {
                    return Arrays.asList(ByteBuffer.allocate(69));
                }
            }.dispatch();
            fail();
        } catch (RuntimeException e) {

        }
        assertTrue(driver.close());
    }

    @Test
    void requireThatDispatchHandlesCloseException() {
        final TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        Response response = new Response(Response.Status.OK);
        builder.serverBindings().bind("http://localhost/", new MyRequestHandler(new ContentChannel() {

            @Override
            public void write(ByteBuffer buf, CompletionHandler handler) {
                handler.completed();
            }

            @Override
            public void close(CompletionHandler handler) {
                throw new RuntimeException();
            }
        }, response));
        driver.activateContainer(builder);
        try {
            new RequestDispatch() {

                @Override
                protected Request newRequest() {
                    return new Request(driver, URI.create("http://localhost/"));
                }

                @Override
                protected Iterable<ByteBuffer> requestContent() {
                    return Arrays.asList(ByteBuffer.allocate(69));
                }
            }.dispatch();
            fail();
        } catch (RuntimeException e) {

        }
        assertTrue(driver.close());
    }

    @Test
    void requireThatDispatchCanBeListenedTo() throws InterruptedException {
        final TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        ReadableContentChannel requestContent = new ReadableContentChannel();
        MyRequestHandler requestHandler = new MyRequestHandler(requestContent, null);
        builder.serverBindings().bind("http://localhost/", requestHandler);
        driver.activateContainer(builder);
        RunnableLatch listener = new RunnableLatch();
        new RequestDispatch() {

            @Override
            protected Request newRequest() {
                return new Request(driver, URI.create("http://localhost/"));
            }
        }.dispatch().whenComplete((__, ___) -> listener.run());
        assertFalse(listener.await(100, TimeUnit.MILLISECONDS));
        ContentChannel responseContent = ResponseDispatch.newInstance(Response.Status.OK)
                .connect(requestHandler.responseHandler);
        assertFalse(listener.await(100, TimeUnit.MILLISECONDS));
        assertNull(requestContent.read());
        assertTrue(listener.await(600, TimeUnit.SECONDS));
        responseContent.close(null);
        assertTrue(driver.close());
    }

    private static class MyRequestHandler extends AbstractRequestHandler {

        final ContentChannel content;
        final Response response;
        ResponseHandler responseHandler;

        MyRequestHandler(ContentChannel content, Response response) {
            this.content = content;
            this.response = response;
        }

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            if (response != null) {
                ResponseDispatch.newInstance(response).dispatch(handler);
            } else {
                responseHandler = handler;
            }
            return content;
        }
    }
}
