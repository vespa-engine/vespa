// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestDeniedException;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.jdisc.test.ClientTestDriver;
import com.yahoo.messagebus.shared.ClientSession;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleReply;
import org.junit.Test;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class MbusClientTestCase {

    @Test
    public void requireThatClientRetainsSession() {
        MySession session = new MySession();
        assertEquals(1, session.refCount);
        MbusClient client = new MbusClient(session);
        assertEquals(2, session.refCount);
        session.release();
        assertEquals(1, session.refCount);
        client.destroy();
        assertEquals(0, session.refCount);
    }

    @Test
    public void requireThatRequestResponseWorks() {
        ClientTestDriver driver = ClientTestDriver.newInstance();
        MyResponseHandler responseHandler = MyResponseHandler.newInstance();
        assertTrue(driver.sendMessage(new SimpleMessage("foo"), responseHandler));
        assertTrue(driver.awaitMessageAndSendReply(new EmptyReply()));

        Response response = responseHandler.awaitResponse();
        assertNotNull(response);
        assertEquals(Response.Status.OK, response.getStatus());
        assertTrue(driver.close());
    }

    @Test
    public void requireThatNonMbusRequestIsDenied() throws InterruptedException {
        ClientTestDriver driver = ClientTestDriver.newInstance();
        Request serverReq = null;
        Request clientReq = null;
        try {
            serverReq = driver.newServerRequest();
            clientReq = new Request(serverReq, URI.create("mbus://host/path"));
            clientReq.connect(MyResponseHandler.newInstance());
            fail();
        } catch (RequestDeniedException e) {
            System.out.println(e.getMessage());
        } finally {
            if (serverReq != null) {
                serverReq.release();
            }
            if (clientReq != null) {
                clientReq.release();
            }
        }
        assertTrue(driver.close());
    }

    @Test
    public void requireThatRequestContentDoesNotSupportWrite() throws InterruptedException {
        ClientTestDriver driver = ClientTestDriver.newInstance();
        MyResponseHandler responseHandler = MyResponseHandler.newInstance();

        Request request = null;
        ContentChannel content;
        try {
            request = driver.newClientRequest(new SimpleMessage("foo"));
            content = request.connect(responseHandler);
        } finally {
            if (request != null) {
                request.release();
            }
        }
        try {
            content.write(ByteBuffer.allocate(69), null);
            fail();
        } catch (UnsupportedOperationException e) {

        }
        content.close(null);

        assertTrue(driver.awaitMessageAndSendReply(new EmptyReply()));
        assertNotNull(responseHandler.awaitResponse());
        assertTrue(driver.close());
    }

    @Test
    public void requireThatResponseIsMbus() {
        ClientTestDriver driver = ClientTestDriver.newInstance();
        MyResponseHandler responseHandler = MyResponseHandler.newInstance();
        assertTrue(driver.sendMessage(new SimpleMessage("foo"), responseHandler));
        assertTrue(driver.awaitMessageAndSendReply(new EmptyReply()));

        Response response = responseHandler.awaitResponse();
        assertTrue(response instanceof MbusResponse);
        Reply reply = ((MbusResponse)response).getReply();
        assertTrue(reply instanceof EmptyReply);
        assertTrue(driver.close());
    }

    @Test
    public void requireThatServerReceivesGivenMessage() {
        ClientTestDriver driver = ClientTestDriver.newInstance();
        MyResponseHandler responseHandler = MyResponseHandler.newInstance();
        assertTrue(driver.sendMessage(new SimpleMessage("foo"), responseHandler));

        Message msg = driver.awaitMessage();
        assertTrue(msg instanceof SimpleMessage);
        assertEquals("foo", ((SimpleMessage)msg).getValue());

        Reply reply = new EmptyReply();
        reply.swapState(msg);
        driver.sendReply(reply);

        assertNotNull(responseHandler.awaitResponse());
        assertTrue(driver.close());
    }

    @Test
    public void requireThatClientReceivesGivenReply() {
        ClientTestDriver driver = ClientTestDriver.newInstance();
        MyResponseHandler responseHandler = MyResponseHandler.newInstance();
        assertTrue(driver.sendMessage(new SimpleMessage("foo"), responseHandler));

        Message msg = driver.awaitMessage(); // TODO: Timing sensitive
        assertNotNull(msg);
        Reply reply = new SimpleReply("bar");
        reply.swapState(msg);
        driver.sendReply(reply);

        Response response = responseHandler.awaitResponse();
        assertTrue(response instanceof MbusResponse);
        reply = ((MbusResponse)response).getReply();
        assertTrue(reply instanceof SimpleReply);
        assertEquals("bar", ((SimpleReply)reply).getValue());
        assertTrue(driver.close());
    }

    @Test
    public void requireThatStateIsTransferredToResponse() {
        ClientTestDriver driver = ClientTestDriver.newInstance();
        MyResponseHandler responseHandler = MyResponseHandler.newInstance();

        Message msg = new SimpleMessage("foo");
        Object pushedCtx = new Object();
        msg.setContext(pushedCtx);
        ReplyHandler pushedHandler = new MyReplyHandler();
        msg.pushHandler(pushedHandler);
        Object currentCtx = new Object();
        msg.setContext(currentCtx);
        msg.getTrace().setLevel(6);
        assertTrue(driver.sendMessage(msg, responseHandler));
        assertTrue(driver.awaitMessageAndSendReply(new EmptyReply()));

        Response response = responseHandler.awaitResponse();
        assertTrue(response.getClass().getName(), response instanceof MbusResponse);
        Reply reply = ((MbusResponse)response).getReply();
        assertSame(currentCtx, reply.getContext());
        assertEquals(6, reply.getTrace().getLevel());
        assertSame(pushedHandler, reply.popHandler());
        assertSame(pushedCtx, reply.getContext());
        assertTrue(driver.close());
    }

    @Test
    public void requireThatStateIsTransferredToSyncMbusSendFailureResponse() {
        ClientTestDriver driver = ClientTestDriver.newInstance();
        driver.sourceSession().close();

        Message msg = new SimpleMessage("foo");
        ReplyHandler pushedHandler = new MyReplyHandler();
        Object pushedCtx = new Object();
        msg.setContext(pushedCtx);
        msg.pushHandler(pushedHandler);
        Object currentCtx = new Object();
        msg.setContext(currentCtx);
        msg.getTrace().setLevel(6);

        MyResponseHandler responseHandler = MyResponseHandler.newInstance();
        driver.sendMessage(msg, responseHandler);

        Response response = responseHandler.awaitResponse();
        assertNotNull(response);
        assertTrue(response.getClass().getName(), response instanceof MbusResponse);
        Reply reply = ((MbusResponse)response).getReply();
        assertSame(currentCtx, reply.getContext());
        assertEquals(6, reply.getTrace().getLevel());
        assertSame(pushedHandler, reply.popHandler());
        assertSame(pushedCtx, reply.getContext());
        assertTrue(driver.close());
    }

    @Test
    public void requireThatStateIsTransferredToTimeoutResponse() throws InterruptedException {
        ClientTestDriver driver = ClientTestDriver.newInstance();
        MyResponseHandler responseHandler = MyResponseHandler.newInstance();

        Message msg = new SimpleMessage("foo");
        ReplyHandler pushedHandler = new MyReplyHandler();
        Object pushedCtx = new Object();
        msg.setContext(pushedCtx);
        msg.pushHandler(pushedHandler);
        Object currentCtx = new Object();
        msg.setContext(currentCtx);
        msg.getTrace().setLevel(6);

        Request request = driver.newClientRequest(msg);
        request.setTimeout(1, TimeUnit.MILLISECONDS);
        assertTrue(driver.sendRequest(request, responseHandler));
        request.release();

        Response response = responseHandler.awaitResponse();
        assertNotNull(response);
        assertTrue(response.getClass().getName(), response instanceof MbusResponse);
        Reply reply = ((MbusResponse)response).getReply();
        assertSame(currentCtx, reply.getContext());
        assertEquals(6, reply.getTrace().getLevel());
        assertSame(pushedHandler, reply.popHandler());
        assertSame(pushedCtx, reply.getContext());
        assertTrue(driver.close());
    }

    @Test
    public void requireThatSyncMbusSendFailureRespondsWithError() {
        ClientTestDriver driver = ClientTestDriver.newInstance();
        driver.sourceSession().close();

        MyResponseHandler responseHandler = MyResponseHandler.newInstance();
        driver.sendMessage(new SimpleMessage("foo"), responseHandler);
        Response response = responseHandler.awaitResponse();
        assertNotNull(response);
        assertTrue(response.getClass().getName(), response instanceof MbusResponse);
        Reply reply = ((MbusResponse)response).getReply();
        assertEquals(1, reply.getNumErrors());
        assertEquals(ErrorCode.SEND_QUEUE_CLOSED, reply.getError(0).getCode());
        assertTrue(driver.close());
    }

    private static class MyResponseHandler implements ResponseHandler {

        final MyResponseContent content;
        Response response;

        MyResponseHandler(MyResponseContent content) {
            this.content = content;
        }

        Response awaitResponse() {
            try {
                content.closeLatch.await(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            if (response instanceof MbusResponse) {
                //System.out.println(((MbusResponse)response).getReply().getTrace());
            }
            return response;
        }

        @Override
        public ContentChannel handleResponse(Response response) {
            this.response = response;
            return content;
        }

        static MyResponseHandler newInstance() {
            return new MyResponseHandler(new MyResponseContent());
        }
    }

    private static class MyResponseContent implements ContentChannel {

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

    private static class MySession implements ClientSession {

        int refCount = 1;

        @Override
        public Result sendMessage(Message msg) {
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

    private static class MyReplyHandler implements ReplyHandler {

        @Override
        public void handleReply(Reply reply) {

        }
    }
}
