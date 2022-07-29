// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.test.NonWorkingRequest;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Simon Thoresen Hult
 */
public class AbstractRequestHandlerTestCase {

    private static final int NUM_REQUESTS = 666;

    @Test
    void requireThatHandleTimeoutIsImplemented() throws Exception {
        FutureResponse handler = new FutureResponse();
        new AbstractRequestHandler() {

            @Override
            public ContentChannel handleRequest(Request request, ResponseHandler handler) {
                return null;
            }
        }.handleTimeout(NonWorkingRequest.newInstance("http://localhost/"), handler);
        Response response = handler.get(600, TimeUnit.SECONDS);
        assertNotNull(response);
        assertEquals(Response.Status.GATEWAY_TIMEOUT, response.getStatus());
    }

    @Test
    void requireThatHelloWorldWorks() throws InterruptedException {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.serverBindings().bind("http://localhost/", new HelloWorldHandler());
        driver.activateContainer(builder);

        for (int i = 0; i < NUM_REQUESTS; ++i) {
            MyResponseHandler responseHandler = new MyResponseHandler();
            driver.newRequestDispatch("http://localhost/", responseHandler).dispatch();

            ByteBuffer buf = responseHandler.content.read();
            assertNotNull(buf);
            assertEquals("Hello World!", new String(buf.array(), buf.arrayOffset() + buf.position(), buf.remaining(), UTF_8));
            assertNull(responseHandler.content.read());
        }
        assertTrue(driver.close());
    }

    @Test
    void requireThatEchoWorks() throws InterruptedException {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.serverBindings().bind("http://localhost/", new EchoHandler());
        driver.activateContainer(builder);

        for (int i = 0; i < NUM_REQUESTS; ++i) {
            MyResponseHandler responseHandler = new MyResponseHandler();
            RequestDispatch dispatch = driver.newRequestDispatch("http://localhost/", responseHandler);
            FastContentWriter requestContent = dispatch.connectFastWriter();
            ByteBuffer buf = ByteBuffer.allocate(69);
            requestContent.write(buf);
            requestContent.close();

            assertSame(buf, responseHandler.content.read());
            assertNull(responseHandler.content.read());
        }
        assertTrue(driver.close());
    }

    @Test
    void requireThatForwardWorks() throws InterruptedException {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.serverBindings().bind("http://localhost/", new ForwardHandler());
        builder.clientBindings().bind("http://remotehost/", new EchoHandler());
        driver.activateContainer(builder);

        for (int i = 0; i < NUM_REQUESTS; ++i) {
            MyResponseHandler responseHandler = new MyResponseHandler();
            RequestDispatch dispatch = driver.newRequestDispatch("http://localhost/", responseHandler);
            FastContentWriter requestContent = dispatch.connectFastWriter();
            ByteBuffer buf = ByteBuffer.allocate(69);
            requestContent.write(buf);
            requestContent.close();

            assertSame(buf, responseHandler.content.read());
            assertNull(responseHandler.content.read());
        }
        assertTrue(driver.close());
    }

    private static class HelloWorldHandler extends AbstractRequestHandler {

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            FastContentWriter writer = ResponseDispatch.newInstance(Response.Status.OK).connectFastWriter(handler);
            try {
                writer.write("Hello World!");
            } finally {
                writer.close();
            }
            return null;
        }
    }

    private static class EchoHandler extends AbstractRequestHandler {

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            return new WritingContentChannel(new FastContentWriter(ResponseDispatch.newInstance(Response.Status.OK).connect(handler)));
        }
    }

    private static class ForwardHandler extends AbstractRequestHandler {

        @Override
        public ContentChannel handleRequest(final Request request, final ResponseHandler handler) {
            return new WritingContentChannel(new FastContentWriter(new RequestDispatch() {

                @Override
                public Request newRequest() {
                    return new Request(request, URI.create("http://remotehost/"));
                }

                @Override
                public ContentChannel handleResponse(Response response) {
                    return handler.handleResponse(response);
                }
            }.connect()));
        }
    }

    private static class WritingContentChannel implements ContentChannel {

        final FastContentWriter writer;

        WritingContentChannel(FastContentWriter writer) {
            this.writer = writer;
        }

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            try {
                writer.write(buf);
                handler.completed();
            } catch (Exception e) {
                handler.failed(e);
            }
        }

        @Override
        public void close(CompletionHandler handler) {
            try {
                writer.close();
                handler.completed();
            } catch (Exception e) {
                handler.failed(e);
            }
        }
    }

    private static class MyResponseHandler implements ResponseHandler {

        final ReadableContentChannel content = new ReadableContentChannel();

        @Override
        public ContentChannel handleResponse(Response response) {
            return content;
        }
    }
}
