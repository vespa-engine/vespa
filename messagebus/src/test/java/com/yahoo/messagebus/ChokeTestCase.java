// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.network.Identity;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.messagebus.network.rpc.test.TestServer;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.test.Receptor;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class ChokeTestCase {

    Slobrok slobrok;
    TestServer srcServer, dstServer;
    SourceSession srcSession;
    DestinationSession dstSession;

    @BeforeEach
    public void setUp() throws ListenFailedException {
        slobrok = new Slobrok();
        dstServer = new TestServer(new MessageBusParams().addProtocol(new SimpleProtocol()),
                                   new RPCNetworkParams().setIdentity(new Identity("dst")).setSlobrokConfigId(TestServer.getSlobrokConfig(slobrok)));
        dstSession = dstServer.mb.createDestinationSession(new DestinationSessionParams().setName("session").setMessageHandler(new Receptor()));
        srcServer = new TestServer(new MessageBusParams().setRetryPolicy(null).addProtocol(new SimpleProtocol()),
                                   new RPCNetworkParams().setSlobrokConfigId(TestServer.getSlobrokConfig(slobrok)));
        srcSession = srcServer.mb.createSourceSession(
                new SourceSessionParams().setTimeout(600.0).setThrottlePolicy(null).setReplyHandler(new Receptor()));
        assertTrue(srcServer.waitSlobrok("dst/session", 1));
    }

    @AfterEach
    public void tearDown() {
        slobrok.stop();
        dstSession.destroy();
        dstServer.destroy();
        srcSession.destroy();
        srcServer.destroy();
    }

    @Test
    @SuppressWarnings("deprecation")
    void testMaxCount() {
        int max = 10;
        dstServer.mb.setMaxPendingCount(max);
        List<Message> lst = new ArrayList<>();
        for (int i = 0; i < max * 2; ++i) {
            if (i < max) {
                assertEquals(i, dstServer.mb.getPendingCount());
            } else {
                assertEquals(max, dstServer.mb.getPendingCount());
            }
            assertTrue(srcSession.send(createMessage("msg"), Route.parse("dst/session")).isAccepted());
            if (i < max) {
                Message msg = ((Receptor) dstSession.getMessageHandler()).getMessage(60);
                assertNotNull(msg);
                lst.add(msg);
            } else {
                Reply reply = ((Receptor) srcSession.getReplyHandler()).getReply(60);
                assertNotNull(reply);
                assertEquals(1, reply.getNumErrors());
                assertEquals(ErrorCode.SESSION_BUSY, reply.getError(0).getCode());
            }
        }
        for (int i = 0; i < 5; ++i) {
            Message msg = lst.remove(0);
            dstSession.acknowledge(msg);

            Reply reply = ((Receptor) srcSession.getReplyHandler()).getReply(60);
            assertNotNull(reply);
            assertFalse(reply.hasErrors());
            assertNotNull(msg = reply.getMessage());
            assertTrue(srcSession.send(msg, Route.parse("dst/session")).isAccepted());

            assertNotNull(msg = ((Receptor) dstSession.getMessageHandler()).getMessage(60));
            lst.add(msg);
        }
        while (!lst.isEmpty()) {
            assertEquals(lst.size(), dstServer.mb.getPendingCount());
            Message msg = lst.remove(0);
            dstSession.acknowledge(msg);

            Reply reply = ((Receptor) srcSession.getReplyHandler()).getReply(60);
            assertNotNull(reply);
            assertFalse(reply.hasErrors());
        }
        assertEquals(0, dstServer.mb.getPendingCount());
    }

    @Test
    @SuppressWarnings("deprecation")
    void testMaxSize() {
        int size = createMessage("msg").getApproxSize();
        int max = size * 10;
        dstServer.mb.setMaxPendingSize(max);
        List<Message> lst = new ArrayList<>();
        for (int i = 0; i < max * 2; i += size) {
            if (i < max) {
                assertEquals(i, dstServer.mb.getPendingSize());
            } else {
                assertEquals(max, dstServer.mb.getPendingSize());
            }
            assertTrue(srcSession.send(createMessage("msg"), Route.parse("dst/session")).isAccepted());
            if (i < max) {
                Message msg = ((Receptor) dstSession.getMessageHandler()).getMessage(60);
                assertNotNull(msg);
                lst.add(msg);
            } else {
                Reply reply = ((Receptor) srcSession.getReplyHandler()).getReply(60);
                assertNotNull(reply);
                assertEquals(1, reply.getNumErrors());
                assertEquals(ErrorCode.SESSION_BUSY, reply.getError(0).getCode());
            }
        }
        for (int i = 0; i < 5; ++i) {
            Message msg = lst.remove(0);
            dstSession.acknowledge(msg);

            Reply reply = ((Receptor) srcSession.getReplyHandler()).getReply(60);
            assertNotNull(reply);
            assertFalse(reply.hasErrors());
            assertNotNull(msg = reply.getMessage());
            assertTrue(srcSession.send(msg, Route.parse("dst/session")).isAccepted());

            assertNotNull(msg = ((Receptor) dstSession.getMessageHandler()).getMessage(60));
            lst.add(msg);
        }
        while (!lst.isEmpty()) {
            assertEquals(size * lst.size(), dstServer.mb.getPendingSize());
            Message msg = lst.remove(0);
            dstSession.acknowledge(msg);

            Reply reply = ((Receptor) srcSession.getReplyHandler()).getReply(60);
            assertNotNull(reply);
            assertFalse(reply.hasErrors());
        }
        assertEquals(0, dstServer.mb.getPendingSize());
    }

    private static Message createMessage(String msg) {
        Message ret = new SimpleMessage(msg);
        ret.getTrace().setLevel(9);
        return ret;
    }

}
