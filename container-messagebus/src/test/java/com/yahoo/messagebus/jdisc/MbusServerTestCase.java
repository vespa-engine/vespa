// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc;

import com.google.inject.AbstractModule;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.BindingSetSelector;
import com.yahoo.jdisc.handler.*;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.jdisc.test.ServerTestDriver;
import com.yahoo.messagebus.shared.ServerSession;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleReply;
import org.junit.Test;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class MbusServerTestCase {

    @Test
    public void requireThatServerRetainsSession() {
        MySession session = new MySession();
        assertEquals(1, session.refCount);
        MbusServer server = new MbusServer(null, session);
        assertEquals(2, session.refCount);
        session.release();
        assertEquals(1, session.refCount);
        server.destroy();
        assertEquals(0, session.refCount);
    }

    @Test
    public void requireThatNoBindingSetSelectedExceptionIsCaught() {
        ServerTestDriver driver = ServerTestDriver.newUnboundInstance(true, new MySelector(null));
        assertTrue(driver.sendMessage(new SimpleMessage("foo")));
        assertNotNull(driver.awaitErrors(ErrorCode.APP_FATAL_ERROR));
        assertTrue(driver.close());
    }

    @Test
    public void requireThatBindingSetNotFoundExceptionIsCaught() {
        ServerTestDriver driver = ServerTestDriver.newUnboundInstance(true, new MySelector("foo"));
        assertTrue(driver.sendMessage(new SimpleMessage("bar")));
        assertNotNull(driver.awaitErrors(ErrorCode.APP_FATAL_ERROR));
        assertTrue(driver.close());
    }

    @Test
    public void requireThatContainerNotReadyExceptionIsCaught() {
        ServerTestDriver driver = ServerTestDriver.newInactiveInstance(true);
        assertTrue(driver.sendMessage(new SimpleMessage("foo")));
        assertNotNull(driver.awaitErrors(ErrorCode.APP_FATAL_ERROR));
        assertTrue(driver.close());
    }

    @Test
    public void requireThatBindingNotFoundExceptionIsCaught() {
        ServerTestDriver driver = ServerTestDriver.newUnboundInstance(true);
        assertTrue(driver.sendMessage(new SimpleMessage("foo")));
        assertNotNull(driver.awaitErrors(ErrorCode.APP_FATAL_ERROR));
        assertTrue(driver.close());
    }

    @Test
    public void requireThatRequestDeniedExceptionIsCaught() {
        ServerTestDriver driver = ServerTestDriver.newInstance(MyRequestHandler.newRequestDenied(), true);
        assertTrue(driver.sendMessage(new SimpleMessage("foo")));
        assertNotNull(driver.awaitErrors(ErrorCode.APP_FATAL_ERROR));
        assertTrue(driver.close());
    }

    @Test
    public void requireThatRequestResponseWorks() {
        MyRequestHandler requestHandler = MyRequestHandler.newInstance();
        ServerTestDriver driver = ServerTestDriver.newInstance(requestHandler, true);
        assertTrue(driver.sendMessage(new SimpleMessage("foo")));

        assertNotNull(requestHandler.awaitRequest());
        assertTrue(requestHandler.sendResponse(new Response(Response.Status.OK)));

        assertNotNull(driver.awaitSuccess());
        assertTrue(driver.close());
    }

    @Test
    public void requireThatRequestIsMbus() {
        MyRequestHandler requestHandler = MyRequestHandler.newInstance();
        ServerTestDriver driver = ServerTestDriver.newInstance(requestHandler, true);
        assertTrue(driver.sendMessage(new SimpleMessage("foo")));

        Request request = requestHandler.awaitRequest();
        assertTrue(request instanceof MbusRequest);
        Message msg = ((MbusRequest)request).getMessage();
        assertTrue(msg instanceof SimpleMessage);
        assertEquals("foo", ((SimpleMessage)msg).getValue());
        assertTrue(requestHandler.sendResponse(new Response(Response.Status.OK)));

        assertNotNull(driver.awaitSuccess());
        assertTrue(driver.close());
    }

    @Test
    public void requireThatReplyInsideMbusResponseIsUsed() {
        MyRequestHandler requestHandler = MyRequestHandler.newInstance();
        ServerTestDriver driver = ServerTestDriver.newInstance(requestHandler, true);
        assertTrue(driver.sendMessage(new SimpleMessage("foo")));

        assertNotNull(requestHandler.awaitRequest());
        Reply reply = new SimpleReply("bar");
        reply.swapState(((MbusRequest)requestHandler.request).getMessage());
        assertTrue(requestHandler.sendResponse(new MbusResponse(Response.Status.OK, reply)));

        reply = driver.awaitSuccess();
        assertTrue(reply instanceof SimpleReply);
        assertEquals("bar", ((SimpleReply)reply).getValue());

        assertTrue(driver.close());
    }

    @Test
    public void requireThatNonMbusResponseCausesEmptyReply() {
        MyRequestHandler requestHandler = MyRequestHandler.newInstance();
        ServerTestDriver driver = ServerTestDriver.newInstance(requestHandler, true);
        assertTrue(driver.sendMessage(new SimpleMessage("foo")));

        assertNotNull(requestHandler.awaitRequest());
        assertTrue(requestHandler.sendResponse(new Response(Response.Status.OK)));

        assertNotNull(driver.awaitSuccess());
        assertTrue(driver.close());
    }

    @Test
    public void requireThatMbusRequestContentCallsCompletion() throws InterruptedException {
        MyRequestHandler requestHandler = MyRequestHandler.newInstance();
        ServerTestDriver driver = ServerTestDriver.newInstance(requestHandler, true);
        assertTrue(driver.sendMessage(new SimpleMessage("foo")));

        assertNotNull(requestHandler.awaitRequest());
        ContentChannel content = requestHandler.responseHandler.handleResponse(new Response(Response.Status.OK));
        assertNotNull(content);
        MyCompletion completion = new MyCompletion();
        content.close(completion);
        assertTrue(completion.completedLatch.await(60, TimeUnit.SECONDS));

        assertNotNull(driver.awaitSuccess());
        assertTrue(driver.close());
    }

    @Test
    public void requireThatResponseContentDoesNotSupportWrite() {
        MyRequestHandler requestHandler = MyRequestHandler.newInstance();
        ServerTestDriver driver = ServerTestDriver.newInstance(requestHandler, true);
        assertTrue(driver.sendMessage(new SimpleMessage("foo")));

        assertNotNull(requestHandler.awaitRequest());
        ContentChannel content = requestHandler.responseHandler.handleResponse(new Response(Response.Status.OK));
        assertNotNull(content);
        try {
            content.write(ByteBuffer.allocate(69), null);
            fail();
        } catch (UnsupportedOperationException e) {

        }
        content.close(null);

        assertNotNull(driver.awaitSuccess());
        assertTrue(driver.close());
    }

    @Test
    public void requireThatResponseErrorCodeDoesNotDuplicateReplyError() {
        assertError(Collections.<Integer>emptyList(),
                    Response.Status.OK);
        assertError(Arrays.asList(ErrorCode.APP_FATAL_ERROR),
                    Response.Status.BAD_REQUEST);
        assertError(Arrays.asList(ErrorCode.FATAL_ERROR),
                    Response.Status.BAD_REQUEST, ErrorCode.FATAL_ERROR);
        assertError(Arrays.asList(ErrorCode.TRANSIENT_ERROR, ErrorCode.APP_FATAL_ERROR),
                    Response.Status.BAD_REQUEST, ErrorCode.TRANSIENT_ERROR);
        assertError(Arrays.asList(ErrorCode.FATAL_ERROR, ErrorCode.TRANSIENT_ERROR),
                    Response.Status.BAD_REQUEST, ErrorCode.FATAL_ERROR, ErrorCode.TRANSIENT_ERROR);
    }

    private static void assertError(List<Integer> expectedErrors, int responseStatus, int... responseErrors) {
        MyRequestHandler requestHandler = MyRequestHandler.newInstance();
        ServerTestDriver driver = ServerTestDriver.newInstance(requestHandler, true);
        assertTrue(driver.sendMessage(new SimpleMessage("foo")));

        assertNotNull(requestHandler.awaitRequest());
        Reply reply = new SimpleReply("bar");
        reply.swapState(((MbusRequest)requestHandler.request).getMessage());
        for (int err : responseErrors) {
            reply.addError(new Error(err, "err"));
        }
        assertTrue(requestHandler.sendResponse(new MbusResponse(responseStatus, reply)));

        assertNotNull(reply = driver.awaitReply());
        List<Integer> actual = new LinkedList<>();
        for (int i = 0; i < reply.getNumErrors(); ++i) {
            actual.add(reply.getError(i).getCode());
        }
        assertEquals(expectedErrors, actual);
        assertTrue(driver.close());
    }

    private static class MySelector extends AbstractModule implements BindingSetSelector {

        final String bindingSet;

        MySelector(String bindingSet) {
            this.bindingSet = bindingSet;
        }

        @Override
        protected void configure() {
            bind(BindingSetSelector.class).toInstance(this);
        }

        @Override
        public String select(URI uri) {
            return bindingSet;
        }
    }

    private static class MyRequestHandler extends AbstractRequestHandler {

        final MyRequestContent content;
        Request request;
        ResponseHandler responseHandler;

        MyRequestHandler(MyRequestContent content) {
            this.content = content;
        }

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler responseHandler) {
            this.request = request;
            this.responseHandler = responseHandler;
            if (content == null) {
                throw new RequestDeniedException(request);
            }
            return content;
        }

        Request awaitRequest() {
            try {
                if (!content.closeLatch.await(60, TimeUnit.SECONDS)) {
                    return null;
                }
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            if (request instanceof MbusRequest) {
                ((MbusRequest)request).getMessage().getTrace().trace(0, "Request received by DISC.");
            }
            return request;
        }

        boolean sendResponse(Response response) {
            ContentChannel content = responseHandler.handleResponse(response);
            if (content == null) {
                return false;
            }
            content.close(null);
            return true;
        }

        static MyRequestHandler newInstance() {
            return new MyRequestHandler(new MyRequestContent());
        }

        static MyRequestHandler newRequestDenied() {
            return new MyRequestHandler(null);
        }
    }

    private static class MyRequestContent implements ContentChannel {

        final CountDownLatch writeLatch = new CountDownLatch(1);
        final CountDownLatch closeLatch = new CountDownLatch(1);

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            if (handler != null) {
                handler.completed();
            }
            writeLatch.countDown();
        }

        @Override
        public void close(CompletionHandler handler) {
            if (handler != null) {
                handler.completed();
            }
            closeLatch.countDown();
        }
    }

    private static class MyCompletion implements CompletionHandler {

        final CountDownLatch completedLatch = new CountDownLatch(1);

        @Override
        public void completed() {
            completedLatch.countDown();
        }

        @Override
        public void failed(Throwable t) {

        }
    }

    private static class MySession implements ServerSession {

        int refCount = 1;

        @Override
        public void sendReply(Reply reply) {

        }

        @Override
        public MessageHandler getMessageHandler() {
            return null;
        }

        @Override
        public void setMessageHandler(MessageHandler msgHandler) {

        }

        @Override
        public String connectionSpec() {
            return null;
        }

        @Override
        public String name() {
            return null;
        }

        @Override
        public ResourceReference refer() {
            ++refCount;
            return new ResourceReference() {
                @Override
                public void close() {
                    --refCount;
                }
            };
        }

        @Override
        public void release() {
            --refCount;
        }
    }
}
