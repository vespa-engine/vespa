// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class ThreadedRequestHandlerTestCase {

    @Test
    void requireThatNullExecutorThrowsException() {
        try {
            new ThreadedRequestHandler(null) {

                @Override
                public void handleRequest(Request request, BufferedContentChannel content, ResponseHandler handler) {

                }
            };
            fail();
        } catch (NullPointerException e) {

        }
    }

    @Test
    void requireThatAccessorWork() {
        MyRequestHandler requestHandler = new MyRequestHandler(newExecutor());
        requestHandler.setTimeout(1000, TimeUnit.MILLISECONDS);
        assertEquals(1000, requestHandler.getTimeout(TimeUnit.MILLISECONDS));
        assertEquals(1, requestHandler.getTimeout(TimeUnit.SECONDS));
    }

    @Test
    void requireThatHandlerSetsRequestTimeout() throws InterruptedException {
        MyRequestHandler requestHandler = new MyRequestHandler(newExecutor());
        requestHandler.setTimeout(600, TimeUnit.SECONDS);
        TestDriver driver = newTestDriver("http://localhost/", requestHandler);

        MyResponseHandler responseHandler = new MyResponseHandler();
        driver.dispatchRequest("http://localhost/", responseHandler);

        requestHandler.entryLatch.countDown();
        assertTrue(requestHandler.exitLatch.await(600, TimeUnit.SECONDS));
        assertNull(requestHandler.content.read());
        assertNotNull(requestHandler.request.getTimeout(TimeUnit.MILLISECONDS));

        assertTrue(responseHandler.latch.await(600, TimeUnit.SECONDS));
        assertNull(responseHandler.content.read());
        assertTrue(driver.close());
    }

    @Test
    void requireThatRequestAndResponseReachHandlers() throws InterruptedException {
        MyRequestHandler requestHandler = new MyRequestHandler(newExecutor());
        TestDriver driver = newTestDriver("http://localhost/", requestHandler);

        MyResponseHandler responseHandler = new MyResponseHandler();
        Request request = new Request(driver, URI.create("http://localhost/"));
        ContentChannel requestContent = request.connect(responseHandler);
        ByteBuffer buf = ByteBuffer.allocate(69);
        requestContent.write(buf, null);
        requestContent.close(null);
        request.release();

        requestHandler.entryLatch.countDown();
        assertTrue(requestHandler.exitLatch.await(600, TimeUnit.SECONDS));
        assertSame(request, requestHandler.request);
        assertSame(buf, requestHandler.content.read());
        assertNull(requestHandler.content.read());

        assertTrue(responseHandler.latch.await(600, TimeUnit.SECONDS));
        assertSame(requestHandler.response, responseHandler.response);
        assertNull(responseHandler.content.read());
        assertTrue(driver.close());
    }

    @Test
    void requireThatNotImplementedHandlerDoesNotPreventShutdown() throws Exception {
        TestDriver driver = newTestDriver("http://localhost/", new ThreadedRequestHandler(newExecutor()) {

        });
        assertEquals(Response.Status.NOT_IMPLEMENTED,
                dispatchRequest(driver, "http://localhost/", ByteBuffer.wrap(new byte[]{69}))
                        .get(600, TimeUnit.SECONDS).getStatus());
        assertTrue(driver.close());
    }

    @Test
    void requireThatThreadedRequestHandlerRetainsTheRequestUntilHandlerIsRun() throws Exception {
        final TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        final AtomicInteger baseRetainCount = new AtomicInteger();
        builder.serverBindings().bind("http://localhost/base", new AbstractRequestHandler() {

            @Override
            public ContentChannel handleRequest(Request request, ResponseHandler handler) {
                baseRetainCount.set(request.retainCount());
                handler.handleResponse(new Response(Response.Status.OK)).close(null);
                return null;
            }
        });
        final CountDownLatch entryLatch = new CountDownLatch(1);
        final CountDownLatch exitLatch = new CountDownLatch(1);
        final AtomicInteger testRetainCount = new AtomicInteger();
        builder.serverBindings().bind("http://localhost/test", new ThreadedRequestHandler(newExecutor()) {

            @Override
            public void handleRequest(Request request, ReadableContentChannel requestContent,
                    ResponseHandler responseHandler) {
                try {
                    entryLatch.await(600, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    return;
                }
                testRetainCount.set(request.retainCount());
                responseHandler.handleResponse(new Response(Response.Status.OK)).close(null);
                requestContent.read(); // drain content to call completion handlers
                exitLatch.countDown();
            }
        });
        driver.activateContainer(builder);
        dispatchRequest(driver, "http://localhost/base");
        dispatchRequest(driver, "http://localhost/test");
        entryLatch.countDown();
        exitLatch.await(600, TimeUnit.SECONDS);
        assertEquals(baseRetainCount.get(), testRetainCount.get());
        assertTrue(driver.close());
    }

    private static TestDriver newTestDriver(String uri, RequestHandler requestHandler) {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.serverBindings().bind(uri, requestHandler);
        driver.activateContainer(builder);
        return driver;
    }

    private static CompletableFuture<Response> dispatchRequest(final CurrentContainer container, final String uri,
                                                               final ByteBuffer... content) {
        return new RequestDispatch() {

            @Override
            protected Request newRequest() {
                return new Request(container, URI.create(uri));
            }

            @Override
            protected Iterable<ByteBuffer> requestContent() {
                return Arrays.asList(content);
            }
        }.dispatch();
    }

    private static Executor newExecutor() {
        return Executors.newSingleThreadExecutor();
    }

    private static class MyRequestHandler extends ThreadedRequestHandler {

        final CountDownLatch entryLatch = new CountDownLatch(1);
        final CountDownLatch exitLatch = new CountDownLatch(1);
        final ReadableContentChannel content = new ReadableContentChannel();
        Response response = null;
        Request request = null;

        MyRequestHandler(Executor executor) {
            super(executor);
        }

        @Override
        public void handleRequest(Request request, BufferedContentChannel content, ResponseHandler handler) {
            try {
                if (!entryLatch.await(600, TimeUnit.SECONDS)) {
                    return;
                }
                this.request = request;
                content.connectTo(this.content);
                response = new Response(Response.Status.OK);
                handler.handleResponse(response).close(null);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                exitLatch.countDown();
            }
        }
    }

    private static class MyResponseHandler implements ResponseHandler {

        final CountDownLatch latch = new CountDownLatch(1);
        final ReadableContentChannel content = new ReadableContentChannel();
        Response response = null;

        @Override
        public ContentChannel handleResponse(Response response) {
            this.response = response;
            latch.countDown();

            BufferedContentChannel content = new BufferedContentChannel();
            content.connectTo(this.content);
            return content;
        }
    }
}
