// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.concurrent.SystemTimer;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.DestinationSession;
import com.yahoo.messagebus.IntermediateSession;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.SourceSession;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.network.rpc.test.TestServer;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.routing.RoutingTableSpec;
import com.yahoo.messagebus.test.Receptor;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleProtocol;
import com.yahoo.messagebus.test.SimpleReply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author havardpe
 */
public class BasicNetworkTestCase {

    Slobrok     slobrok;
    TestServer src;
    TestServer  pxy;
    TestServer  dst;

    @BeforeEach
    public void setUp() throws ListenFailedException {
        RoutingTableSpec table = new RoutingTableSpec(SimpleProtocol.NAME);
        table.addHop("pxy", "test/pxy/session", List.of("test/pxy/session"));
        table.addHop("dst", "test/dst/session", List.of("test/dst/session"));
        table.addRoute("test", List.of("pxy", "dst"));
        slobrok = new Slobrok();
        src = new TestServer("test/src", table, slobrok, null);
        pxy = new TestServer("test/pxy", table, slobrok, null);
        dst = new TestServer("test/dst", table, slobrok, null);
    }

    @AfterEach
    public void tearDown() {
        dst.destroy();
        pxy.destroy();
        src.destroy();
        slobrok.stop();
    }

    private class Fixture {
        // set up receptors
        Receptor src_rr = new Receptor();
        Receptor pxy_mr = new Receptor();
        Receptor pxy_rr = new Receptor();
        Receptor dst_mr = new Receptor();

        SourceSession       ss;
        IntermediateSession is;
        DestinationSession  ds;

        Fixture() {
            // set up sessions
            SourceSessionParams sp = new SourceSessionParams();
            sp.setTimeout(30.0);

            ss = src.mb.createSourceSession(src_rr, sp);
            is = pxy.mb.createIntermediateSession("session", true, pxy_mr, pxy_rr);
            ds = dst.mb.createDestinationSession("session", true, dst_mr);

            // wait for slobrok registration
            assertTrue(src.waitSlobrok("test/pxy/session", 1));
            assertTrue(src.waitSlobrok("test/dst/session", 1));
            assertTrue(pxy.waitSlobrok("test/dst/session", 1));
        }

        void destroy() {
            ss.destroy();
            is.destroy();
            ds.destroy();
        }
    }

    @Test
    void testNetwork() {
        var f = new Fixture();
        // send message on client
        f.ss.send(new SimpleMessage("test message"), "test");

        // check message on proxy
        Message msg = f.pxy_mr.getMessage(60);
        assertNotNull(msg);
        assertEquals(SimpleProtocol.MESSAGE, msg.getType());
        SimpleMessage sm = (SimpleMessage) msg;
        assertEquals("test message", sm.getValue());
        assertFalse(sm.hasMetadata());

        // forward message on proxy
        sm.setValue(sm.getValue() + " pxy");
        f.is.forward(sm);

        // check message on server
        msg = f.dst_mr.getMessage(60);
        assertNotNull(msg);
        assertEquals(SimpleProtocol.MESSAGE, msg.getType());
        sm = (SimpleMessage) msg;
        assertEquals("test message pxy", sm.getValue());
        assertFalse(sm.hasMetadata());

        // send reply on server
        SimpleReply sr = new SimpleReply("test reply");
        sm.swapState(sr);
        f.ds.reply(sr);

        // check reply on proxy
        Reply reply = f.pxy_rr.getReply(60);
        assertNotNull(reply);
        assertEquals(SimpleProtocol.REPLY, reply.getType());
        sr = (SimpleReply) reply;
        assertEquals("test reply", sr.getValue());

        // forward reply on proxy
        sr.setValue(sr.getValue() + " pxy");
        f.is.forward(sr);

        // check reply on client
        reply = f.src_rr.getReply(60);
        assertNotNull(reply);
        assertEquals(SimpleProtocol.REPLY, reply.getType());
        sr = (SimpleReply) reply;
        assertEquals("test reply pxy", sr.getValue());

        f.destroy();
    }

    void doTestMetadataKvsAreForwarded(String fooMeta, String barMeta) {
        var f = new Fixture();

        var msgToSend = new SimpleMessage("test message");
        msgToSend.setFooMeta(fooMeta);
        msgToSend.setBarMeta(barMeta);
        f.ss.send(msgToSend, "test");

        Message msg = f.pxy_mr.getMessage(60);
        assertNotNull(msg);
        SimpleMessage sm = (SimpleMessage)msg;
        assertEquals(fooMeta, sm.getFooMeta());
        assertEquals(barMeta, sm.getBarMeta());
        f.is.forward(msg);

        msg = f.dst_mr.getMessage(60);
        assertNotNull(msg);
        sm = (SimpleMessage)msg;
        assertEquals(fooMeta, sm.getFooMeta());
        assertEquals(barMeta, sm.getBarMeta());

        // Unwind to avoid dangling messages
        SimpleReply sr = new SimpleReply("test reply");
        msg.swapState(sr);
        f.ds.reply(sr);

        Reply reply = f.pxy_rr.getReply(60);
        assertNotNull(reply);
        f.is.forward(reply);

        reply = f.src_rr.getReply(60);
        assertNotNull(reply);

        f.destroy();
    }

    @Test
    void empty_kv_map_is_propagated() {
        doTestMetadataKvsAreForwarded(null, null);
    }

    @Test
    void single_header_kv_is_propagated() {
        doTestMetadataKvsAreForwarded("marve", null);
    }

    @Test
    void multiple_header_kvs_are_propagated() {
        doTestMetadataKvsAreForwarded("marve", "fleksnes");
    }

    @Test
    void testTimeoutsFollowMessage() {
        SourceSessionParams params = new SourceSessionParams().setTimeout(600.0);
        SourceSession ss = src.mb.createSourceSession(new Receptor(), params);
        DestinationSession ds = dst.mb.createDestinationSession("session", true, new Receptor());
        assertTrue(src.waitSlobrok("test/dst/session", 1));

        // Test default timeouts being set.
        Message msg = new SimpleMessage("msg");
        msg.getTrace().setLevel(9);
        long now = SystemTimer.INSTANCE.milliTime();
        assertTrue(ss.send(msg, Route.parse("dst")).isAccepted());

        assertNotNull(msg = ((Receptor) ds.getMessageHandler()).getMessage(60));
        assertTrue(msg.getTimeReceived() >= now);
        assertTrue(params.getTimeout() * 1000 >= msg.getTimeRemaining());
        ds.acknowledge(msg);

        assertNotNull(((Receptor) ss.getReplyHandler()).getReply(60));

        // Test default timeouts being overwritten.
        msg = new SimpleMessage("msg");
        msg.getTrace().setLevel(9);
        msg.setTimeRemaining(2 * (long) (params.getTimeout() * 1000));
        assertTrue(ss.send(msg, Route.parse("dst")).isAccepted());

        assertNotNull(msg = ((Receptor) ds.getMessageHandler()).getMessage(60));
        assertTrue(params.getTimeout() * 1000 < msg.getTimeRemaining());
        ds.acknowledge(msg);

        assertNotNull(((Receptor) ss.getReplyHandler()).getReply(60));

        ss.destroy();
        ds.destroy();
    }

}
