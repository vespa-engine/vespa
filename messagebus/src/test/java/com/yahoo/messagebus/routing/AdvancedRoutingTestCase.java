// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.DestinationSession;
import com.yahoo.messagebus.DestinationSessionParams;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageBusParams;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.SourceSession;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.network.Identity;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.messagebus.network.rpc.test.TestServer;
import com.yahoo.messagebus.routing.test.CustomPolicyFactory;
import com.yahoo.messagebus.test.Receptor;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleProtocol;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.UnknownHostException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen
 */
public class AdvancedRoutingTestCase {

    Slobrok slobrok;
    TestServer srcServer, dstServer;
    SourceSession srcSession;
    DestinationSession dstFoo, dstBar, dstBaz;

    @Before
    public void setUp() throws ListenFailedException {
        slobrok = new Slobrok();
        dstServer = new TestServer(new MessageBusParams().addProtocol(new SimpleProtocol()),
                                   new RPCNetworkParams().setIdentity(new Identity("dst")).setSlobrokConfigId(TestServer.getSlobrokConfig(slobrok)));
        dstFoo = dstServer.mb.createDestinationSession(new DestinationSessionParams().setName("foo").setMessageHandler(new Receptor()));
        dstBar = dstServer.mb.createDestinationSession(new DestinationSessionParams().setName("bar").setMessageHandler(new Receptor()));
        dstBaz = dstServer.mb.createDestinationSession(new DestinationSessionParams().setName("baz").setMessageHandler(new Receptor()));
        srcServer = new TestServer(new MessageBusParams().setRetryPolicy(new RetryTransientErrorsPolicy().setBaseDelay(0)).addProtocol(new SimpleProtocol()),
                                   new RPCNetworkParams().setSlobrokConfigId(TestServer.getSlobrokConfig(slobrok)));
        srcSession = srcServer.mb.createSourceSession(
                new SourceSessionParams().setTimeout(600.0).setReplyHandler(new Receptor()));
        assertTrue(srcServer.waitSlobrok("dst/*", 3));
    }

    @After
    public void tearDown() {
        slobrok.stop();
        dstFoo.destroy();
        dstBar.destroy();
        dstBaz.destroy();
        dstServer.destroy();
        srcSession.destroy();
        srcServer.destroy();
    }

    @Test
    public void testAdvanced() {
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("Custom", new CustomPolicyFactory(false, ErrorCode.NO_ADDRESS_FOR_SERVICE));
        srcServer.mb.putProtocol(protocol);
        srcServer.setupRouting(new RoutingTableSpec(SimpleProtocol.NAME)
                               .addHop(new HopSpec("bar", "dst/bar"))
                               .addHop(new HopSpec("baz", "dst/baz"))
                               .addRoute(new RouteSpec("baz").addHop("baz")));
        Message msg = new SimpleMessage("msg");
        msg.getTrace().setLevel(9);
        assertTrue(srcSession.send(msg, Route.parse("[Custom:" + dstFoo.getConnectionSpec() + ",bar,route:baz,dst/cox,?dst/unknown]")).isAccepted());

        // Initial send.
        assertNotNull(msg = ((Receptor)dstFoo.getMessageHandler()).getMessage(60));
        dstFoo.acknowledge(msg);
        assertNotNull(msg = ((Receptor)dstBar.getMessageHandler()).getMessage(60));
        Reply reply = new EmptyReply();
        reply.swapState(msg);
        reply.addError(new Error(ErrorCode.TRANSIENT_ERROR, "bar"));
        dstBar.reply(reply);
        assertNotNull(msg = ((Receptor)dstBaz.getMessageHandler()).getMessage(60));
        reply = new EmptyReply();
        reply.swapState(msg);
        reply.addError(new Error(ErrorCode.TRANSIENT_ERROR, "baz1"));
        dstBaz.reply(reply);

        // First retry.
        assertNull(((Receptor)dstFoo.getMessageHandler()).getMessage(0));
        assertNotNull(msg = ((Receptor)dstBar.getMessageHandler()).getMessage(60));
        dstBar.acknowledge(msg);
        assertNotNull(msg = ((Receptor)dstBaz.getMessageHandler()).getMessage(60));
        reply = new EmptyReply();
        reply.swapState(msg);
        reply.addError(new Error(ErrorCode.TRANSIENT_ERROR, "baz2"));
        dstBaz.reply(reply);

        // Second retry.
        assertNull(((Receptor)dstFoo.getMessageHandler()).getMessage(0));
        assertNull(((Receptor)dstBar.getMessageHandler()).getMessage(0));
        assertNotNull(msg = ((Receptor)dstBaz.getMessageHandler()).getMessage(60));
        reply = new EmptyReply();
        reply.swapState(msg);
        reply.addError(new Error(ErrorCode.FATAL_ERROR, "baz3"));
        dstBaz.reply(reply);

        // Done.
        reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertEquals(2, reply.getNumErrors());
        assertEquals(ErrorCode.FATAL_ERROR, reply.getError(0).getCode());
        assertEquals(ErrorCode.NO_ADDRESS_FOR_SERVICE, reply.getError(1).getCode());
    }

}
