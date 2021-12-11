// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.handler.RequestDispatch;
import com.yahoo.jdisc.test.TestDriver;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.test.SimpleMessage;
import org.junit.Test;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class MbusRequestHandlerTestCase {

    @Test
    public void requireThatNonMbusRequestThrows() throws Exception {
        final TestDriver driver = newTestDriver(SameThreadReplier.INSTANCE);
        try {
            new RequestDispatch() {

                @Override
                protected Request newRequest() {
                    return new Request(driver, URI.create("mbus://localhost/"));
                }
            }.connect();
            fail();
        } catch (UnsupportedOperationException e) {
            assertEquals("Expected MbusRequest, got com.yahoo.jdisc.Request.", e.getMessage());
        }
        assertTrue(driver.close());
    }

    @Test
    public void requireThatHandlerCanRespondInSameThread() throws Exception {
        TestDriver driver = newTestDriver(SameThreadReplier.INSTANCE);

        Response response = dispatchMessage(driver, new SimpleMessage("msg")).get(60, TimeUnit.SECONDS);
        assertTrue(response instanceof MbusResponse);
        assertEquals(Response.Status.OK, response.getStatus());
        Reply reply = ((MbusResponse)response).getReply();
        assertTrue(reply instanceof EmptyReply);
        assertFalse(reply.hasErrors());

        assertTrue(driver.close());
    }

    @Test
    public void requireThatHandlerCanRespondInOtherThread() throws Exception {
        TestDriver driver = newTestDriver(ThreadedReplier.INSTANCE);

        Response response = dispatchMessage(driver, new SimpleMessage("msg")).get(60, TimeUnit.SECONDS);
        assertTrue(response instanceof MbusResponse);
        assertEquals(Response.Status.OK, response.getStatus());
        Reply reply = ((MbusResponse)response).getReply();
        assertTrue(reply instanceof EmptyReply);
        assertFalse(reply.hasErrors());

        assertTrue(driver.close());
    }

    private static TestDriver newTestDriver(MbusRequestHandler handler) {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.serverBindings().bind("mbus://*/*", handler);
        driver.activateContainer(builder);
        return driver;
    }

    private static CompletableFuture<Response> dispatchMessage(final TestDriver driver, final Message msg) {
        return new RequestDispatch() {

            @Override
            protected Request newRequest() {
                return new MbusRequest(driver, URI.create("mbus://localhost/"), msg);
            }
        }.dispatch();
    }

    private static class SameThreadReplier extends MbusRequestHandler {

        final static SameThreadReplier INSTANCE = new SameThreadReplier();

        @Override
        public void handleMessage(Message msg) {
            Reply reply = new EmptyReply();
            reply.swapState(msg);
            reply.popHandler().handleReply(reply);
        }
    }

    private static class ThreadedReplier extends MbusRequestHandler {

        final static ThreadedReplier INSTANCE = new ThreadedReplier();

        @Override
        public void handleMessage(final Message msg) {
            Executors.newSingleThreadExecutor().execute(new Runnable() {

                @Override
                public void run() {
                    SameThreadReplier.INSTANCE.handleMessage(msg);
                }
            });
        }
    }
}
