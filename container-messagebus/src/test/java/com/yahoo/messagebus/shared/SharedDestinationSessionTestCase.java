// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.shared;

import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.jdisc.test.MessageQueue;
import com.yahoo.messagebus.jdisc.test.RemoteClient;
import com.yahoo.messagebus.jdisc.test.ReplyQueue;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleProtocol;
import com.yahoo.messagebus.test.SimpleReply;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class SharedDestinationSessionTestCase {

    @Test
    public void requireThatMessageHandlerCanBeAccessed() {
        SharedDestinationSession session = newDestinationSession();
        assertNull(session.getMessageHandler());

        MessageQueue handler = new MessageQueue();
        session.setMessageHandler(handler);
        assertSame(handler, session.getMessageHandler());
    }

    @Test
    public void requireThatMessageHandlerCanOnlyBeSetOnce() {
        SharedDestinationSession session = newDestinationSession();
        session.setMessageHandler(new MessageQueue());
        try {
            session.setMessageHandler(new MessageQueue());
            fail();
        } catch (IllegalStateException e) {
            assertEquals("Message handler already registered.", e.getMessage());
        }
        session.release();
    }

    @Test
    public void requireThatMessageHandlerIsCalled() throws InterruptedException {
        SharedDestinationSession session = newDestinationSession();
        MessageQueue queue = new MessageQueue();
        session.setMessageHandler(queue);
        session.handleMessage(new SimpleMessage("foo"));
        assertNotNull(queue.awaitMessage(60, TimeUnit.SECONDS));
        session.release();
    }

    @Test
    public void requireThatSessionRepliesIfMessageHandlerIsNull() throws InterruptedException {
        SharedDestinationSession session = newDestinationSession();
        Message msg = new SimpleMessage("foo");
        ReplyQueue queue = new ReplyQueue();
        msg.pushHandler(queue);
        session.handleMessage(msg);
        Reply reply = queue.awaitReply(60, TimeUnit.SECONDS);
        assertNotNull(reply);
        assertEquals(1, reply.getNumErrors());
        assertEquals(ErrorCode.SESSION_BUSY, reply.getError(0).getCode());
        session.release();
    }

    @Test
    public void requireThatSessionIsClosedOnDestroy() {
        SharedDestinationSession session = newDestinationSession();
        session.release();
        assertFalse("DestinationSession not destroyed by release().", session.session().destroy());
    }

    @Test
    public void requireThatMbusIsReleasedOnDestroy() {
        Slobrok slobrok = null;
        try {
            slobrok = new Slobrok();
        } catch (ListenFailedException e) {
            fail();
        }
        RPCNetworkParams netParams = new RPCNetworkParams().setSlobrokConfigId(slobrok.configId());
        SharedMessageBus mbus = SharedMessageBus.newInstance(new MessageBusParams(), netParams);
        SharedDestinationSession session = mbus.newDestinationSession(new DestinationSessionParams());
        mbus.release();
        session.release();
        assertFalse("MessageBus not destroyed by release().", mbus.messageBus().destroy());
    }

    @Test
    public void requireThatSessionCanSendReply() throws InterruptedException {
        RemoteClient client = RemoteClient.newInstanceWithInternSlobrok(true);
        MessageQueue queue = new MessageQueue();
        DestinationSessionParams params = new DestinationSessionParams().setMessageHandler(queue);
        SharedDestinationSession session = newDestinationSession(client.slobrokId(), params);
        Route route = Route.parse(session.connectionSpec());

        assertTrue(client.sendMessage(new SimpleMessage("foo").setRoute(route)).isAccepted());
        Message msg = queue.awaitMessage(60, TimeUnit.SECONDS);
        assertNotNull(msg);
        Reply reply = new SimpleReply("bar");
        reply.swapState(msg);
        session.sendReply(reply);
        assertNotNull(client.awaitReply(60, TimeUnit.SECONDS));

        session.release();
        client.close();
    }

    private static SharedDestinationSession newDestinationSession() {
        Slobrok slobrok = null;
        try {
            slobrok = new Slobrok();
        } catch (ListenFailedException e) {
            fail();
        }
        return newDestinationSession(slobrok.configId(), new DestinationSessionParams());
    }

    private static SharedDestinationSession newDestinationSession(String slobrokId, DestinationSessionParams params) {
        RPCNetworkParams netParams = new RPCNetworkParams().setSlobrokConfigId(slobrokId);
        MessageBusParams mbusParams = new MessageBusParams().addProtocol(new SimpleProtocol());
        SharedMessageBus mbus = SharedMessageBus.newInstance(mbusParams, netParams);
        SharedDestinationSession session = mbus.newDestinationSession(params);
        mbus.release();
        return session;
    }
}
