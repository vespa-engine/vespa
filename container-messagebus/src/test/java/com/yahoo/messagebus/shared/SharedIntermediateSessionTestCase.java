// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.shared;

import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.jdisc.test.MessageQueue;
import com.yahoo.messagebus.jdisc.test.RemoteClient;
import com.yahoo.messagebus.jdisc.test.RemoteServer;
import com.yahoo.messagebus.jdisc.test.ReplyQueue;
import com.yahoo.messagebus.network.local.LocalNetwork;
import com.yahoo.messagebus.network.local.LocalWire;
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
public class SharedIntermediateSessionTestCase {

    @Test
    public void requireThatMessageHandlerCanBeAccessed() {
        SharedIntermediateSession session = newIntermediateSession(false);
        assertNull(session.getMessageHandler());

        MessageQueue handler = new MessageQueue();
        session.setMessageHandler(handler);
        assertSame(handler, session.getMessageHandler());
    }

    @Test
    public void requireThatMessageHandlerCanOnlyBeSetOnce() {
        SharedIntermediateSession session = newIntermediateSession(false);
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
        SharedIntermediateSession session = newIntermediateSession(false);
        MessageQueue queue = new MessageQueue();
        session.setMessageHandler(queue);
        session.handleMessage(new SimpleMessage("foo"));
        assertNotNull(queue.awaitMessage(60, TimeUnit.SECONDS));
        session.release();
    }

    @Test
    public void requireThatSessionRepliesIfMessageHandlerIsNull() throws InterruptedException {
        SharedIntermediateSession session = newIntermediateSession(false);
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
    public void requireThatReplyHandlerCanNotBeSet() throws ListenFailedException {
        Slobrok slobrok = new Slobrok();
        try {
            newIntermediateSession(slobrok.configId(),
                                   new IntermediateSessionParams().setReplyHandler(new ReplyQueue()),
                                   false);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Reply handler must be null.", e.getMessage());
        }
    }

    @Test
    public void requireThatSessionIsClosedOnDestroy() {
        SharedIntermediateSession session = newIntermediateSession(false);
        session.release();
        assertFalse("IntermediateSession not destroyed by release().", session.session().destroy());
    }

    @Test
    public void requireThatMbusIsReleasedOnDestroy() {
        try {
            new Slobrok();
        } catch (ListenFailedException e) {
            fail();
        }
        SharedMessageBus mbus = new SharedMessageBus(new MessageBus(new LocalNetwork(new LocalWire()), new MessageBusParams()));

        SharedIntermediateSession session = mbus.newIntermediateSession(new IntermediateSessionParams());
        mbus.release();
        session.release();
        assertFalse("MessageBus not destroyed by release().", mbus.messageBus().destroy());
    }

    @Test
    public void requireThatSessionCanSendMessage() throws InterruptedException {
        RemoteServer server = RemoteServer.newInstanceWithInternSlobrok();
        SharedIntermediateSession session = newIntermediateSession(server.slobrokId(),
                                                                   new IntermediateSessionParams(),
                                                                   true);
        ReplyQueue queue = new ReplyQueue();
        Message msg = new SimpleMessage("foo").setRoute(Route.parse(server.connectionSpec()));
        msg.setTimeReceivedNow();
        msg.setTimeRemaining(60000);
        msg.getTrace().setLevel(9);
        msg.pushHandler(queue);
        assertTrue(session.sendMessage(msg).isAccepted());
        assertNotNull(msg = server.awaitMessage(60, TimeUnit.SECONDS));
        server.ackMessage(msg);
        assertNotNull(queue.awaitReply(60, TimeUnit.SECONDS));

        session.release();
        server.close();
    }

    @Test
    public void requireThatSessionCanSendReply() throws InterruptedException {
        RemoteClient client = RemoteClient.newInstanceWithInternSlobrok(true);
        MessageQueue queue = new MessageQueue();
        IntermediateSessionParams params = new IntermediateSessionParams().setMessageHandler(queue);
        SharedIntermediateSession session = newIntermediateSession(client.slobrokId(), params, true);
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

    private static SharedIntermediateSession newIntermediateSession(boolean network) {
        Slobrok slobrok = null;
        try {
            slobrok = new Slobrok();
        } catch (ListenFailedException e) {
            fail();
        }
        return newIntermediateSession(slobrok.configId(), new IntermediateSessionParams(), network);
    }

    private static SharedIntermediateSession newIntermediateSession(String slobrokId,
                                                                    IntermediateSessionParams params,
                                                                    boolean network) {
        RPCNetworkParams netParams = new RPCNetworkParams().setSlobrokConfigId(slobrokId);
        MessageBusParams mbusParams = new MessageBusParams().addProtocol(new SimpleProtocol());
        SharedMessageBus mbus = network
                                ? SharedMessageBus.newInstance(mbusParams, netParams)
                                : new SharedMessageBus(new MessageBus(new LocalNetwork(new LocalWire()), mbusParams));
        SharedIntermediateSession session = mbus.newIntermediateSession(params);
        mbus.release();
        return session;
    }
}
