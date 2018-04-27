// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol.test;

import com.yahoo.component.VersionSpecification;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.serialization.DocumentDeserializer;
import com.yahoo.document.serialization.DocumentSerializer;
import com.yahoo.documentapi.messagebus.protocol.DocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.DocumentReply;
import com.yahoo.documentapi.messagebus.protocol.RoutableFactories50;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.DestinationSession;
import com.yahoo.messagebus.DestinationSessionParams;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageBusParams;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.SourceSession;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.network.Identity;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.messagebus.network.rpc.test.TestServer;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.test.Receptor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen
 */
public class RoutableFactoryTestCase {

    private Slobrok slobrok;
    private DocumentProtocol srcProtocol, dstProtocol;
    private TestServer srcServer, dstServer;
    private SourceSession srcSession;
    private DestinationSession dstSession;

    @Before
    public void setUp() throws ListenFailedException {
        slobrok = new Slobrok();
        DocumentTypeManager docMan = new DocumentTypeManager();
        dstProtocol = new DocumentProtocol(docMan);
        dstServer = new TestServer(new MessageBusParams().addProtocol(dstProtocol),
                                   new RPCNetworkParams().setIdentity(new Identity("dst")).setSlobrokConfigId(TestServer.getSlobrokConfig(slobrok)));
        dstSession = dstServer.mb.createDestinationSession(new DestinationSessionParams().setName("session").setMessageHandler(new Receptor()));
        srcProtocol = new DocumentProtocol(docMan);
        srcServer = new TestServer(new MessageBusParams().addProtocol(srcProtocol),
                                   new RPCNetworkParams().setSlobrokConfigId(TestServer.getSlobrokConfig(slobrok)));
        srcSession = srcServer.mb.createSourceSession(new SourceSessionParams().setReplyHandler(new Receptor()));
        assertTrue(srcServer.waitSlobrok("dst/session", 1));
    }

    @After
    public void tearDown() {
        slobrok.stop();
        dstSession.destroy();
        dstServer.destroy();
        srcSession.destroy();
        srcServer.destroy();
    }

    @Test
    public void testFactory() {
        Route route = Route.parse("dst/session");

        // Source should fail to encode the message.
        assertTrue(srcSession.send(new MyMessage(), route).isAccepted());
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertTrue(reply.hasErrors());
        assertEquals(ErrorCode.ENCODE_ERROR, reply.getError(0).getCode());
        assertNull(reply.getError(0).getService());

        // Destination should fail to decode the message.
        srcProtocol.putRoutableFactory(MyMessage.TYPE, new MyMessageFactory(), new VersionSpecification());
        assertTrue(srcSession.send(new MyMessage(), route).isAccepted());
        assertNotNull(reply = ((Receptor)srcSession.getReplyHandler()).getReply(60));
        System.out.println(reply.getTrace());
        assertTrue(reply.hasErrors());
        assertEquals(ErrorCode.DECODE_ERROR, reply.getError(0).getCode());
        assertEquals("dst/session", reply.getError(0).getService());

        // Destination should fail to encode the reply.
        dstProtocol.putRoutableFactory(MyMessage.TYPE, new MyMessageFactory(), new VersionSpecification());
        assertTrue(srcSession.send(new MyMessage(), route).isAccepted());
        Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60);
        assertNotNull(msg);
        reply = new MyReply();
        reply.swapState(msg);
        dstSession.reply(reply);
        assertNotNull(reply = ((Receptor)srcSession.getReplyHandler()).getReply(60));
        System.out.println(reply.getTrace());
        assertTrue(reply.hasErrors());
        assertEquals(ErrorCode.ENCODE_ERROR, reply.getError(0).getCode());
        assertEquals("dst/session", reply.getError(0).getService());

        // Source should fail to decode the reply.
        dstProtocol.putRoutableFactory(MyReply.TYPE, new MyReplyFactory(), new VersionSpecification());
        assertTrue(srcSession.send(new MyMessage(), route).isAccepted());
        assertNotNull(msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60));
        reply = new MyReply();
        reply.swapState(msg);
        dstSession.reply(reply);
        assertNotNull(reply = ((Receptor)srcSession.getReplyHandler()).getReply(60));
        System.out.println(reply.getTrace());
        assertTrue(reply.hasErrors());
        assertEquals(ErrorCode.DECODE_ERROR, reply.getError(0).getCode());
        assertNull(reply.getError(0).getService());

        // All should succeed.
        srcProtocol.putRoutableFactory(MyReply.TYPE, new MyReplyFactory(), new VersionSpecification());
        assertTrue(srcSession.send(new MyMessage(), route).isAccepted());
        assertNotNull(msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60));
        reply = new MyReply();
        reply.swapState(msg);
        dstSession.reply(reply);
        assertNotNull(reply = ((Receptor)srcSession.getReplyHandler()).getReply(60));
        System.out.println(reply.getTrace());
        assertFalse(reply.hasErrors());
    }

    private static class MyMessageFactory extends RoutableFactories50.DocumentMessageFactory {

        @Override
        protected DocumentMessage doDecode(DocumentDeserializer buf) {
            return new MyMessage();
        }

        @Override
        protected boolean doEncode(DocumentMessage msg, DocumentSerializer buf) {
            return true;
        }
    }

    private static class MyReplyFactory extends RoutableFactories50.DocumentReplyFactory {

        @Override
        protected DocumentReply doDecode(DocumentDeserializer buf) {
            return new MyReply();
        }

        @Override
        protected boolean doEncode(DocumentReply msg, DocumentSerializer buf) {
            return true;
        }
    }

    private static class MyMessage extends DocumentMessage {

        final static int TYPE = 666;

        MyMessage() {
            getTrace().setLevel(9);
        }

        @Override
        public DocumentReply createReply() {
            return new MyReply();
        }

        @Override
        public int getType() {
            return TYPE;
        }
    }

    private static class MyReply extends DocumentReply {

        final static int TYPE = 777;

        public MyReply() {
            super(TYPE);
        }
    }

}
