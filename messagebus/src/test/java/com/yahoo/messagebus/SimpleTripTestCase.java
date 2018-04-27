// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.messagebus.test.SimpleReply;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen
 */
public class SimpleTripTestCase {

    @Test
    public void testSimpleTrip() throws ListenFailedException {
        Slobrok slobrok = new Slobrok();
        TestServer server = new TestServer(new MessageBusParams().addProtocol(new SimpleProtocol()),
                                           new RPCNetworkParams()
                                                   .setIdentity(new Identity("srv"))
                                                   .setSlobrokConfigId(TestServer.getSlobrokConfig(slobrok)));
        DestinationSession dst = server.mb.createDestinationSession(new DestinationSessionParams().setName("session").setMessageHandler(new Receptor()));
        SourceSession src = server.mb.createSourceSession(
                new SourceSessionParams().setTimeout(600.0).setReplyHandler(new Receptor()));
        assertTrue(server.waitSlobrok("srv/session", 1));

        assertTrue(src.send(new SimpleMessage("msg"), Route.parse("srv/session")).isAccepted());
        Message msg = ((Receptor)dst.getMessageHandler()).getMessage(60);
        assertNotNull(msg);
        assertEquals(SimpleProtocol.NAME, msg.getProtocol());
        assertEquals(SimpleProtocol.MESSAGE, msg.getType());
        assertEquals("msg", ((SimpleMessage)msg).getValue());

        Reply reply = new SimpleReply("reply");
        reply.swapState(msg);
        dst.reply(reply);

        assertNotNull(reply = ((Receptor)src.getReplyHandler()).getReply(60));
        assertEquals(SimpleProtocol.NAME, reply.getProtocol());
        assertEquals(SimpleProtocol.REPLY, reply.getType());
        assertEquals("reply", ((SimpleReply)reply).getValue());

        src.destroy();
        dst.destroy();
        server.destroy();
    }

}
