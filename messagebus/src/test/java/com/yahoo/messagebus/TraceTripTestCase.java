// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.network.rpc.test.TestServer;
import com.yahoo.messagebus.routing.RoutingTableSpec;
import com.yahoo.messagebus.test.Receptor;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleProtocol;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.UnknownHostException;
import java.util.Arrays;

import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen
 */
public class TraceTripTestCase {

    Slobrok slobrok;
    TestServer src;
    TestServer pxy;
    TestServer dst;

    @Before
    public void setUp() throws ListenFailedException {
        RoutingTableSpec table = new RoutingTableSpec(SimpleProtocol.NAME)
                                 .addHop("pxy", "test/pxy/session", Arrays.asList("test/pxy/session"))
                                 .addHop("dst", "test/dst/session", Arrays.asList("test/dst/session"))
                                 .addRoute("test", Arrays.asList("pxy", "dst"));

        slobrok = new Slobrok();
        src = new TestServer("test/src", table, slobrok, null);
        pxy = new TestServer("test/pxy", table, slobrok, null);
        dst = new TestServer("test/dst", table, slobrok, null);
    }

    @After
    public void tearDown() {
        dst.destroy();
        pxy.destroy();
        src.destroy();
        slobrok.stop();
    }

    @Test
    public void testTrip() {
        Receptor src_rr = new Receptor();
        SourceSession src_s = src.mb.createSourceSession(src_rr);

        new Proxy(pxy.mb);
        assertTrue(src.waitSlobrok("test/pxy/session", 1));

        new Server(dst.mb);
        assertTrue(src.waitSlobrok("test/dst/session", 1));
        assertTrue(pxy.waitSlobrok("test/dst/session", 1));

        Message msg = new SimpleMessage("");
        msg.getTrace().setLevel(1);
        msg.getTrace().trace(1, "Client message", false);
        src_s.send(msg, "test");
        Reply reply = src_rr.getReply(60);
        reply.getTrace().trace(1, "Client reply", false);
        assertTrue(reply.getNumErrors() == 0);

        TraceNode t = new TraceNode()
                      .addChild("Client message")
                      .addChild("Proxy message")
                      .addChild("Server message")
                      .addChild("Server reply")
                      .addChild("Proxy reply")
                      .addChild("Client reply");
        System.out.println("reply: " + reply.getTrace().getRoot().encode());
        System.out.println("want : " + t.encode());
        assertTrue(reply.getTrace().getRoot().encode().equals(t.encode()));
    }

    private static class Proxy implements MessageHandler, ReplyHandler {
        private IntermediateSession session;

        public Proxy(MessageBus bus) {
            session = bus.createIntermediateSession("session", true, this, this);
        }

        public void handleMessage(Message msg) {
            msg.getTrace().trace(1, "Proxy message", false);
            System.out.println(msg.getTrace().getRoot().encode());
            session.forward(msg);
        }

        public void handleReply(Reply reply) {
            reply.getTrace().trace(1, "Proxy reply", false);
            System.out.println(reply.getTrace().getRoot().encode());
            session.forward(reply);
        }
    }

    private static class Server implements MessageHandler {
        private DestinationSession session;

        public Server(MessageBus bus) {
            session = bus.createDestinationSession("session", true, this);
        }

        public void handleMessage(Message msg) {
            msg.getTrace().trace(1, "Server message", false);
            System.out.println(msg.getTrace().getRoot().encode());
            Reply reply = new EmptyReply();
            msg.swapState(reply);
            reply.getTrace().trace(1, "Server reply", false);
            System.out.println(reply.getTrace().getRoot().encode());
            session.reply(reply);
        }
    }

}
