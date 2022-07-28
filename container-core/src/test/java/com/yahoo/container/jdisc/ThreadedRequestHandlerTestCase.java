// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.handler.*;
import com.yahoo.jdisc.test.TestDriver;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

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
    void requireThatHandlerSetsRequestTimeout() throws InterruptedException {
        Executor executor = Executors.newSingleThreadExecutor();
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        MyRequestHandler requestHandler = MyRequestHandler.newInstance(executor);
        builder.serverBindings().bind("http://localhost/", requestHandler);
        driver.activateContainer(builder);

        MyResponseHandler responseHandler = new MyResponseHandler();
        driver.dispatchRequest("http://localhost/", responseHandler);

        requestHandler.entryLatch.countDown();
        assertTrue(requestHandler.exitLatch.await(60, TimeUnit.SECONDS));
        assertNull(requestHandler.content.read());
        assertNotNull(requestHandler.request.getTimeout(TimeUnit.MILLISECONDS));

        assertTrue(responseHandler.latch.await(60, TimeUnit.SECONDS));
        assertNull(responseHandler.content.read());
        assertTrue(driver.close());
    }

    @Test
    void requireThatOverriddenRequestTimeoutIsUsed() throws InterruptedException {
        Executor executor = Executors.newSingleThreadExecutor();
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        MyRequestHandler requestHandler = MyRequestHandler.newWithTimeout(executor, Duration.ofSeconds(1));
        builder.serverBindings().bind("http://localhost/", requestHandler);
        driver.activateContainer(builder);

        MyResponseHandler responseHandler = new MyResponseHandler();
        driver.dispatchRequest("http://localhost/", responseHandler);

        requestHandler.entryLatch.countDown();
        assertTrue(requestHandler.exitLatch.await(60, TimeUnit.SECONDS));
        assertEquals(1, (long) requestHandler.request.getTimeout(TimeUnit.SECONDS));

        assertTrue(responseHandler.latch.await(60, TimeUnit.SECONDS));
        assertNull(responseHandler.content.read());
        assertTrue(driver.close());
    }

    @Test
    void requireThatRequestAndResponseReachHandlers() throws InterruptedException {
        Executor executor = Executors.newSingleThreadExecutor();
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        MyRequestHandler requestHandler = MyRequestHandler.newInstance(executor);
        builder.serverBindings().bind("http://localhost/", requestHandler);
        driver.activateContainer(builder);

        MyResponseHandler responseHandler = new MyResponseHandler();
        Request request = new Request(driver, URI.create("http://localhost/"));
        ContentChannel requestContent = request.connect(responseHandler);
        ByteBuffer buf = ByteBuffer.allocate(69);
        requestContent.write(buf, null);
        requestContent.close(null);
        request.release();

        requestHandler.entryLatch.countDown();
        assertTrue(requestHandler.exitLatch.await(60, TimeUnit.SECONDS));
        assertSame(request, requestHandler.request);
        assertSame(buf, requestHandler.content.read());
        assertNull(requestHandler.content.read());

        assertTrue(responseHandler.latch.await(60, TimeUnit.SECONDS));
        assertSame(requestHandler.response, responseHandler.response);
        assertNull(responseHandler.content.read());
        assertTrue(driver.close());
    }

    @Test
    void requireThatRejectedExecutionIsHandledGracefully() throws Exception {
        // Instrumentation.
        final Executor executor = new Executor() {
            @Override
            public void execute(final Runnable command) {
                throw new RejectedExecutionException("Deliberately thrown; simulating overloaded executor");
            }
        };
        final RequestHandler requestHandler = new ThreadedRequestHandler(executor) {
            @Override
            protected void handleRequest(Request request, BufferedContentChannel requestContent, ResponseHandler responseHandler) {
                throw new AssertionError("Should never get here");
            }
        };

        // Setup.
        final TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        final ContainerBuilder builder = driver.newContainerBuilder();
        builder.serverBindings().bind("http://localhost/", requestHandler);
        driver.activateContainer(builder);
        final MyResponseHandler responseHandler = new MyResponseHandler();

        // Execution.
        try {
            driver.dispatchRequest("http://localhost/", responseHandler);
            fail("Above statement should throw exception");
        } catch (OverloadException e) {
            // As expected.
        }

        // Verification.
        assertEquals(0, responseHandler.latch.getCount(), "Response handler should be invoked synchronously in this case.");
        assertEquals(Response.Status.SERVICE_UNAVAILABLE, responseHandler.response.getStatus());
        assertNull(responseHandler.content.read());
        assertTrue(driver.close());
    }

    @Test
    void requireThatRequestContentIsClosedIfHandlerIgnoresIt() throws InterruptedException {
        Executor executor = Executors.newSingleThreadExecutor();
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        MyRequestHandler requestHandler = MyRequestHandler.newIgnoreContent(executor);
        builder.serverBindings().bind("http://localhost/", requestHandler);
        driver.activateContainer(builder);

        MyResponseHandler responseHandler = new MyResponseHandler();
        ContentChannel content = driver.connectRequest("http://localhost/", responseHandler);
        MyCompletion writeCompletion = new MyCompletion();
        content.write(ByteBuffer.allocate(69), writeCompletion);
        MyCompletion closeCompletion = new MyCompletion();
        content.close(closeCompletion);

        requestHandler.entryLatch.countDown();
        assertTrue(requestHandler.exitLatch.await(60, TimeUnit.SECONDS));
        assertTrue(writeCompletion.latch.await(60, TimeUnit.SECONDS));
        assertTrue(writeCompletion.completed);
        assertTrue(closeCompletion.latch.await(60, TimeUnit.SECONDS));
        assertTrue(writeCompletion.completed);

        assertTrue(responseHandler.latch.await(60, TimeUnit.SECONDS));
        assertSame(requestHandler.response, responseHandler.response);
        assertNull(responseHandler.content.read());
        assertTrue(driver.close());
    }

    @Test
    void requireThatResponseIsDispatchedIfHandlerIgnoresIt() throws InterruptedException {
        Executor executor = Executors.newSingleThreadExecutor();
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        MyRequestHandler requestHandler = MyRequestHandler.newIgnoreResponse(executor);
        builder.serverBindings().bind("http://localhost/", requestHandler);
        driver.activateContainer(builder);

        MyResponseHandler responseHandler = new MyResponseHandler();
        driver.dispatchRequest("http://localhost/", responseHandler);
        requestHandler.entryLatch.countDown();
        assertTrue(requestHandler.exitLatch.await(60, TimeUnit.SECONDS));
        assertNull(requestHandler.content.read());

        assertTrue(responseHandler.latch.await(60, TimeUnit.SECONDS));
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR, responseHandler.response.getStatus());
        assertNull(responseHandler.content.read());
        assertTrue(driver.close());
    }

    @Test
    void requireThatRequestContentIsClosedAndResponseIsDispatchedIfHandlerIgnoresIt()
            throws InterruptedException
    {
        Executor executor = Executors.newSingleThreadExecutor();
        assertThatRequestContentIsClosedAndResponseIsDispatchedIfHandlerIgnoresIt(
                MyRequestHandler.newIgnoreAll(executor));
        assertThatRequestContentIsClosedAndResponseIsDispatchedIfHandlerIgnoresIt(
                MyRequestHandler.newThrowException(executor));
    }

    private static void assertThatRequestContentIsClosedAndResponseIsDispatchedIfHandlerIgnoresIt(
            MyRequestHandler requestHandler)
            throws InterruptedException
    {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.serverBindings().bind("http://localhost/", requestHandler);
        driver.activateContainer(builder);

        MyResponseHandler responseHandler = new MyResponseHandler();
        ContentChannel content = driver.connectRequest("http://localhost/", responseHandler);
        MyCompletion writeCompletion = new MyCompletion();
        content.write(ByteBuffer.allocate(69), writeCompletion);
        MyCompletion closeCompletion = new MyCompletion();
        content.close(closeCompletion);

        requestHandler.entryLatch.countDown();
        assertTrue(requestHandler.exitLatch.await(60, TimeUnit.SECONDS));
        assertTrue(writeCompletion.latch.await(60, TimeUnit.SECONDS));
        assertTrue(writeCompletion.completed);
        assertTrue(closeCompletion.latch.await(60, TimeUnit.SECONDS));
        assertTrue(writeCompletion.completed);

        assertTrue(responseHandler.latch.await(60, TimeUnit.SECONDS));
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR, responseHandler.response.getStatus());
        assertNull(responseHandler.content.read());
        assertTrue(driver.close());
    }

    private static class MyRequestHandler extends ThreadedRequestHandler {

        final CountDownLatch entryLatch = new CountDownLatch(1);
        final CountDownLatch exitLatch = new CountDownLatch(1);
        final ReadableContentChannel content = new ReadableContentChannel();
        final boolean consumeContent;
        final boolean createResponse;
        final boolean throwException;
        final Duration timeout;
        Response response = null;
        Request request = null;

        MyRequestHandler(Executor executor,
                         boolean consumeContent,
                         boolean createResponse,
                         boolean throwException,
                         Duration timeout) {
            super(executor);
            this.consumeContent = consumeContent;
            this.createResponse = createResponse;
            this.throwException = throwException;
            this.timeout = timeout;
        }

        @Override
        public void handleRequest(Request request, BufferedContentChannel content, ResponseHandler handler) {
            try {
                if (!entryLatch.await(60, TimeUnit.SECONDS)) {
                    return;
                }
                if (throwException) {
                    throw new RuntimeException();
                }
                this.request = request;
                if (consumeContent) {
                    content.connectTo(this.content);
                }
                if (createResponse) {
                    response = new Response(Response.Status.OK);
                    handler.handleResponse(response).close(null);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                exitLatch.countDown();
            }
        }

        @Override
        public Duration getTimeout() {
            if (timeout == null) return super.getTimeout();
            return timeout;
        }

        static MyRequestHandler newInstance(Executor executor) {
            return new MyRequestHandler(executor, true, true, false, null);
        }

        static MyRequestHandler newThrowException(Executor executor) {
            return new MyRequestHandler(executor, true, true, true, null);
        }

        static MyRequestHandler newIgnoreContent(Executor executor) {
            return new MyRequestHandler(executor, false, true, false, null);
        }

        static MyRequestHandler newIgnoreResponse(Executor executor) {
            return new MyRequestHandler(executor, true, false, false, null);
        }

        static MyRequestHandler newIgnoreAll(Executor executor) {
            return new MyRequestHandler(executor, false, false, false, null);
        }

        static MyRequestHandler newWithTimeout(Executor executor, Duration timeout) {
            return new MyRequestHandler(executor, false, false, false, timeout);
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

    private static class MyCompletion implements CompletionHandler {

        final CountDownLatch latch = new CountDownLatch(1);
        boolean completed;

        @Override
        public void completed() {
            completed = true;
            latch.countDown();
        }

        @Override
        public void failed(Throwable t) {
            latch.countDown();
        }
    }

    @Test
    void testMaxPendingOutputStream() throws IOException, ExecutionException, InterruptedException {
        ReadableContentChannel buffer = new ReadableContentChannel();
        MaxPendingContentChannelOutputStream limited = new MaxPendingContentChannelOutputStream(buffer, 2);

        ExecutorService executor = Executors.newSingleThreadExecutor();

        limited.send(ByteBuffer.allocate(2));
        limited.send(ByteBuffer.allocate(1)); // 2 is not > 2, so OK.

        // Next write will block.
        Future<?> future = executor.submit(() -> Exceptions.uncheck(() -> limited.send(ByteBuffer.allocate(0))));
        try {
            future.get(100, TimeUnit.MILLISECONDS);
            fail("Should not be able to write now");
        }
        catch (TimeoutException expected) {
        }

        // Free buffer capacity, so write completes, then drain buffer.
        assertEquals(2, buffer.read().capacity());
        future.get();
        buffer.close(null);
        assertEquals(1, buffer.read().capacity());
        assertEquals(0, buffer.read().capacity());
        assertNull(buffer.read());

        // Buffer is closed, so further writes fail. This does not count towards pending bytes.
        try {
            limited.send(ByteBuffer.allocate(3));
            fail("Should throw");
        }
        catch (IOException expected) {
        }
        try {
            limited.send(ByteBuffer.allocate(3));
            fail("Should throw");
        }
        catch (IOException expected) {
        }
    }

}
