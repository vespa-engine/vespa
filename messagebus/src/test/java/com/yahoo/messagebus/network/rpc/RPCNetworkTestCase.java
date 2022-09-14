// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.component.Version;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.DestinationSession;
import com.yahoo.messagebus.DestinationSessionParams;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageBusParams;
import com.yahoo.messagebus.Protocol;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.Routable;
import com.yahoo.messagebus.SourceSession;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.network.rpc.test.TestServer;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.routing.RoutingPolicy;
import com.yahoo.messagebus.test.Receptor;
import com.yahoo.text.Utf8String;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class RPCNetworkTestCase {

    @Test
    void requireThatProtocolEncodeExceptionIsCaught() throws Exception {
        RuntimeException e = new RuntimeException();

        Slobrok slobrok = new Slobrok();
        TestServer server = new TestServer(new MessageBusParams().addProtocol(MyProtocol.newEncodeException(e)),
                new RPCNetworkParams().setSlobrokConfigId(slobrok.configId()));
        Receptor receptor = new Receptor();
        SourceSession src = server.mb.createSourceSession(
                new SourceSessionParams().setTimeout(600.0).setReplyHandler(receptor));
        DestinationSession dst = server.mb.createDestinationSession(new DestinationSessionParams());
        assertTrue(src.send(new MyMessage().setRoute(Route.parse(dst.getConnectionSpec()))).isAccepted());

        Reply reply = receptor.getReply(60);
        assertNotNull(reply);
        assertEquals(1, reply.getNumErrors());

        StringWriter expected = new StringWriter();
        e.printStackTrace(new PrintWriter(expected));

        String actual = reply.getError(0).toString();
        assertTrue(actual.contains(expected.toString()), actual);
    }

    private static class MyMessage extends Message {

        @Override
        public Utf8String getProtocol() {
            return new Utf8String(MyProtocol.NAME);
        }

        @Override
        public int getType() {
            return 0;
        }
    }

    private static class MyProtocol implements Protocol {

        final static String NAME = "myProtocol";
        final RuntimeException encodeException;

        MyProtocol(RuntimeException encodeException) {
            this.encodeException = encodeException;
        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public byte[] encode(Version version, Routable routable) {
            throw encodeException;
        }

        @Override
        public Routable decode(Version version, byte[] payload) {
            return null;
        }

        @Override
        public RoutingPolicy createPolicy(String name, String param) {
            return null;
        }

        static MyProtocol newEncodeException(RuntimeException e) {
            return new MyProtocol(e);
        }
    }

}
