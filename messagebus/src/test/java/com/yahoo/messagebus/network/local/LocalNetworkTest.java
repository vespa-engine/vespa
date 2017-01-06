// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.local;

import com.yahoo.messagebus.DestinationSession;
import com.yahoo.messagebus.DestinationSessionParams;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.IntermediateSession;
import com.yahoo.messagebus.IntermediateSessionParams;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageBus;
import com.yahoo.messagebus.MessageBusParams;
import com.yahoo.messagebus.MessageHandler;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.SourceSession;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.routing.Hop;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleProtocol;
import com.yahoo.messagebus.test.SimpleReply;
import org.junit.Test;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
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
        assertThat(source.send(msg).isAccepted(), is(true));

        msg = serverB.messages.poll(60, TimeUnit.SECONDS);
        assertThat(msg, instanceOf(SimpleMessage.class));
        assertThat(((SimpleMessage)msg).getValue(), is("foo"));
        intermediate.forward(msg);

        msg = serverC.messages.poll(60, TimeUnit.SECONDS);
        assertThat(msg, instanceOf(SimpleMessage.class));
        assertThat(((SimpleMessage)msg).getValue(), is("foo"));
        Reply reply = new SimpleReply("bar");
        reply.swapState(msg);
        destination.reply(reply);

        reply = serverB.replies.poll(60, TimeUnit.SECONDS);
        assertThat(reply, instanceOf(SimpleReply.class));
        assertThat(((SimpleReply)reply).getValue(), is("bar"));
        intermediate.forward(reply);

        reply = serverA.replies.poll(60, TimeUnit.SECONDS);
        assertThat(reply, instanceOf(SimpleReply.class));
        assertThat(((SimpleReply)reply).getValue(), is("bar"));

        serverA.mbus.destroy();
        serverB.mbus.destroy();
        serverC.mbus.destroy();
    }

    @Test
    public void requireThatUnknownServiceRepliesWithNoAddressForService() throws InterruptedException {
        final Server server = new Server(new LocalWire());
        final SourceSession source = server.newSourceSession();

        final Message msg = new SimpleMessage("foo").setRoute(Route.parse("bar"));
        assertThat(source.send(msg).isAccepted(), is(true));
        final Reply reply = server.replies.poll(60, TimeUnit.SECONDS);
        assertThat(reply, instanceOf(EmptyReply.class));

        server.mbus.destroy();
    }

    private static class Server implements MessageHandler, ReplyHandler {

        final MessageBus mbus;
        final BlockingDeque<Message> messages = new LinkedBlockingDeque<>();
        final BlockingDeque<Reply> replies = new LinkedBlockingDeque<>();

        Server(final LocalWire wire) {
            mbus = new MessageBus(new LocalNetwork(wire),
                                  new MessageBusParams().addProtocol(new SimpleProtocol())
                                                        .setRetryPolicy(null));
        }

        SourceSession newSourceSession() {
            return mbus.createSourceSession(
                    new SourceSessionParams().setTimeout(600.0).setReplyHandler(this));
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
        public void handleMessage(final Message msg) {
            messages.addLast(msg);
        }

        @Override
        public void handleReply(final Reply reply) {
            replies.addLast(reply);
        }
    }
}
