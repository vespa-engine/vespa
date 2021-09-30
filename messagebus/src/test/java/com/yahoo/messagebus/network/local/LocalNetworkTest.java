// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.local;

import com.yahoo.concurrent.SystemTimer;
import com.yahoo.messagebus.DestinationSession;
import com.yahoo.messagebus.DestinationSessionParams;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.IntermediateSession;
import com.yahoo.messagebus.IntermediateSessionParams;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageBus;
import com.yahoo.messagebus.MessageBusParams;
import com.yahoo.messagebus.MessageHandler;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.Result;
import com.yahoo.messagebus.SourceSession;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.StaticThrottlePolicy;
import com.yahoo.messagebus.ThrottlePolicy;
import com.yahoo.messagebus.routing.Hop;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleProtocol;
import com.yahoo.messagebus.test.SimpleReply;
import org.junit.Test;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class LocalNetworkTest {

    @Test
    public void requireThatLocalNetworkCanSendAndReceive() throws InterruptedException {
        final LocalWire wire = new LocalWire();

        final Server serverA = new Server(wire);
        final SourceSession source = serverA.newSourceSession();

        final Server serverB = new Server(wire);
        final IntermediateSession intermediate = serverB.newIntermediateSession();

        final Server serverC = new Server(wire);
        final DestinationSession destination = serverC.newDestinationSession();

        Message msg = new SimpleMessage("foo");
        msg.setRoute(new Route().addHop(Hop.parse(intermediate.getConnectionSpec()))
                                .addHop(Hop.parse(destination.getConnectionSpec())));
        assertTrue(source.send(msg).isAccepted());

        msg = serverB.messages.poll(60, TimeUnit.SECONDS);
        assertTrue(msg instanceof SimpleMessage);
        assertEquals("foo", ((SimpleMessage)msg).getValue());
        intermediate.forward(msg);

        msg = serverC.messages.poll(60, TimeUnit.SECONDS);
        assertTrue(msg instanceof SimpleMessage);
        assertEquals("foo", ((SimpleMessage)msg).getValue());
        Reply reply = new SimpleReply("bar");
        reply.swapState(msg);
        destination.reply(reply);

        reply = serverB.replies.poll(60, TimeUnit.SECONDS);
        assertTrue(reply instanceof SimpleReply);
        assertEquals("bar", ((SimpleReply)reply).getValue());
        intermediate.forward(reply);

        reply = serverA.replies.poll(60, TimeUnit.SECONDS);
        assertTrue(reply instanceof SimpleReply);
        assertEquals("bar", ((SimpleReply)reply).getValue());

        serverA.mbus.destroy();
        serverB.mbus.destroy();
        serverC.mbus.destroy();
    }

    @Test
    public void requireThatUnknownServiceRepliesWithNoAddressForService() throws InterruptedException {
        final Server server = new Server(new LocalWire());
        final SourceSession source = server.newSourceSession();

        final Message msg = new SimpleMessage("foo").setRoute(Route.parse("bar"));
        assertTrue(source.send(msg).isAccepted());
        final Reply reply = server.replies.poll(60, TimeUnit.SECONDS);
        assertTrue(reply instanceof EmptyReply);

        server.mbus.destroy();
    }

    @Test
    public void requireThatBlockingSendTimeOutInSendQ() throws InterruptedException {
        final LocalWire wire = new LocalWire();

        final Server serverA = new Server(wire);
        final SourceSession source = serverA.newSourceSession(new StaticThrottlePolicy().setMaxPendingCount(1));

        final Server serverB = new Server(wire);
        final IntermediateSession intermediate = serverB.newIntermediateSession();

        final Server serverC = new Server(wire);
        final DestinationSession destination = serverC.newDestinationSession();

        Message msg = new SimpleMessage("foo");
        msg.setRoute(new Route().addHop(Hop.parse(intermediate.getConnectionSpec()))
                .addHop(Hop.parse(destination.getConnectionSpec())));
        assertTrue(source.sendBlocking(msg).isAccepted());
        long start = SystemTimer.INSTANCE.milliTime();
        Message msg2 = new SimpleMessage("foo2");
        msg2.setRoute(new Route().addHop(Hop.parse(intermediate.getConnectionSpec()))
                .addHop(Hop.parse(destination.getConnectionSpec())));
        long TIMEOUT = 1000;
        msg2.setTimeRemaining(TIMEOUT);
        Result res = source.sendBlocking(msg2);
        assertFalse(res.isAccepted());
        assertEquals(ErrorCode.TIMEOUT, res.getError().getCode());
        assertTrue(res.getError().getMessage().endsWith("Timed out in sendQ"));
        long end = SystemTimer.INSTANCE.milliTime();
        assertTrue(end >= start+TIMEOUT);
        assertTrue(end < start+5*TIMEOUT);

        msg = serverB.messages.poll(60, TimeUnit.SECONDS);
        assertTrue(msg instanceof SimpleMessage);
        assertEquals("foo", ((SimpleMessage)msg).getValue());
        intermediate.forward(msg);

        msg = serverC.messages.poll(60, TimeUnit.SECONDS);
        assertTrue(msg instanceof SimpleMessage);
        assertEquals("foo", ((SimpleMessage)msg).getValue());
        Reply reply = new SimpleReply("bar");
        reply.swapState(msg);
        destination.reply(reply);

        reply = serverB.replies.poll(60, TimeUnit.SECONDS);
        assertTrue(reply instanceof SimpleReply);
        assertEquals("bar", ((SimpleReply)reply).getValue());
        intermediate.forward(reply);

        reply = serverA.replies.poll(60, TimeUnit.SECONDS);
        assertEquals(ErrorCode.TIMEOUT, reply.getError(0).getCode());
        assertTrue(reply.getError(0).getMessage().endsWith("Timed out in sendQ"));

        reply = serverA.replies.poll(60, TimeUnit.SECONDS);
        assertTrue(reply instanceof SimpleReply);
        assertEquals("bar", ((SimpleReply)reply).getValue());

        serverA.mbus.destroy();
        serverB.mbus.destroy();
        serverC.mbus.destroy();

    }

    private static class Server implements MessageHandler, ReplyHandler {

        final MessageBus mbus;
        final BlockingDeque<Message> messages = new LinkedBlockingDeque<>();
        final BlockingDeque<Reply> replies = new LinkedBlockingDeque<>();

        Server(LocalWire wire) {
            mbus = new MessageBus(new LocalNetwork(wire),
                                  new MessageBusParams().addProtocol(new SimpleProtocol())
                                                        .setRetryPolicy(null));
        }

        SourceSession newSourceSession() {
            return mbus.createSourceSession(new SourceSessionParams()
                    .setTimeout(600.0)
                    .setReplyHandler(this));
        }
        SourceSession newSourceSession(ThrottlePolicy throttlePolicy) {
            return mbus.createSourceSession(new SourceSessionParams()
                    .setTimeout(600.0)
                    .setReplyHandler(this)
                    .setThrottlePolicy(throttlePolicy));
        }
        IntermediateSession newIntermediateSession() {
            return mbus.createIntermediateSession(new IntermediateSessionParams()
                                                          .setMessageHandler(this)
                                                          .setReplyHandler(this));
        }

        DestinationSession newDestinationSession() {
            return mbus.createDestinationSession(new DestinationSessionParams()
                                                         .setMessageHandler(this));
        }

        @Override
        public void handleMessage(Message msg) {
            messages.addLast(msg);
        }

        @Override
        public void handleReply(Reply reply) {
            replies.addLast(reply);
        }
    }
}
