// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol.test;

import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocolRoutingPolicy;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RoutingPolicyFactory;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.metrics.MetricSet;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.messagebus.network.rpc.test.TestServer;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.routing.RoutingContext;
import com.yahoo.messagebus.test.Receptor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen
 */
public class PolicyFactoryTestCase {

    private Slobrok slobrok;
    private TestServer srv;
    private SourceSession src;

    @Before
    public void setUp() throws ListenFailedException {
        slobrok = new Slobrok();
        srv = new TestServer(new MessageBusParams().addProtocol(new DocumentProtocol(new DocumentTypeManager())),
                             new RPCNetworkParams().setSlobrokConfigId(TestServer.getSlobrokConfig(slobrok)));
        src = srv.mb.createSourceSession(new SourceSessionParams().setReplyHandler(new Receptor()));
    }

    @After
    public void tearDown() {
        slobrok.stop();
        src.destroy();
        srv.destroy();
    }

    @Test
    public void testFactory() {
        Route route = Route.parse("[MyPolicy]");
        assertTrue(src.send(createMessage(), route).isAccepted());
        Reply reply = ((Receptor)src.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertEquals(1, reply.getNumErrors());
        assertEquals(ErrorCode.UNKNOWN_POLICY, reply.getError(0).getCode());

        Protocol obj = srv.mb.getProtocol(DocumentProtocol.NAME);
        assertTrue(obj instanceof DocumentProtocol);
        DocumentProtocol protocol = (DocumentProtocol)obj;
        protocol.putRoutingPolicyFactory("MyPolicy", new MyFactory());

        assertTrue(src.send(createMessage(), route).isAccepted());
        assertNotNull(reply = ((Receptor)src.getReplyHandler()).getReply(60));
        System.out.println(reply.getTrace());
        assertEquals(1, reply.getNumErrors());
        assertEquals(DocumentProtocol.ERROR_POLICY_FAILURE, reply.getError(0).getCode());
    }

    private static Message createMessage() {
        Message msg = new RemoveDocumentMessage(new DocumentId("doc:scheme:"));
        msg.getTrace().setLevel(9);
        return msg;
    }

    private static class MyFactory implements RoutingPolicyFactory {

        public DocumentProtocolRoutingPolicy createPolicy(String param) {
            return new MyPolicy(param);
        }

        public void destroy() {
        }
    }

    private static class MyPolicy implements DocumentProtocolRoutingPolicy {

        private final String param;

        private MyPolicy(String param) {
            this.param = param;
        }

        public void select(RoutingContext ctx) {
            ctx.setError(DocumentProtocol.ERROR_POLICY_FAILURE, param);
        }

        public void merge(RoutingContext ctx) {
            throw new AssertionError("Routing passed terminated select.");
        }

        public void destroy() {
        }

        public MetricSet getMetrics() {
            return null;
        }
    }

}
