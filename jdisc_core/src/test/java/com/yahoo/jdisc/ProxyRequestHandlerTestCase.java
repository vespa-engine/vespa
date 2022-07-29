// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc;

import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

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
public class ProxyRequestHandlerTestCase {

    @Test
    void requireThatRequestHandlerIsProxied() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        MyRequestHandler requestHandler = MyRequestHandler.newEagerCompletion();
        Request request = newRequest(driver, requestHandler);
        RequestHandler resolvedHandler = new ProxyRequestHandler(request.container().resolveHandler(request));
        MyResponseHandler responseHandler = MyResponseHandler.newEagerCompletion();
        resolvedHandler.handleRequest(request, responseHandler).close(null);
        request.release();
        assertNotNull(requestHandler.handler);
        resolvedHandler.handleTimeout(request, responseHandler);
        assertTrue(requestHandler.timeout);
        requestHandler.respond();

        requestHandler.release();
        final ResourceReference resolvedHandlerReference = resolvedHandler.refer();
        assertTrue(driver.close()); // release installed ref

        assertFalse(requestHandler.destroyed);
        resolvedHandlerReference.close();
        assertTrue(requestHandler.destroyed);
    }

    @Test
    void requireThatRequestContentCompletedIsProxied() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        MyRequestHandler requestHandler = MyRequestHandler.newInstance();
        Request request = newRequest(driver, requestHandler);
        ContentChannel resolvedContent = request.connect(MyResponseHandler.newEagerCompletion());
        request.release();

        assertSame(request, requestHandler.request);

        ByteBuffer buf = ByteBuffer.allocate(69);
        resolvedContent.write(buf, null);
        assertSame(buf, requestHandler.content.writeBuf);
        requestHandler.content.writeCompletion.completed();
        MyCompletion writeCompletion = new MyCompletion();
        resolvedContent.write(buf = ByteBuffer.allocate(69), writeCompletion);
        assertSame(buf, requestHandler.content.writeBuf);
        assertFalse(writeCompletion.completed);
        assertNull(writeCompletion.failed);
        requestHandler.content.writeCompletion.completed();
        assertTrue(writeCompletion.completed);
        assertNull(writeCompletion.failed);

        MyCompletion closeCompletion = new MyCompletion();
        resolvedContent.close(closeCompletion);
        assertTrue(requestHandler.content.closed);
        assertFalse(closeCompletion.completed);
        assertNull(writeCompletion.failed);
        requestHandler.content.closeCompletion.completed();
        assertTrue(closeCompletion.completed);
        assertNull(closeCompletion.failed);

        requestHandler.respond();
        assertTrue(driver.close());
    }

    @Test
    void requireThatRequestContentFailedIsProxied() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        MyRequestHandler requestHandler = MyRequestHandler.newInstance();
        Request request = newRequest(driver, requestHandler);
        ContentChannel resolvedContent = request.connect(MyResponseHandler.newEagerCompletion());
        request.release();

        assertSame(request, requestHandler.request);

        ByteBuffer buf = ByteBuffer.allocate(69);
        resolvedContent.write(buf, null);
        assertSame(buf, requestHandler.content.writeBuf);
        requestHandler.content.writeCompletion.completed();
        MyCompletion writeCompletion = new MyCompletion();
        resolvedContent.write(buf = ByteBuffer.allocate(69), writeCompletion);
        assertSame(buf, requestHandler.content.writeBuf);
        assertFalse(writeCompletion.completed);
        assertNull(writeCompletion.failed);
        MyException writeFailed = new MyException();
        requestHandler.content.writeCompletion.failed(writeFailed);
        assertFalse(writeCompletion.completed);
        assertSame(writeFailed, writeCompletion.failed);

        MyCompletion closeCompletion = new MyCompletion();
        resolvedContent.close(closeCompletion);
        assertTrue(requestHandler.content.closed);
        assertFalse(closeCompletion.completed);
        assertNull(closeCompletion.failed);
        MyException closeFailed = new MyException();
        requestHandler.content.closeCompletion.failed(closeFailed);
        assertFalse(writeCompletion.completed);
        assertSame(closeFailed, closeCompletion.failed);

        requestHandler.respond();
        assertTrue(driver.close());
    }

    @Test
    void requireThatNullRequestContentIsProxied() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        MyRequestHandler requestHandler = MyRequestHandler.newNullContent();
        Request request = newRequest(driver, requestHandler);
        request.connect(MyResponseHandler.newEagerCompletion()).close(null);
        request.release();

        requestHandler.respond();
        assertTrue(driver.close());
    }

    @Test
    void requireThatRequestWriteCompletionCanOnlyBeCalledOnce() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        MyRequestHandler requestHandler = MyRequestHandler.newInstance();
        Request request = newRequest(driver, requestHandler);
        ContentChannel resolvedContent = request.connect(MyResponseHandler.newEagerCompletion());
        request.release();

        CountingCompletionHandler completion = new CountingCompletionHandler();
        resolvedContent.write(ByteBuffer.allocate(0), completion);
        assertEquals(0, completion.numCompleted.get());
        assertEquals(0, completion.numFailed.get());
        requestHandler.content.writeCompletion.completed();
        assertEquals(1, completion.numCompleted.get());
        assertEquals(0, completion.numFailed.get());
        try {
            requestHandler.content.writeCompletion.completed();
            fail();
        } catch (IllegalStateException e) {
            // ignore
        }
        assertEquals(1, completion.numCompleted.get());
        assertEquals(0, completion.numFailed.get());
        try {
            requestHandler.content.writeCompletion.failed(new Throwable());
            fail();
        } catch (IllegalStateException e) {
            // ignore
        }
        assertEquals(1, completion.numCompleted.get());
        assertEquals(0, completion.numFailed.get());

        resolvedContent.close(null);
        requestHandler.content.closeCompletion.completed();
        requestHandler.respond();
        assertTrue(driver.close());
    }

    @Test
    void requireThatRequestCloseCompletionCanOnlyBeCalledOnce() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        MyRequestHandler requestHandler = MyRequestHandler.newInstance();
        Request request = newRequest(driver, requestHandler);
        ContentChannel resolvedContent = request.connect(MyResponseHandler.newEagerCompletion());
        request.release();

        CountingCompletionHandler completion = new CountingCompletionHandler();
        resolvedContent.close(completion);
        assertEquals(0, completion.numCompleted.get());
        assertEquals(0, completion.numFailed.get());
        requestHandler.content.closeCompletion.completed();
        assertEquals(1, completion.numCompleted.get());
        assertEquals(0, completion.numFailed.get());
        try {
            requestHandler.content.closeCompletion.completed();
            fail();
        } catch (IllegalStateException e) {
            // ignore
        }
        assertEquals(1, completion.numCompleted.get());
        assertEquals(0, completion.numFailed.get());
        try {
            requestHandler.content.closeCompletion.failed(new Throwable());
            fail();
        } catch (IllegalStateException e) {
            // ignore
        }
        assertEquals(1, completion.numCompleted.get());
        assertEquals(0, completion.numFailed.get());

        requestHandler.respond();
        assertTrue(driver.close());
    }

    @Test
    void requireThatResponseContentCompletedIsProxied() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        MyRequestHandler requestHandler = MyRequestHandler.newEagerCompletion();
        Request request = newRequest(driver, requestHandler);
        MyResponseHandler responseHandler = MyResponseHandler.newInstance();
        request.connect(responseHandler).close(null);
        request.release();
        Response response = new Response(Response.Status.OK);
        ContentChannel resolvedContent = requestHandler.handler.handleResponse(response);

        assertSame(response, responseHandler.response);

        ByteBuffer buf = ByteBuffer.allocate(69);
        resolvedContent.write(buf, null);
        assertSame(buf, responseHandler.content.writeBuf);
        responseHandler.content.writeCompletion.completed();
        MyCompletion writeCompletion = new MyCompletion();
        resolvedContent.write(buf = ByteBuffer.allocate(69), writeCompletion);
        assertSame(buf, responseHandler.content.writeBuf);
        assertFalse(writeCompletion.completed);
        assertNull(writeCompletion.failed);
        responseHandler.content.writeCompletion.completed();
        assertTrue(writeCompletion.completed);
        assertNull(writeCompletion.failed);

        MyCompletion closeCompletion = new MyCompletion();
        resolvedContent.close(closeCompletion);
        assertTrue(responseHandler.content.closed);
        assertFalse(closeCompletion.completed);
        assertNull(closeCompletion.failed);
        responseHandler.content.closeCompletion.completed();
        assertTrue(closeCompletion.completed);
        assertNull(closeCompletion.failed);
        assertTrue(driver.close());
    }

    @Test
    void requireThatResponseContentFailedIsProxied() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        MyRequestHandler requestHandler = MyRequestHandler.newEagerCompletion();
        Request request = newRequest(driver, requestHandler);
        MyResponseHandler responseHandler = MyResponseHandler.newInstance();
        request.connect(responseHandler).close(null);
        request.release();
        Response response = new Response(Response.Status.OK);
        ContentChannel resolvedContent = requestHandler.handler.handleResponse(response);

        assertSame(response, responseHandler.response);

        ByteBuffer buf = ByteBuffer.allocate(69);
        resolvedContent.write(buf, null);
        assertSame(buf, responseHandler.content.writeBuf);
        responseHandler.content.writeCompletion.completed();
        MyCompletion writeCompletion = new MyCompletion();
        resolvedContent.write(buf = ByteBuffer.allocate(69), writeCompletion);
        assertSame(buf, responseHandler.content.writeBuf);
        assertFalse(writeCompletion.completed);
        assertNull(writeCompletion.failed);
        MyException writeFailed = new MyException();
        responseHandler.content.writeCompletion.failed(writeFailed);
        assertFalse(writeCompletion.completed);
        assertSame(writeFailed, writeCompletion.failed);

        MyCompletion closeCompletion = new MyCompletion();
        resolvedContent.close(closeCompletion);
        assertTrue(responseHandler.content.closed);
        assertFalse(closeCompletion.completed);
        assertNull(closeCompletion.failed);
        MyException closeFailed = new MyException();
        responseHandler.content.closeCompletion.failed(closeFailed);
        assertFalse(closeCompletion.completed);
        assertSame(closeFailed, closeCompletion.failed);
        assertTrue(driver.close());
    }

    @Test
    void requireThatNullResponseContentIsProxied() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        MyRequestHandler requestHandler = MyRequestHandler.newEagerCompletion();
        Request request = newRequest(driver, requestHandler);
        ResponseHandler responseHandler = new ResponseHandler() {

            @Override
            public ContentChannel handleResponse(Response response) {
                return null;
            }
        };
        request.connect(responseHandler).close(null);
        requestHandler.handler.handleResponse(new Response(Response.Status.OK)).close(null);
        request.release();
        assertTrue(driver.close());
    }

    @Test
    void requireThatResponseWriteCompletionCanOnlyBeCalledOnce() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        MyRequestHandler requestHandler = MyRequestHandler.newEagerCompletion();
        MyResponseHandler responseHandler = MyResponseHandler.newInstance();
        Request request = newRequest(driver, requestHandler);
        request.connect(responseHandler).close(null);
        request.release();
        ContentChannel resolvedContent = requestHandler.handler.handleResponse(new Response(Response.Status.OK));

        CountingCompletionHandler completion = new CountingCompletionHandler();
        resolvedContent.write(ByteBuffer.allocate(0), completion);
        assertEquals(0, completion.numCompleted.get());
        assertEquals(0, completion.numFailed.get());
        responseHandler.content.writeCompletion.completed();
        assertEquals(1, completion.numCompleted.get());
        assertEquals(0, completion.numFailed.get());
        try {
            responseHandler.content.writeCompletion.completed();
            fail();
        } catch (IllegalStateException e) {
            // ignore
        }
        assertEquals(1, completion.numCompleted.get());
        assertEquals(0, completion.numFailed.get());
        try {
            responseHandler.content.writeCompletion.failed(new Throwable());
            fail();
        } catch (IllegalStateException e) {
            // ignore
        }
        assertEquals(1, completion.numCompleted.get());
        assertEquals(0, completion.numFailed.get());

        resolvedContent.close(null);
        responseHandler.content.closeCompletion.completed();
        assertTrue(driver.close());
    }

    @Test
    void requireThatResponseCloseCompletionCanOnlyBeCalledOnce() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        MyRequestHandler requestHandler = MyRequestHandler.newEagerCompletion();
        MyResponseHandler responseHandler = MyResponseHandler.newInstance();
        Request request = newRequest(driver, requestHandler);
        request.connect(responseHandler).close(null);
        request.release();
        ContentChannel resolvedContent = requestHandler.handler.handleResponse(new Response(Response.Status.OK));

        CountingCompletionHandler completion = new CountingCompletionHandler();
        resolvedContent.close(completion);
        assertEquals(0, completion.numCompleted.get());
        assertEquals(0, completion.numFailed.get());
        responseHandler.content.closeCompletion.completed();
        assertEquals(1, completion.numCompleted.get());
        assertEquals(0, completion.numFailed.get());
        try {
            responseHandler.content.closeCompletion.completed();
            fail();
        } catch (IllegalStateException e) {
            // ignore
        }
        assertEquals(1, completion.numCompleted.get());
        assertEquals(0, completion.numFailed.get());
        try {
            responseHandler.content.closeCompletion.failed(new Throwable());
            fail();
        } catch (IllegalStateException e) {
            // ignore
        }
        assertEquals(1, completion.numCompleted.get());
        assertEquals(0, completion.numFailed.get());
        assertTrue(driver.close());
    }

    @Test
    void requireThatUncaughtCompletionFailureIsLogged() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        MyRequestHandler requestHandler = MyRequestHandler.newInstance();
        Request request = newRequest(driver, requestHandler);
        ContentChannel resolvedContent = request.connect(MyResponseHandler.newEagerCompletion());
        request.release();

        MyLogHandler logHandler = new MyLogHandler();
        Logger.getLogger(ProxyRequestHandler.class.getName()).addHandler(logHandler);

        resolvedContent.write(ByteBuffer.allocate(69), null);
        MyException writeFailed = new MyException();
        requestHandler.content.writeCompletion.failed(writeFailed);
        assertNotNull(logHandler.record);
        assertSame(writeFailed, logHandler.record.getThrown());

        resolvedContent.close(null);
        MyException closeFailed = new MyException();
        requestHandler.content.closeCompletion.failed(closeFailed);
        assertNotNull(logHandler.record);
        assertSame(closeFailed, logHandler.record.getThrown());

        requestHandler.respond();
        assertTrue(driver.close());
    }

    private static Request newRequest(TestDriver driver, RequestHandler requestHandler) {
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.serverBindings().bind("http://host/path", requestHandler);
        driver.activateContainer(builder);
        return new Request(driver, URI.create("http://host/path"));
    }

    private static class MyException extends RuntimeException {

    }

    private static class MyCompletion implements CompletionHandler {

        boolean completed = false;
        Throwable failed = null;

        @Override
        public void completed() {
            completed = true;
        }

        @Override
        public void failed(Throwable t) {
            failed = t;
        }
    }

    private static class MyContent implements ContentChannel {

        final boolean eagerCompletion;
        CompletionHandler writeCompletion = null;
        CompletionHandler closeCompletion = null;
        ByteBuffer writeBuf = null;
        boolean closed = false;

        MyContent(boolean eagerCompletion) {
            this.eagerCompletion = eagerCompletion;
        }

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            writeBuf = buf;
            writeCompletion = handler;
            if (eagerCompletion) {
                writeCompletion.completed();
            }
        }

        @Override
        public void close(CompletionHandler handler) {
            closed = true;
            closeCompletion = handler;
            if (eagerCompletion) {
                closeCompletion.completed();
            }
        }

        static MyContent newInstance() {
            return new MyContent(false);
        }

        static MyContent newEagerCompletion() {
            return new MyContent(true);
        }
    }

    private static class MyRequestHandler extends AbstractResource implements RequestHandler {

        final MyContent content;
        Request request = null;
        ResponseHandler handler = null;
        boolean timeout = false;
        boolean destroyed = false;

        MyRequestHandler(MyContent content) {
            this.content = content;
        }

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            this.request = request;
            this.handler = handler;
            return content;
        }

        @Override
        public void handleTimeout(Request request, ResponseHandler handler) {
            timeout = true;
        }

        @Override
        public void destroy() {
            destroyed = true;
        }

        void respond() {
            handler.handleResponse(new Response(Response.Status.OK)).close(null);
        }

        static MyRequestHandler newInstance() {
            return new MyRequestHandler(MyContent.newInstance());
        }

        static MyRequestHandler newEagerCompletion() {
            return new MyRequestHandler(MyContent.newEagerCompletion());
        }

        static MyRequestHandler newNullContent() {
            return new MyRequestHandler(null);
        }
    }

    private static class MyResponseHandler implements ResponseHandler {

        final MyContent content;
        Response response;

        MyResponseHandler(MyContent content) {
            this.content = content;
        }

        @Override
        public ContentChannel handleResponse(Response response) {
            this.response = response;
            return content;
        }

        static MyResponseHandler newInstance() {
            return new MyResponseHandler(MyContent.newInstance());
        }

        static MyResponseHandler newEagerCompletion() {
            return new MyResponseHandler(MyContent.newEagerCompletion());
        }
    }

    private static class MyLogHandler extends Handler {

        LogRecord record;

        @Override
        public void publish(LogRecord record) {
            this.record = record;
        }

        @Override
        public void flush() {

        }

        @Override
        public void close() throws SecurityException {

        }
    }

    private static class CountingCompletionHandler implements CompletionHandler {

        final AtomicInteger numCompleted = new AtomicInteger(0);
        final AtomicInteger numFailed = new AtomicInteger(0);

        @Override
        public void completed() {
            numCompleted.incrementAndGet();
        }

        @Override
        public void failed(Throwable t) {
            numFailed.incrementAndGet();
        }
    }
}
