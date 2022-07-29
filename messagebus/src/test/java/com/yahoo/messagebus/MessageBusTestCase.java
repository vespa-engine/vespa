// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.network.rpc.RPCNetwork;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.messagebus.network.rpc.test.TestServer;
import com.yahoo.messagebus.routing.RetryTransientErrorsPolicy;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.routing.RoutingPolicy;
import com.yahoo.messagebus.routing.test.CustomPolicyFactory;
import com.yahoo.messagebus.test.Receptor;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleProtocol;
import org.junit.jupiter.api.Test;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MessageBusTestCase {

    @Test
    void requireThatBucketSequencingWithResenderEnabledCausesError() throws ListenFailedException {
        Slobrok slobrok = new Slobrok();
        TestServer server = new TestServer(new MessageBusParams()
                        .addProtocol(new SimpleProtocol())
                        .setRetryPolicy(new RetryTransientErrorsPolicy()),
                new RPCNetworkParams()
                        .setSlobrokConfigId(slobrok.configId()));
        Receptor receptor = new Receptor();
        SourceSession session = server.mb.createSourceSession(
                new SourceSessionParams().setTimeout(600.0).setReplyHandler(receptor));
        assertTrue(session.send(new SimpleMessage("foo") {
            @Override
            public boolean hasBucketSequence() {
                return true;
            }
        }.setRoute(Route.parse("bar"))).isAccepted());
        Reply reply = receptor.getReply(60);
        assertNotNull(reply);
        assertEquals(1, reply.getNumErrors());
        assertEquals(ErrorCode.SEQUENCE_ERROR, reply.getError(0).getCode());
        session.destroy();
        server.destroy();
        slobrok.stop();
    }

    @Test
    void testConnectionSpec() throws ListenFailedException, UnknownHostException {
        // Setup servers and sessions.
        Slobrok slobrok = new Slobrok();
        List<TestServer> servers = new ArrayList<>();

        TestServer srcServer = new TestServer("feeder", null, slobrok, null);
        servers.add(srcServer);
        SourceSession src = servers.get(0).mb.createSourceSession(new Receptor());

        List<IntermediateSession> sessions = new ArrayList<>();
        for (int i = 0; i < 10; ++i) {
            TestServer server = new TestServer("intermediate/" + i, null, slobrok, null);
            servers.add(server);
            sessions.add(server.mb.createIntermediateSession("session", true, new Receptor(), new Receptor()));
        }

        TestServer dstServer = new TestServer("destination", null, slobrok, null);
        DestinationSession dst = dstServer.mb.createDestinationSession("session", true, new Receptor());

        assertTrue(srcServer.waitSlobrok("intermediate/*/session", sessions.size()));
        assertTrue(srcServer.waitSlobrok("destination/session", 1));

        StringBuilder route = new StringBuilder();
        for (int i = 0; i < sessions.size(); i++) {
            route.append("intermediate/").append(i).append("/session ");
            route.append(sessions.get(i).getConnectionSpec()).append(" ");
        }
        route.append(dst.getConnectionSpec());

        Message msg = new SimpleMessage("empty");
        assertTrue(src.send(msg, Route.parse(route.toString())).isAccepted());
        for (IntermediateSession itr : sessions) {
            // Received using session name.
            assertNotNull(msg = ((Receptor) itr.getMessageHandler()).getMessage(60));
            itr.forward(msg);

            // Received using connection spec.
            assertNotNull(msg = ((Receptor) itr.getMessageHandler()).getMessage(60));
            itr.forward(msg);
        }
        assertNotNull(msg = ((Receptor) dst.getMessageHandler()).getMessage(60));
        dst.acknowledge(msg);
        for (int i = sessions.size(); --i >= 0; ) {
            IntermediateSession itr = sessions.get(i);

            // Received for connection spec.
            Reply reply = ((Receptor) itr.getReplyHandler()).getReply(60);
            assertNotNull(reply);
            itr.forward(reply);

            // Received for session name.
            assertNotNull(reply = ((Receptor) itr.getReplyHandler()).getReply(60));
            itr.forward(reply);
        }
        assertNotNull(((Receptor) src.getReplyHandler()).getReply(60));

        // Cleanup.
        for (IntermediateSession session : sessions) {
            session.destroy();
        }
        for (TestServer server : servers) {
            server.destroy();
        }
        slobrok.stop();
    }

    @Test
    void testRoutingPolicyCache() throws ListenFailedException, UnknownHostException {
        Slobrok slobrok = new Slobrok();
        String config = "slobrok[1]\nslobrok[0].connectionspec \"" + new Spec("localhost", slobrok.port()).toString() + "\"\n";
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("Custom", new CustomPolicyFactory());
        MessageBus bus = new MessageBus(new RPCNetwork(new RPCNetworkParams().setSlobrokConfigId("raw:" + config)),
                new MessageBusParams().addProtocol(protocol));

        RoutingPolicy all = bus.getRoutingPolicy(SimpleProtocol.NAME, "Custom", null);
        assertNotNull(all);

        RoutingPolicy ref = bus.getRoutingPolicy(SimpleProtocol.NAME, "Custom", null);
        assertNotNull(ref);
        assertSame(all, ref);

        RoutingPolicy allArg = bus.getRoutingPolicy(SimpleProtocol.NAME, "Custom", "Arg");
        assertNotNull(allArg);
        assertNotSame(all, allArg);

        RoutingPolicy refArg = bus.getRoutingPolicy(SimpleProtocol.NAME, "Custom", "Arg");
        assertNotNull(refArg);
        assertSame(allArg, refArg);
    }
}
