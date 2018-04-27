// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.network.rpc.test.TestServer;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.test.Receptor;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleReply;
import org.junit.Test;

import java.net.UnknownHostException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class RoutableTestCase {

    private final double delta = 0.00000001;

    @Test
    public void testMessageContext() throws ListenFailedException {
        Slobrok slobrok = new Slobrok();
        TestServer srcServer = new TestServer("src", null, slobrok, null);
        TestServer dstServer = new TestServer("dst", null, slobrok, null);
        SourceSession srcSession = srcServer.mb.createSourceSession(
                new Receptor(),
                new SourceSessionParams().setTimeout(600.0));
        DestinationSession dstSession = dstServer.mb.createDestinationSession("session", true, new Receptor());

        assertTrue(srcServer.waitSlobrok("dst/session", 1));

        Object context = new Object();
        Message msg = new SimpleMessage("msg");
        msg.setContext(context);
        assertTrue(srcSession.send(msg, "dst/session", true).isAccepted());

        assertNotNull(msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60));
        dstSession.acknowledge(msg);

        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        assertSame(reply.getContext(), context);

        srcSession.destroy();
        srcServer.destroy();
        dstSession.destroy();
        dstServer.destroy();
        slobrok.stop();
    }

    @Test
    public void testMessageSwapState() {
        Message foo = new SimpleMessage("foo");
        Route fooRoute = Route.parse("foo");
        foo.setRoute(fooRoute);
        foo.setRetry(1);
        foo.setTimeReceivedNow();
        foo.setTimeRemaining(2);

        Message bar = new SimpleMessage("bar");
        Route barRoute = Route.parse("bar");
        bar.setRoute(barRoute);
        bar.setRetry(3);
        bar.setTimeReceivedNow();
        bar.setTimeRemaining(4);

        foo.swapState(bar);
        assertEquals(barRoute, foo.getRoute());
        assertEquals(fooRoute, bar.getRoute());
        assertEquals(3, foo.getRetry());
        assertEquals(1, bar.getRetry());
        assertTrue(foo.getTimeReceived() >= bar.getTimeReceived());
        assertEquals(4, foo.getTimeRemaining());
        assertEquals(2, bar.getTimeRemaining());
    }

    @Test
    public void testReplySwapState() {
        Reply foo = new SimpleReply("foo");
        Message fooMsg = new SimpleMessage("foo");
        foo.setMessage(fooMsg);
        foo.setRetryDelay(1);
        foo.addError(new Error(ErrorCode.APP_FATAL_ERROR, "fatal"));
        foo.addError(new Error(ErrorCode.APP_TRANSIENT_ERROR, "transient"));

        Reply bar = new SimpleReply("bar");
        Message barMsg = new SimpleMessage("bar");
        bar.setMessage(barMsg);
        bar.setRetryDelay(2);
        bar.addError(new Error(ErrorCode.ERROR_LIMIT, "err"));

        foo.swapState(bar);
        assertEquals(barMsg, foo.getMessage());
        assertEquals(fooMsg, bar.getMessage());
        assertEquals(2.0, foo.getRetryDelay(), delta);
        assertEquals(1.0, bar.getRetryDelay(), delta);
        assertEquals(1, foo.getNumErrors());
        assertEquals(2, bar.getNumErrors());
    }

    @Test
    public void testMessageDiscard() {
        Receptor handler = new Receptor();
        Message msg = new SimpleMessage("foo");
        msg.pushHandler(handler);
        msg.discard();

        assertNull(handler.getReply(0));
    }

    @Test
    public void testReplyDiscard() {
        Receptor handler = new Receptor();
        Message msg = new SimpleMessage("foo");
        msg.pushHandler(handler);

        Reply reply = new SimpleReply("bar");
        reply.swapState(msg);
        reply.discard();

        assertNull(handler.getReply(0));
    }

}
