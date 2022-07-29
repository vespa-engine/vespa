// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class ContainerShutdownTestCase {

    @Test
    void requireThatContainerBlocksTermination() {
        Context ctx = Context.newInstance();
        Container container = ctx.driver.newReference(URI.create("http://host/path"));
        assertFalse(ctx.shutdown());
        container.release();
        assertTrue(ctx.terminated);
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatNewRequestBlocksTermination() {
        Context ctx = Context.newPendingRequest(MyRequestHandler.newInstance());
        assertFalse(ctx.shutdown());
        ctx.request.release();
        assertTrue(ctx.terminated);
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatOpenRequestBlocksTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newEagerCompletion();
        Context ctx = Context.newPendingRequest(requestHandler);
        ContentChannel requestContent = ctx.request.connect(MyResponseHandler.newEagerCompletion());
        ctx.request.release();
        requestHandler.respond().close(null);
        assertFalse(ctx.shutdown());
        requestContent.close(null);
        assertTrue(ctx.terminated);
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatResponsePendingBlocksTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newEagerCompletion();
        Context ctx = Context.newPendingRequest(requestHandler);
        ctx.request.connect(MyResponseHandler.newEagerCompletion()).close(null);
        ctx.request.release();
        assertFalse(ctx.shutdown());
        requestHandler.respond().close(null);
        assertTrue(ctx.terminated);
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatOpenResponseBlocksTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newEagerCompletion();
        Context ctx = Context.newPendingRequest(requestHandler);
        ctx.request.connect(MyResponseHandler.newEagerCompletion()).close(null);
        ctx.request.release();
        ContentChannel responseContent = requestHandler.respond();
        assertFalse(ctx.shutdown());
        responseContent.close(null);
        assertTrue(ctx.terminated);
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatRequestExceptionDoesNotBlockTermination() {
        Context ctx = Context.newPendingRequest(MyRequestHandler.newRequestException());
        try {
            ctx.request.connect(MyResponseHandler.newEagerCompletion());
            fail();
        } catch (MyException e) {
            // ignore
        }
        ctx.request.release();
        assertTrue(ctx.shutdown());
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatRequestExceptionWithEagerHandleResponseBlocksTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newRequestExceptionWithEagerHandleResponse();
        Context ctx = Context.newPendingRequest(requestHandler);
        try {
            ctx.request.connect(MyResponseHandler.newEagerCompletion());
            fail();
        } catch (MyException e) {
            // ignore
        }
        ctx.request.release();
        assertFalse(ctx.shutdown());
        requestHandler.responseContent.close(null);
        assertTrue(ctx.terminated);
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatRequestExceptionWithEagerCloseResponseDoesNotBlockTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newRequestExceptionWithEagerCloseResponse();
        Context ctx = Context.newPendingRequest(requestHandler);
        try {
            ctx.request.connect(MyResponseHandler.newEagerCompletion());
            fail();
        } catch (MyException e) {
            // ignore
        }
        ctx.request.release();
        assertTrue(ctx.shutdown());
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatNullRequestContentBlocksTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newNullContent();
        Context ctx = Context.newPendingRequest(requestHandler);
        ctx.request.connect(MyResponseHandler.newEagerCompletion()).close(null);
        ctx.request.release();
        assertFalse(ctx.shutdown());
        requestHandler.respond();
        requestHandler.responseContent.close(null);
        assertTrue(ctx.terminated);
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatNullRequestContentWithEagerHandleResponseBlocksTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newNullContentWithEagerHandleResponse();
        Context ctx = Context.newPendingRequest(requestHandler);
        ctx.request.connect(MyResponseHandler.newEagerCompletion()).close(null);
        ctx.request.release();
        assertFalse(ctx.shutdown());
        requestHandler.responseContent.close(null);
        assertTrue(ctx.terminated);
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatNullRequestContentWithEagerCloseResponseBlocksTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newNulContentWithEagerCloseResponse();
        Context ctx = Context.newPendingRequest(requestHandler);
        ContentChannel requestContent = ctx.request.connect(MyResponseHandler.newEagerCompletion());
        ctx.request.release();
        assertFalse(ctx.shutdown());
        requestContent.close(null);
        assertTrue(ctx.terminated);
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatRequestContentWriteFailedDoesNotBlockTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newEagerFail();
        Context ctx = Context.newPendingRequest(requestHandler);
        ContentChannel requestContent = ctx.request.connect(MyResponseHandler.newEagerCompletion());
        requestContent.write(ByteBuffer.allocate(69), null);
        requestContent.close(null);
        ctx.request.release();
        requestHandler.respond().close(null);
        assertTrue(ctx.shutdown());
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatRequestContentWriteExceptionDoesNotBlockTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newContentWriteExceptionWithEagerCompletion();
        Context ctx = Context.newPendingRequest(requestHandler);
        ContentChannel requestContent = ctx.request.connect(MyResponseHandler.newEagerCompletion());
        try {
            requestContent.write(ByteBuffer.allocate(69), null);
            fail();
        } catch (MyException e) {
            // ignore
        }
        requestContent.close(null);
        ctx.request.release();
        requestHandler.respond().close(null);
        assertTrue(ctx.shutdown());
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatRequestContentWriteExceptionDoesNotForceTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newContentWriteExceptionWithEagerCompletion();
        Context ctx = Context.newPendingRequest(requestHandler);
        ContentChannel requestContent = ctx.request.connect(MyResponseHandler.newEagerCompletion());
        try {
            requestContent.write(ByteBuffer.allocate(69), null);
            fail();
        } catch (MyException e) {
            // ignore
        }
        ctx.request.release();
        requestHandler.respond().close(null);
        assertFalse(ctx.shutdown());
        requestContent.close(null);
        assertTrue(ctx.terminated);
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatRequestContentWriteExceptionWithCompletionDoesNotBlockTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newContentWriteExceptionWithEagerCompletion();
        Context ctx = Context.newPendingRequest(requestHandler);
        ContentChannel requestContent = ctx.request.connect(MyResponseHandler.newEagerCompletion());
        try {
            requestContent.write(ByteBuffer.allocate(69), MyCompletion.newInstance());
            fail();
        } catch (MyException e) {
            // ignore
        }
        requestContent.close(null);
        ctx.request.release();
        requestHandler.respond().close(null);
        assertTrue(ctx.shutdown());
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatRequestContentCloseFailedDoesNotBlockTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newEagerFail();
        Context ctx = Context.newPendingRequest(requestHandler);
        ContentChannel requestContent = ctx.request.connect(MyResponseHandler.newEagerCompletion());
        requestContent.close(null);
        ctx.request.release();
        requestHandler.respond().close(null);
        assertTrue(ctx.shutdown());
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatRequestContentCloseExceptionDoesNotBlockTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newContentCloseException();
        Context ctx = Context.newPendingRequest(requestHandler);
        ContentChannel requestContent = ctx.request.connect(MyResponseHandler.newEagerCompletion());
        try {
            requestContent.close(null);
            fail();
        } catch (MyException e) {
            // ignore
        }
        ctx.request.release();
        requestHandler.respond().close(null);
        assertTrue(ctx.shutdown());
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatRequestContentCloseExceptionWithCompletionDoesNotBlockTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newContentCloseException();
        Context ctx = Context.newPendingRequest(requestHandler);
        ContentChannel requestContent = ctx.request.connect(MyResponseHandler.newEagerCompletion());
        try {
            requestContent.close(MyCompletion.newInstance());
            fail();
        } catch (MyException e) {
            // ignore
        }
        ctx.request.release();
        requestHandler.respond().close(null);
        assertTrue(ctx.shutdown());
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatRequestWriteCompletionBlocksTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newEagerCloseResponse();
        Context ctx = Context.newPendingRequest(requestHandler);
        ContentChannel requestContent = ctx.request.connect(MyResponseHandler.newEagerCompletion());
        ctx.request.release();
        requestContent.write(null, MyCompletion.newInstance());
        requestContent.close(null);
        requestHandler.requestContent.closeCompletion.completed();
        assertFalse(ctx.shutdown());
        requestHandler.requestContent.writeCompletion.completed();
        assertTrue(ctx.terminated);
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatRequestWriteCompletionExceptionDoesNotBlockTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newEagerCloseResponse();
        Context ctx = Context.newPendingRequest(requestHandler);
        ContentChannel requestContent = ctx.request.connect(MyResponseHandler.newEagerCompletion());
        ctx.request.release();
        requestContent.write(null, MyCompletion.newException());
        requestContent.close(null);
        requestHandler.requestContent.closeCompletion.completed();
        assertFalse(ctx.shutdown());
        try {
            requestHandler.requestContent.writeCompletion.completed();
            fail();
        } catch (MyException e) {
            // ignore
        }
        assertTrue(ctx.terminated);
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatRequestCloseCompletionBlocksTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newEagerCloseResponse();
        Context ctx = Context.newPendingRequest(requestHandler);
        ContentChannel requestContent = ctx.request.connect(MyResponseHandler.newEagerCompletion());
        ctx.request.release();
        requestContent.close(MyCompletion.newInstance());
        assertFalse(ctx.shutdown());
        requestHandler.requestContent.closeCompletion.completed();
        assertTrue(ctx.terminated);
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatRequestCloseCompletionExceptionDoesNotBlockTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newEagerCloseResponse();
        Context ctx = Context.newPendingRequest(requestHandler);
        ContentChannel requestContent = ctx.request.connect(MyResponseHandler.newEagerCompletion());
        ctx.request.release();
        requestContent.close(MyCompletion.newException());
        assertFalse(ctx.shutdown());
        try {
            requestHandler.requestContent.closeCompletion.completed();
            fail();
        } catch (MyException e) {
            // ignore
        }
        assertTrue(ctx.terminated);
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatNullResponseContentBlocksTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newEagerRespondWithEagerCompletion();
        Context ctx = Context.newPendingRequest(requestHandler);
        MyResponseHandler responseHandler = MyResponseHandler.newNullContent();
        ctx.request.connect(responseHandler).close(null);
        ctx.request.release();

        assertFalse(ctx.shutdown());
        requestHandler.responseContent.close(MyCompletion.newInstance());
        assertTrue(ctx.terminated);
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatResponseExceptionDoesNotBlockTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newEagerCompletion();
        Context ctx = Context.newPendingRequest(requestHandler);
        ctx.request.connect(MyResponseHandler.newResponseException()).close(null);
        ctx.request.release();
        try {
            requestHandler.respond();
            fail();
        } catch (MyException e) {
            // ignore
        }
        assertTrue(ctx.shutdown());
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatResponseContentWriteFailedDoesNotBlockTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newEagerCompletion();
        Context ctx = Context.newPendingRequest(requestHandler);
        MyResponseHandler responseHandler = MyResponseHandler.newEagerFail();
        ctx.request.connect(responseHandler).close(null);
        ctx.request.release();
        requestHandler.respond();
        requestHandler.responseContent.write(ByteBuffer.allocate(69), null);
        requestHandler.responseContent.close(null);
        assertTrue(ctx.shutdown());
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatResponseContentCloseFailedDoesNotBlockTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newEagerCompletion();
        Context ctx = Context.newPendingRequest(requestHandler);
        MyResponseHandler responseHandler = MyResponseHandler.newEagerFail();
        ctx.request.connect(responseHandler).close(null);
        ctx.request.release();
        requestHandler.respond();
        requestHandler.responseContent.close(null);
        assertTrue(ctx.shutdown());
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatResponseContentWriteExceptionDoesNotBlockTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newEagerCompletion();
        Context ctx = Context.newPendingRequest(requestHandler);
        MyResponseHandler responseHandler = MyResponseHandler.newContentWriteException();
        ctx.request.connect(responseHandler).close(null);
        ctx.request.release();
        requestHandler.respond();
        try {
            requestHandler.responseContent.write(ByteBuffer.allocate(69), null);
            fail();
        } catch (MyException e) {
            // ignore
        }
        requestHandler.responseContent.close(null);
        responseHandler.content.closeCompletion.completed();
        assertTrue(ctx.shutdown());
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatResponseContentWriteExceptionDoesNotForceTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newEagerCompletion();
        Context ctx = Context.newPendingRequest(requestHandler);
        MyResponseHandler responseHandler = MyResponseHandler.newContentWriteException();
        ctx.request.connect(responseHandler).close(null);
        ctx.request.release();
        requestHandler.respond();
        try {
            requestHandler.responseContent.write(ByteBuffer.allocate(69), null);
            fail();
        } catch (MyException e) {
            // ignore
        }
        assertFalse(ctx.shutdown());
        requestHandler.responseContent.close(null);
        responseHandler.content.closeCompletion.completed();
        assertTrue(ctx.terminated);
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatResponseContentWriteExceptionWithCompletionDoesNotBlockTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newEagerCompletion();
        Context ctx = Context.newPendingRequest(requestHandler);
        MyResponseHandler responseHandler = MyResponseHandler.newContentWriteException();
        ctx.request.connect(responseHandler).close(null);
        ctx.request.release();
        requestHandler.respond();
        try {
            requestHandler.responseContent.write(ByteBuffer.allocate(69), MyCompletion.newInstance());
            fail();
        } catch (MyException e) {
            // ignore
        }
        requestHandler.responseContent.close(null);
        responseHandler.content.closeCompletion.completed();
        assertTrue(ctx.shutdown());
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatResponseContentCloseExceptionDoesNotBlockTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newEagerCompletion();
        Context ctx = Context.newPendingRequest(requestHandler);
        ctx.request.connect(MyResponseHandler.newContentCloseException()).close(null);
        ctx.request.release();
        try {
            requestHandler.respond().close(null);
            fail();
        } catch (MyException e) {
            // ignore
        }
        assertTrue(ctx.shutdown());
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatResponseContentCloseExceptionWithCompletionDoesNotBlockTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newEagerCompletion();
        Context ctx = Context.newPendingRequest(requestHandler);
        ctx.request.connect(MyResponseHandler.newContentCloseException()).close(null);
        ctx.request.release();
        try {
            requestHandler.respond().close(MyCompletion.newInstance());
            fail();
        } catch (MyException e) {
            // ignore
        }
        assertTrue(ctx.shutdown());
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatResponseWriteCompletionBlocksTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newEagerRespondWithEagerCompletion();
        Context ctx = Context.newPendingRequest(requestHandler);
        MyResponseHandler responseHandler = MyResponseHandler.newInstance();
        ctx.request.connect(responseHandler).close(null);
        ctx.request.release();

        requestHandler.responseContent.write(null, MyCompletion.newInstance());
        requestHandler.responseContent.close(null);
        responseHandler.content.closeCompletion.completed();
        assertFalse(ctx.shutdown());
        responseHandler.content.writeCompletion.completed();
        assertTrue(ctx.terminated);
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatResponseWriteCompletionExceptionDoesNotBlockTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newEagerRespondWithEagerCompletion();
        Context ctx = Context.newPendingRequest(requestHandler);
        MyResponseHandler responseHandler = MyResponseHandler.newInstance();
        ctx.request.connect(responseHandler).close(null);
        ctx.request.release();

        requestHandler.responseContent.write(null, MyCompletion.newException());
        requestHandler.responseContent.close(null);
        responseHandler.content.closeCompletion.completed();
        assertFalse(ctx.shutdown());
        try {
            responseHandler.content.writeCompletion.completed();
            fail();
        } catch (MyException e) {
            // ignore
        }
        assertTrue(ctx.terminated);
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatResponseCloseCompletionBlocksTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newEagerRespondWithEagerCompletion();
        Context ctx = Context.newPendingRequest(requestHandler);
        MyResponseHandler responseHandler = MyResponseHandler.newInstance();
        ctx.request.connect(responseHandler).close(null);
        ctx.request.release();

        requestHandler.responseContent.close(MyCompletion.newInstance());
        assertFalse(ctx.shutdown());
        responseHandler.content.closeCompletion.completed();
        assertTrue(ctx.terminated);
        assertTrue(ctx.driver.close());
    }

    @Test
    void requireThatResponseCloseCompletionExceptionDoesNotBlockTermination() {
        MyRequestHandler requestHandler = MyRequestHandler.newEagerRespondWithEagerCompletion();
        Context ctx = Context.newPendingRequest(requestHandler);
        MyResponseHandler responseHandler = MyResponseHandler.newInstance();
        ctx.request.connect(responseHandler).close(null);
        ctx.request.release();

        requestHandler.responseContent.close(MyCompletion.newException());
        assertFalse(ctx.shutdown());
        try {
            responseHandler.content.closeCompletion.completed();
            fail();
        } catch (MyException e) {
            // ignore
        }
        assertTrue(ctx.terminated);
        assertTrue(ctx.driver.close());
    }

    private static class Context {

        final TestDriver driver;
        final Request request;
        boolean terminated = false;

        Context(TestDriver driver, Request request) {
            this.driver = driver;
            this.request = request;
        }

        boolean shutdown() {
            driver.activateContainer(null).notifyTermination(new Runnable() {

                @Override
                public void run() {
                    terminated = true;
                }
            });
            return terminated;
        }

        static Context newInstance() {
            TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
            driver.activateContainer(driver.newContainerBuilder());
            return new Context(driver, null);
        }

        static Context newPendingRequest(RequestHandler requestHandler) {
            TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
            ContainerBuilder builder = driver.newContainerBuilder();
            builder.serverBindings().bind("http://host/path", requestHandler);
            driver.activateContainer(builder);
            return new Context(driver, new Request(driver, URI.create("http://host/path")));
        }
    }

    private static class MyCompletion implements CompletionHandler {

        final boolean throwException;

        MyCompletion(boolean throwException) {
            this.throwException = throwException;
        }

        @Override
        public void completed() {
            if (throwException) {
                throw new MyException();
            }
        }

        @Override
        public void failed(Throwable t) {
            if (throwException) {
                throw new MyException();
            }
        }

        static MyCompletion newInstance() {
            return new MyCompletion(false);
        }

        static MyCompletion newException() {
            return new MyCompletion(true);
        }
    }

    private static class MyContent implements ContentChannel {

        final boolean eagerCompletion;
        final boolean eagerFail;
        final boolean writeException;
        final boolean closeException;
        CompletionHandler writeCompletion = null;
        CompletionHandler closeCompletion = null;

        MyContent(boolean eagerCompletion, boolean eagerFail, boolean writeException, boolean closeException) {
            this.eagerCompletion = eagerCompletion;
            this.eagerFail = eagerFail;
            this.writeException = writeException;
            this.closeException = closeException;
        }

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            writeCompletion = handler;
            if (eagerCompletion) {
                writeCompletion.completed();
            } else if (eagerFail) {
                writeCompletion.failed(new MyException());
            }
            if (writeException) {
                throw new MyException();
            }
        }

        @Override
        public void close(CompletionHandler handler) {
            closeCompletion = handler;
            if (eagerCompletion) {
                closeCompletion.completed();
            } else if (eagerFail) {
                closeCompletion.failed(new MyException());
            }
            if (closeException) {
                throw new MyException();
            }
        }

        static MyContent newInstance() {
            return new MyContent(false, false, false, false);
        }

        static MyContent newEagerCompletion() {
            return new MyContent(true, false, false, false);
        }

        static MyContent newEagerFail() {
            return new MyContent(false, true, false, false);
        }

        static MyContent newWriteException() {
            return new MyContent(false, false, true, false);
        }

        static MyContent newWriteExceptionWithEagerCompletion() {
            return new MyContent(true, false, true, false);
        }

        static MyContent newCloseException() {
            return new MyContent(false, false, false, true);
        }
    }

    private static class MyRequestHandler extends AbstractRequestHandler {

        final MyContent requestContent;
        final boolean eagerRespond;
        final boolean closeResponse;
        final boolean throwException;
        ContentChannel responseContent = null;
        ResponseHandler handler = null;

        MyRequestHandler(MyContent requestContent, boolean eagerRespond, boolean closeResponse,
                         boolean throwException)
        {
            this.requestContent = requestContent;
            this.eagerRespond = eagerRespond;
            this.closeResponse = closeResponse;
            this.throwException = throwException;
        }

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            this.handler = handler;
            if (eagerRespond) {
                respond();
            }
            if (throwException) {
                throw new MyException();
            }
            return requestContent;
        }

        ContentChannel respond() {
            responseContent = handler.handleResponse(new Response(Response.Status.OK));
            if (responseContent != null && closeResponse) {
                responseContent.close(null);
            }
            return responseContent;
        }

        static MyRequestHandler newInstance() {
            return new MyRequestHandler(MyContent.newInstance(), false, false, false);
        }

        static MyRequestHandler newEagerCompletion() {
            return new MyRequestHandler(MyContent.newEagerCompletion(), false, false, false);
        }

        static MyRequestHandler newEagerFail() {
            return new MyRequestHandler(MyContent.newEagerFail(), false, false, false);
        }

        static RequestHandler newRequestException() {
            return new MyRequestHandler(null, false, false, true);
        }

        static MyRequestHandler newNullContent() {
            return new MyRequestHandler(null, false, false, false);
        }

        static MyRequestHandler newNullContentWithEagerHandleResponse() {
            return new MyRequestHandler(null, true, false, false);
        }

        static MyRequestHandler newNulContentWithEagerCloseResponse() {
            return new MyRequestHandler(null, true, true, false);
        }

        static MyRequestHandler newRequestExceptionWithEagerHandleResponse() {
            return new MyRequestHandler(null, true, false, true);
        }

        static MyRequestHandler newRequestExceptionWithEagerCloseResponse() {
            return new MyRequestHandler(null, true, true, true);
        }

        static MyRequestHandler newContentWriteExceptionWithEagerCompletion() {
            return new MyRequestHandler(MyContent.newWriteExceptionWithEagerCompletion(), true, true, false);
        }

        static MyRequestHandler newContentCloseException() {
            return new MyRequestHandler(MyContent.newCloseException(), true, true, false);
        }

        static MyRequestHandler newEagerRespondWithEagerCompletion() {
            return new MyRequestHandler(MyContent.newEagerCompletion(), true, false, false);
        }

        static MyRequestHandler newEagerCloseResponse() {
            return new MyRequestHandler(MyContent.newInstance(), true, true, false);
        }
    }

    private static class MyResponseHandler implements ResponseHandler {

        final MyContent content;
        final boolean throwException;

        MyResponseHandler(MyContent content, boolean throwException) {
            this.content = content;
            this.throwException = throwException;
        }

        @Override
        public ContentChannel handleResponse(Response response) {
            if (throwException) {
                throw new MyException();
            }
            return content;
        }

        static MyResponseHandler newInstance() {
            return new MyResponseHandler(MyContent.newInstance(), false);
        }

        static MyResponseHandler newEagerCompletion() {
            return new MyResponseHandler(MyContent.newEagerCompletion(), false);
        }

        static MyResponseHandler newEagerFail() {
            return new MyResponseHandler(MyContent.newEagerFail(), false);
        }

        static MyResponseHandler newNullContent() {
            return new MyResponseHandler(null, false);
        }

        static MyResponseHandler newResponseException() {
            return new MyResponseHandler(null, true);
        }

        static MyResponseHandler newContentWriteException() {
            return new MyResponseHandler(MyContent.newWriteException(), false);
        }

        static MyResponseHandler newContentCloseException() {
            return new MyResponseHandler(MyContent.newCloseException(), false);
        }
    }

    private static final class MyException extends RuntimeException {

    }
}
