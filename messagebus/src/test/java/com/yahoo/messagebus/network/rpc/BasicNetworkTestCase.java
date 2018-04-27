// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;


import com.yahoo.concurrent.SystemTimer;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.network.rpc.test.TestServer;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.routing.RoutingTableSpec;
import com.yahoo.messagebus.test.Receptor;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleProtocol;
import com.yahoo.messagebus.test.SimpleReply;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.UnknownHostException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


/**
 * @author havardpe
 */
public class BasicNetworkTestCase {

    Slobrok     slobrok;
    TestServer src;
    TestServer  pxy;
    TestServer  dst;

    @Before
    public void setUp() throws ListenFailedException {
        RoutingTableSpec table = new RoutingTableSpec(SimpleProtocol.NAME);
        table.addHop("pxy", "test/pxy/session", Arrays.asList("test/pxy/session"));
        table.addHop("dst", "test/dst/session", Arrays.asList("test/dst/session"));
        table.addRoute("test", Arrays.asList("pxy", "dst"));
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
    public void testNetwork() {
        // set up receptors
        Receptor src_rr = new Receptor();
        Receptor pxy_mr = new Receptor();
        Receptor pxy_rr = new Receptor();
        Receptor dst_mr = new Receptor();

        // set up sessions
        SourceSessionParams sp = new SourceSessionParams();
        sp.setTimeout(30.0);

        SourceSession       ss = src.mb.createSourceSession(src_rr, sp);
        IntermediateSession is = pxy.mb.createIntermediateSession("session", true, pxy_mr, pxy_rr);
        DestinationSession  ds = dst.mb.createDestinationSession("session", true, dst_mr);

        // wait for slobrok registration
        assertTrue(src.waitSlobrok("test/pxy/session", 1));
        assertTrue(src.waitSlobrok("test/dst/session", 1));
        assertTrue(pxy.waitSlobrok("test/dst/session", 1));

        // send message on client
        ss.send(new SimpleMessage("test message"), "test");

        // check message on proxy
        Message msg = pxy_mr.getMessage(60);
        assertTrue(msg != null);
        assertEquals(SimpleProtocol.MESSAGE, msg.getType());
        SimpleMessage sm = (SimpleMessage) msg;
        assertEquals("test message", sm.getValue());

        // forward message on proxy
        sm.setValue(sm.getValue() + " pxy");
        is.forward(sm);

        // check message on server
        msg = dst_mr.getMessage(60);
        assertTrue(msg != null);
        assertEquals(SimpleProtocol.MESSAGE, msg.getType());
        sm = (SimpleMessage) msg;
        assertEquals("test message pxy", sm.getValue());

        // send reply on server
        SimpleReply sr = new SimpleReply("test reply");
        sm.swapState(sr);
        ds.reply(sr);

        // check reply on proxy
        Reply reply = pxy_rr.getReply(60);
        assertTrue(reply != null);
        assertEquals(SimpleProtocol.REPLY, reply.getType());
        sr = (SimpleReply) reply;
        assertEquals("test reply", sr.getValue());

        // forward reply on proxy
        sr.setValue(sr.getValue() + " pxy");
        is.forward(sr);

        // check reply on client
        reply = src_rr.getReply(60);
        assertTrue(reply != null);
        assertEquals(SimpleProtocol.REPLY, reply.getType());
        sr = (SimpleReply) reply;
        assertEquals("test reply pxy", sr.getValue());

        ss.destroy();
        is.destroy();
        ds.destroy();
    }

    @Test
    public void testTimeoutsFollowMessage() {
        SourceSessionParams params = new SourceSessionParams().setTimeout(600.0);
        SourceSession ss = src.mb.createSourceSession(new Receptor(), params);
        DestinationSession ds = dst.mb.createDestinationSession("session", true, new Receptor());
        assertTrue(src.waitSlobrok("test/dst/session", 1));

        // Test default timeouts being set.
        Message msg = new SimpleMessage("msg");
        msg.getTrace().setLevel(9);
        long now = SystemTimer.INSTANCE.milliTime();
        assertTrue(ss.send(msg, Route.parse("dst")).isAccepted());

        assertNotNull(msg = ((Receptor)ds.getMessageHandler()).getMessage(60));
        assertTrue(msg.getTimeReceived() >= now);
        assertTrue(params.getTimeout() * 1000 >= msg.getTimeRemaining());
        ds.acknowledge(msg);

        assertNotNull(((Receptor)ss.getReplyHandler()).getReply(60));

        // Test default timeouts being overwritten.
        msg = new SimpleMessage("msg");
        msg.getTrace().setLevel(9);
        msg.setTimeRemaining(2 * (long)(params.getTimeout() * 1000));
        assertTrue(ss.send(msg, Route.parse("dst")).isAccepted());

        assertNotNull(msg = ((Receptor)ds.getMessageHandler()).getMessage(60));
        assertTrue(params.getTimeout() * 1000 < msg.getTimeRemaining());
        ds.acknowledge(msg);

        assertNotNull(((Receptor)ss.getReplyHandler()).getReply(60));

        ss.destroy();
        ds.destroy();
    }

}
