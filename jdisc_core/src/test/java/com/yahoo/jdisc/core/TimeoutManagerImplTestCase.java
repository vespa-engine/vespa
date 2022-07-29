// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.TimeoutManager;
import com.yahoo.jdisc.Timer;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestDeniedException;
import com.yahoo.jdisc.handler.RequestDispatch;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.jdisc.test.NonWorkingRequest;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
public class TimeoutManagerImplTestCase {

    private static final String REQUEST_URI = "http://host/path";

    @Test
    void requireThatDefaultIsNoTimeout() {
        Context ctx = new Context(MyRequestHandler.newEagerResponse());
        assertNull(ctx.dispatchRequest(null, MyResponseHandler.newInstance()));
        assertTrue(ctx.close());
    }

    @Test
    void requireThatTimeoutCanBeSetByServerProvider() {
        Context ctx = new Context(MyRequestHandler.newEagerResponse());
        assertEquals(Long.valueOf(69), ctx.dispatchRequest(69L, MyResponseHandler.newInstance()));
        assertTrue(ctx.close());
    }

    @Test
    void requireThatTimeoutCanBeSetByRequestHandler() {
        Context ctx = new Context(MyRequestHandler.newTimeoutWithEagerResponse(69));
        assertEquals(Long.valueOf(69), ctx.dispatchRequest(null, MyResponseHandler.newInstance()));
        assertTrue(ctx.close());
    }

    @Test
    void requireThatTimeoutRequestHandlerTimeoutHasPrecedence() {
        Context ctx = new Context(MyRequestHandler.newTimeoutWithEagerResponse(6));
        assertEquals(Long.valueOf(6), ctx.dispatchRequest(9L, MyResponseHandler.newInstance()));
        assertTrue(ctx.close());
    }

    @Test
    void requireThatResponseCancelsTimeout() throws InterruptedException {
        Context ctx = new Context(MyRequestHandler.newEagerResponse());
        assertEquals(Response.Status.OK, ctx.awaitResponse(69L, MyResponseHandler.newInstance()));
        assertEquals(Response.Status.OK, ctx.awaitResponse(69L, MyResponseHandler.newInstance()));
        assertTrue(ctx.close());
    }

    @Test
    void requireThatNullRequestContentCanTimeout() throws InterruptedException {
        Context ctx = new Context(MyRequestHandler.newNullContent());
        assertEquals(Response.Status.GATEWAY_TIMEOUT, ctx.awaitResponse(69L, MyResponseHandler.newInstance()));
        assertEquals(Response.Status.GATEWAY_TIMEOUT, ctx.awaitResponse(69L, MyResponseHandler.newInstance()));
        assertTrue(ctx.close());
    }

    @Test
    void requireThatTimeoutWorksAfterRequestDenied() throws InterruptedException {
        Context ctx = new Context(MyRequestHandler.newFirstRequestDenied());
        try {
            ctx.dispatchRequest(null, MyResponseHandler.newInstance());
            fail();
        } catch (RequestDeniedException e) {

        }
        assertEquals(Response.Status.GATEWAY_TIMEOUT, ctx.awaitResponse(69L, MyResponseHandler.newInstance()));
        assertTrue(ctx.close());
    }

    @Test
    void requireThatTimeoutWorksAfterResponseDenied() throws InterruptedException {
        Context ctx = new Context(MyRequestHandler.newInstance());
        assertEquals(Response.Status.GATEWAY_TIMEOUT, ctx.awaitResponse(69L, MyResponseHandler.newResponseDenied()));
        assertEquals(Response.Status.GATEWAY_TIMEOUT, ctx.awaitResponse(69L, MyResponseHandler.newInstance()));
        assertTrue(ctx.close());
    }

    @Test
    void requireThatTimeoutWorksAfterResponseThrowsException() throws InterruptedException {
        Context ctx = new Context(MyRequestHandler.newInstance());
        assertEquals(Response.Status.GATEWAY_TIMEOUT, ctx.awaitResponse(69L, MyResponseHandler.newThrowException()));
        assertEquals(Response.Status.GATEWAY_TIMEOUT, ctx.awaitResponse(69L, MyResponseHandler.newInstance()));
        assertTrue(ctx.close());
    }

    @Test
    void requireThatTimeoutWorksAfterResponseInterruptsThread() throws InterruptedException {
        Context ctx = new Context(MyRequestHandler.newInstance());
        assertEquals(Response.Status.GATEWAY_TIMEOUT, ctx.awaitResponse(69L, MyResponseHandler.newInterruptThread()));
        assertEquals(Response.Status.GATEWAY_TIMEOUT, ctx.awaitResponse(69L, MyResponseHandler.newInstance()));
        assertTrue(ctx.close());
    }

    @Test
    void requireThatTimeoutOccursInOrder() throws InterruptedException {
        Context ctx = new Context(MyRequestHandler.newInstance());
        MyResponseHandler foo = MyResponseHandler.newInstance();
        ctx.dispatchRequest(300L, foo);

        MyResponseHandler bar = MyResponseHandler.newInstance();
        ctx.dispatchRequest(100L, bar);

        MyResponseHandler baz = MyResponseHandler.newInstance();
        ctx.dispatchRequest(200L, baz);

        ctx.forwardToTime(100);
        assertFalse(foo.await(10, TimeUnit.MILLISECONDS));
        assertTrue(bar.await(600, TimeUnit.SECONDS));
        assertFalse(baz.await(10, TimeUnit.MILLISECONDS));

        ctx.forwardToTime(200);
        assertFalse(foo.await(10, TimeUnit.MILLISECONDS));
        assertTrue(baz.await(600, TimeUnit.SECONDS));

        ctx.forwardToTime(300);
        assertTrue(foo.await(600, TimeUnit.SECONDS));

        assertTrue(ctx.close());
    }

    @Test
    void requireThatResponseHandlerIsWellBehavedAfterTimeout() throws InterruptedException {
        Context ctx = new Context(MyRequestHandler.newInstance());
        assertEquals(Response.Status.GATEWAY_TIMEOUT, ctx.awaitResponse(69L, MyResponseHandler.newInstance()));

        ContentChannel content = ctx.requestHandler.responseHandler.handleResponse(new Response(Response.Status.OK));
        assertNotNull(content);

        content.write(ByteBuffer.allocate(69), null);
        MyCompletion completion = new MyCompletion();
        content.write(ByteBuffer.allocate(69), completion);
        assertTrue(completion.completed.await(600, TimeUnit.SECONDS));

        completion = new MyCompletion();
        content.close(completion);
        assertTrue(completion.completed.await(600, TimeUnit.SECONDS));

        assertTrue(ctx.close());
    }

    @Test
    void requireThatManagedHandlerForwardsAllCalls() throws InterruptedException {
        Request request = NonWorkingRequest.newInstance(REQUEST_URI);
        MyRequestHandler requestHandler = MyRequestHandler.newInstance();
        TimeoutManagerImpl timeoutManager = new TimeoutManagerImpl(Executors.defaultThreadFactory(),
                new SystemTimer());
        RequestHandler managedHandler = timeoutManager.manageHandler(requestHandler);

        MyResponseHandler responseHandler = MyResponseHandler.newInstance();
        ContentChannel requestContent = managedHandler.handleRequest(request, responseHandler);
        assertNotNull(requestContent);

        ByteBuffer buf = ByteBuffer.allocate(69);
        requestContent.write(buf, null);
        assertSame(buf, requestHandler.content.buf);
        MyCompletion writeCompletion = new MyCompletion();
        requestContent.write(buf = ByteBuffer.allocate(69), writeCompletion);
        assertSame(buf, requestHandler.content.buf);
        requestHandler.content.writeCompletion.completed();
        assertTrue(writeCompletion.completed.await(600, TimeUnit.SECONDS));

        MyCompletion closeCompletion = new MyCompletion();
        requestContent.close(closeCompletion);
        requestHandler.content.closeCompletion.completed();
        assertTrue(closeCompletion.completed.await(600, TimeUnit.SECONDS));

        managedHandler.release();
        assertTrue(requestHandler.destroyed);

        Response response = new Response(Response.Status.OK);
        ContentChannel responseContent = requestHandler.responseHandler.handleResponse(response);
        assertNotNull(responseContent);

        responseContent.write(buf = ByteBuffer.allocate(69), null);
        assertSame(buf, responseHandler.content.buf);
        responseContent.write(buf = ByteBuffer.allocate(69), writeCompletion = new MyCompletion());
        assertSame(buf, responseHandler.content.buf);
        responseHandler.content.writeCompletion.completed();
        assertTrue(writeCompletion.completed.await(600, TimeUnit.SECONDS));

        responseContent.close(closeCompletion = new MyCompletion());
        responseHandler.content.closeCompletion.completed();
        assertTrue(closeCompletion.completed.await(600, TimeUnit.SECONDS));

        assertSame(response, responseHandler.response.get());
    }

    @Test
    void requireThatTimeoutOccursAtExpectedTime() throws InterruptedException {
        final Context ctx = new Context(MyRequestHandler.newInstance());
        final MyResponseHandler responseHandler = MyResponseHandler.newInstance();

        ctx.forwardToTime(100);
        new RequestDispatch() {

            @Override
            protected Request newRequest() {
                Request request = new Request(ctx.driver, URI.create(REQUEST_URI));
                request.setTimeout(300, TimeUnit.MILLISECONDS);
                return request;
            }

            @Override
            public ContentChannel handleResponse(Response response) {
                return responseHandler.handleResponse(response);
            }
        }.dispatch();

        ctx.forwardToTime(300);
        assertFalse(responseHandler.await(100, TimeUnit.MILLISECONDS));
        ctx.forwardToTime(400);
        assertTrue(responseHandler.await(600, TimeUnit.SECONDS));

        Response response = responseHandler.response.get();
        assertNotNull(response);
        assertEquals(Response.Status.GATEWAY_TIMEOUT, response.getStatus());
        assertTrue(ctx.close());
    }

    @Test
    void requireThatQueueEntryIsRemovedWhenResponseHandlerIsCalledBeforeTimeout() {
        Context ctx = new Context(MyRequestHandler.newInstance());
        ctx.dispatchRequest(69L, MyResponseHandler.newInstance());
        assertTrue(ctx.awaitQueueSize(1, 600, TimeUnit.SECONDS));
        ctx.requestHandler.respond();
        assertTrue(ctx.awaitQueueSize(0, 600, TimeUnit.SECONDS));
        assertTrue(ctx.close());
    }

    @Test
    void requireThatNoEntryIsMadeIfTimeoutIsNull() {
        Context ctx = new Context(MyRequestHandler.newInstance());
        ctx.dispatchRequest(null, MyResponseHandler.newInstance());
        assertFalse(ctx.awaitQueueSize(1, 100, TimeUnit.MILLISECONDS));
        assertTrue(ctx.awaitQueueSize(0, 600, TimeUnit.SECONDS));
        ctx.requestHandler.respond();
        assertTrue(ctx.close());
    }

    @Test
    void requireThatNoEntryIsMadeIfHandleRequestCallsHandleResponse() {
        Context ctx = new Context(MyRequestHandler.newEagerResponse());
        ctx.dispatchRequest(69L, MyResponseHandler.newInstance());
        assertFalse(ctx.awaitQueueSize(1, 100, TimeUnit.MILLISECONDS));
        assertTrue(ctx.awaitQueueSize(0, 600, TimeUnit.SECONDS));
        assertTrue(ctx.close());
    }

    @Test
    void requireThatNoEntryIsMadeIfTimeoutHandlerHasBeenSet() {
        final Context ctx = new Context(MyRequestHandler.newInstance());
        new RequestDispatch() {

            @Override
            protected Request newRequest() {
                Request request = new Request(ctx.driver, URI.create(REQUEST_URI));
                request.setTimeout(10, TimeUnit.MILLISECONDS);
                request.setTimeoutManager(new TimeoutManager() {

                    @Override
                    public void scheduleTimeout(Request request) {

                    }
                });
                return request;
            }
        }.dispatch();

        assertFalse(ctx.awaitQueueSize(1, 100, TimeUnit.MILLISECONDS));
        assertTrue(ctx.awaitQueueSize(0, 600, TimeUnit.SECONDS));
        ctx.requestHandler.respond();
        assertTrue(ctx.close());
    }

    private static class Context implements Module, Timer {

        final MyRequestHandler requestHandler;
        final TimeoutManagerImpl timeoutManager;
        final TestDriver driver;
        long millis = 0;

        Context(MyRequestHandler requestHandler) {
            this.requestHandler = requestHandler;
            this.driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi(this);

            ContainerBuilder builder = driver.newContainerBuilder();
            builder.serverBindings().bind(REQUEST_URI, requestHandler);
            driver.activateContainer(builder);

            Container ref = driver.newReference(URI.create(REQUEST_URI));
            timeoutManager = ref.getInstance(TimeoutManagerImpl.class);
            ref.release();
        }

        void forwardToTime(long millis) {
            while (this.millis < millis) {
                this.millis += ScheduledQueue.MILLIS_PER_SLOT;
                timeoutManager.checkTasks(this.millis);
            }
        }

        boolean close() {
            return driver.close();
        }

        @Override
        public void configure(Binder binder) {
            binder.bind(Timer.class).toInstance(this);
        }

        @Override
        public long currentTimeMillis() {
            return millis;
        }

        int awaitResponse(Long serverProviderTimeout, MyResponseHandler responseHandler) throws InterruptedException {
            Long timeout = new MyServerProvider(serverProviderTimeout).dispatchRequest(driver, responseHandler);
            long timeoutAt;
            if (timeout == null) {
                timeoutAt = millis + TimeUnit.SECONDS.toMillis(120);
            } else {
                timeoutAt = millis + timeout;
            }
            forwardToTime(timeoutAt);
            if (!responseHandler.await(600, TimeUnit.SECONDS)) {
                fail("Request handler failed to respond within allocated time.");
            }
            return responseHandler.response.get().getStatus();
        }

        boolean awaitQueueSize(int expectedSize, int timeout, TimeUnit unit) {
            for (long i = 0, len = unit.toMillis(timeout) / 100; i < len; ++i) {
                if (timeoutManager.queueSize() == expectedSize) {
                    return true;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    fail();
                }
            }
            return false;
        }

        public Long dispatchRequest(Long serverProviderTimeout, MyResponseHandler responseHandler) {
            return new MyServerProvider(serverProviderTimeout).dispatchRequest(driver, responseHandler);
        }
    }

    private static class MyServerProvider {

        final Long timeout;

        MyServerProvider(Long timeout) {
            this.timeout = timeout;
        }

        Long dispatchRequest(CurrentContainer container, ResponseHandler responseHandler) {
            Request request = null;
            ContentChannel content = null;
            try {
                request = new Request(container, URI.create(REQUEST_URI));
                if (timeout != null) {
                    request.setTimeout(timeout, TimeUnit.MILLISECONDS);
                }
                content = request.connect(responseHandler);
            } finally {
                if (request != null) {
                    request.release();
                }
                if (content != null) {
                    content.close(null);
                }
            }
            return request.getTimeout(TimeUnit.MILLISECONDS);
        }
    }

    private static class MyCompletion implements CompletionHandler {

        final CountDownLatch completed = new CountDownLatch(1);

        @Override
        public void completed() {
            completed.countDown();
        }

        @Override
        public void failed(Throwable t) {

        }
    }

    private static class MyContent implements ContentChannel {

        ByteBuffer buf;
        CompletionHandler writeCompletion;
        CompletionHandler closeCompletion;

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            this.buf = buf;
            this.writeCompletion = handler;
            if (handler != null) {
                handler.completed();
            }
        }

        @Override
        public void close(CompletionHandler handler) {
            this.closeCompletion = handler;
            if (handler != null) {
                handler.completed();
            }
        }

        static MyContent newInstance() {
            return new MyContent();
        }
    }

    private static class MyResponseHandler implements ResponseHandler {

        final AtomicReference<CountDownLatch> latch = new AtomicReference<>(new CountDownLatch(1));
        final AtomicReference<Response> response = new AtomicReference<>();
        final MyContent content;
        final boolean throwException;
        final boolean interruptThread;

        MyResponseHandler(MyContent content, boolean throwException, boolean interruptThread) {
            this.content = content;
            this.throwException = throwException;
            this.interruptThread = interruptThread;
        }

        boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.get().await(timeout, unit);
        }

        @Override
        public ContentChannel handleResponse(Response response) {
            if (this.response.getAndSet(response) != null) {
                throw new IllegalStateException("Response already received.");
            }
            latch.get().countDown();
            if (interruptThread) {
                Thread.currentThread().interrupt();
            }
            if (throwException) {
                throw new MyException();
            }
            return content;
        }

        static MyResponseHandler newInstance() {
            return new MyResponseHandler(MyContent.newInstance(), false, false);
        }

        static MyResponseHandler newResponseDenied() {
            return new MyResponseHandler(null, false, false);
        }

        static MyResponseHandler newThrowException() {
            return new MyResponseHandler(null, true, false);
        }

        static MyResponseHandler newInterruptThread() {
            return new MyResponseHandler(MyContent.newInstance(), false, true);
        }
    }

    private static class MyRequestHandler extends AbstractResource implements RequestHandler {

        final MyContent content;
        final Long timeout;
        int numDenied;
        int numEager;
        Request request = null;
        ResponseHandler responseHandler = null;
        boolean destroyed = false;

        MyRequestHandler(int numDenied, MyContent content, Long timeout, int numEager) {
            this.numDenied = numDenied;
            this.content = content;
            this.timeout = timeout;
            this.numEager = numEager;
        }

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            if (--numDenied >= 0) {
                throw new RequestDeniedException(request);
            }
            this.request = request;
            this.responseHandler = handler;
            if (timeout != null) {
                request.setTimeout(timeout, TimeUnit.MILLISECONDS);
            }
            if (--numEager >= 0) {
                respond();
            }
            return content;
        }

        @Override
        public void handleTimeout(Request request, ResponseHandler handler) {
            Response.dispatchTimeout(handler);
        }

        @Override
        protected void destroy() {
            destroyed = true;
        }

        void respond() {
            ContentChannel content = responseHandler.handleResponse(new Response(Response.Status.OK));
            if (content != null) {
                content.close(null);
            }
        }

        static MyRequestHandler newInstance() {
            return new MyRequestHandler(0, MyContent.newInstance(), null, 0);
        }

        static MyRequestHandler newTimeoutWithEagerResponse(long millis) {
            return new MyRequestHandler(0, MyContent.newInstance(), millis, Integer.MAX_VALUE);
        }

        static MyRequestHandler newFirstRequestDenied() {
            return new MyRequestHandler(1, MyContent.newInstance(), null, 0);
        }

        static MyRequestHandler newEagerResponse() {
            return new MyRequestHandler(0, MyContent.newInstance(), null, Integer.MAX_VALUE);
        }

        public static MyRequestHandler newNullContent() {
            return new MyRequestHandler(0, null, null, 0);
        }
    }

    private static class MyException extends RuntimeException {

    }
}
