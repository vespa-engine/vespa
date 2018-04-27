// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.network.Identity;
import com.yahoo.messagebus.network.rpc.test.TestServer;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.test.QueueAdapter;
import com.yahoo.messagebus.test.SimpleMessage;
import org.junit.Test;

import java.net.UnknownHostException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author havardpe
 */
public class LoadBalanceTestCase {

    @Test
    public void testLoadBalance() throws ListenFailedException {
        Slobrok slobrok = new Slobrok();
        TestServer src = new TestServer("src", null, slobrok, null);
        TestServer dst1 = new TestServer("dst/1", null, slobrok, null);
        TestServer dst2 = new TestServer("dst/2", null, slobrok, null);
        TestServer dst3 = new TestServer("dst/3", null, slobrok, null);

        // set up handlers
        final QueueAdapter sq = new QueueAdapter();
        SourceSession ss = src.mb.createSourceSession(new SourceSessionParams().setTimeout(600.0).setThrottlePolicy(null)
                                                      .setReplyHandler(new ReplyHandler() {
            @Override
            public void handleReply(Reply reply) {
                System.out.println(Thread.currentThread().getName() + ": Reply '" +
                                   ((SimpleMessage)reply.getMessage()).getValue() + "' received at source.");
                sq.handleReply(reply);
            }
        }));
        SimpleDestination h1 = new SimpleDestination(dst1.mb, dst1.net.getIdentity());
        SimpleDestination h2 = new SimpleDestination(dst2.mb, dst2.net.getIdentity());
        SimpleDestination h3 = new SimpleDestination(dst3.mb, dst3.net.getIdentity());
        assertTrue(src.waitSlobrok("dst/*/session", 3));

        // send messages
        int msgCnt = 30; // should be divisible by 3
        for (int i = 0; i < msgCnt; ++i) {
            ss.send(new SimpleMessage("msg" + i), Route.parse("dst/*/session"));
        }

        // wait for replies
        assertTrue(sq.waitSize(msgCnt, 60));

        // check handler message distribution
        assertEquals(msgCnt / 3, h1.getCount());
        assertEquals(msgCnt / 3, h2.getCount());
        assertEquals(msgCnt / 3, h3.getCount());

        ss.destroy();
        h1.session.destroy();
        h2.session.destroy();
        h3.session.destroy();

        dst3.destroy();
        dst2.destroy();
        dst1.destroy();
        src.destroy();
        slobrok.stop();
    }

    /**
     * Implements a simple destination that counts and acknowledges all messages received.
     */
    private static class SimpleDestination implements MessageHandler {

        final DestinationSession session;
        final String ident;
        int cnt = 0;

        SimpleDestination(MessageBus mb, Identity ident) {
            this.session = mb.createDestinationSession("session", true, this);
            this.ident = ident.getServicePrefix();
        }

        @Override
        public synchronized void handleMessage(Message msg) {
            System.out.println(
                    Thread.currentThread().getName() + ": " +
                    "Message '" + ((SimpleMessage)msg).getValue() + "' received at '" + ident + "'.");
            session.acknowledge(msg);
            ++cnt;
        }

        public synchronized int getCount() {
            return cnt;
        }
    }

}
