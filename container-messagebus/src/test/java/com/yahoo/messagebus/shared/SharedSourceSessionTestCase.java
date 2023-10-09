// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.shared;

import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageBusParams;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.jdisc.test.RemoteServer;
import com.yahoo.messagebus.jdisc.test.ReplyQueue;
import com.yahoo.messagebus.jdisc.test.TestUtils;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleProtocol;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class SharedSourceSessionTestCase {

    @Test
    public void requireThatReplyHandlerCanNotBeSet() {
        try {
            newSourceSession(new SourceSessionParams().setReplyHandler(new ReplyQueue()));
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Reply handler must be null.", e.getMessage());
        }
    }

    @Test
    public void requireThatSessionIsClosedOnDestroy() {
        SharedSourceSession session = newSourceSession(new SourceSessionParams());
        session.release();
        assertFalse("SourceSession not destroyed by release().", session.session().destroy());
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
        SharedSourceSession session = mbus.newSourceSession(new SourceSessionParams());
        mbus.release();
        session.release();
        assertFalse("MessageBus not destroyed by release().", mbus.messageBus().destroy());
    }

    @Test
    public void requireThatSessionCanSendMessage() throws InterruptedException {
        RemoteServer server = RemoteServer.newInstanceWithInternSlobrok();
        SharedSourceSession session = newSourceSession(server.slobroksConfig(),
                                                       new SourceSessionParams());
        ReplyQueue queue = new ReplyQueue();
        Message msg = new SimpleMessage("foo").setRoute(Route.parse(server.connectionSpec()));
        msg.pushHandler(queue);
        assertTrue(session.sendMessage(msg).isAccepted());
        assertNotNull(msg = server.awaitMessage(60, TimeUnit.SECONDS));
        server.ackMessage(msg);
        assertNotNull(queue.awaitReply(60, TimeUnit.SECONDS));

        session.release();
        server.close();
    }

    private static SharedSourceSession newSourceSession(SourceSessionParams params) {
        Slobrok slobrok = null;
        try {
            slobrok = new Slobrok();
        } catch (ListenFailedException e) {
            fail();
        }
        return newSourceSession(TestUtils.configFor(slobrok), params);
    }

    private static SharedSourceSession newSourceSession(SlobroksConfig slobroksConfig, SourceSessionParams params) {
        RPCNetworkParams netParams = new RPCNetworkParams().setSlobroksConfig(slobroksConfig);
        MessageBusParams mbusParams = new MessageBusParams().addProtocol(new SimpleProtocol());
        SharedMessageBus mbus = SharedMessageBus.newInstance(mbusParams, netParams);
        SharedSourceSession session = mbus.newSourceSession(params);
        mbus.release();
        return session;
    }
}
