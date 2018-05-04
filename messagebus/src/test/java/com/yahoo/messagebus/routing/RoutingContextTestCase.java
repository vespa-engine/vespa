// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.network.Identity;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.messagebus.network.rpc.test.TestServer;
import com.yahoo.messagebus.test.Receptor;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleProtocol;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen
 */
public class RoutingContextTestCase {

    public static final int TIMEOUT_SECS = 120;

    Slobrok slobrok;
    TestServer srcServer, dstServer;
    SourceSession srcSession;
    DestinationSession dstSession;

    @Before
    public void setUp() throws ListenFailedException {
        slobrok = new Slobrok();
        dstServer = new TestServer(new MessageBusParams().addProtocol(new SimpleProtocol()),
                                   new RPCNetworkParams().setIdentity(new Identity("dst")).setSlobrokConfigId(TestServer.getSlobrokConfig(slobrok)));
        dstSession = dstServer.mb.createDestinationSession(new DestinationSessionParams().setName("session").setMessageHandler(new Receptor()));
        srcServer = new TestServer(new MessageBusParams().setRetryPolicy(new RetryTransientErrorsPolicy().setBaseDelay(0)).addProtocol(new SimpleProtocol()),
                                   new RPCNetworkParams().setSlobrokConfigId(TestServer.getSlobrokConfig(slobrok)));
        srcSession = srcServer.mb.createSourceSession(
                new SourceSessionParams().setTimeout(600.0).setReplyHandler(new Receptor()));
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
    public void testSingleDirective() {
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("Custom", new CustomPolicyFactory(
                false,
                Arrays.asList("foo", "bar", "baz/cox"),
                Arrays.asList("foo", "bar")));
        srcServer.mb.putProtocol(protocol);
        srcServer.setupRouting(new RoutingTableSpec(SimpleProtocol.NAME)
                .addRoute(new RouteSpec("myroute").addHop("myhop"))
                .addHop(new HopSpec("myhop", "[Custom]")
                .addRecipient("foo").addRecipient("bar").addRecipient("baz/cox")));
        for (int i = 0; i < 2; ++i) {
            assertTrue(srcSession.send(createMessage("msg"), "myroute").isAccepted());
            Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(TIMEOUT_SECS);
            assertNotNull(reply);
            System.out.println(reply.getTrace());
            assertFalse(reply.hasErrors());
        }
    }

    @Test
    public void testMoreDirectives() {
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("Custom", new CustomPolicyFactory(
                false,
                Arrays.asList("foo", "foo/bar", "foo/bar0/baz", "foo/bar1/baz", "foo/bar/baz/cox"),
                Arrays.asList("foo/bar0/baz", "foo/bar1/baz")));
        srcServer.mb.putProtocol(protocol);
        srcServer.setupRouting(new RoutingTableSpec(SimpleProtocol.NAME)
                .addRoute(new RouteSpec("myroute").addHop("myhop"))
                .addHop(new HopSpec("myhop", "foo/[Custom]/baz")
                .addRecipient("foo").addRecipient("foo/bar")
                .addRecipient("foo/bar0/baz").addRecipient("foo/bar1/baz")
                .addRecipient("foo/bar/baz/cox")));
        for (int i = 0; i < 2; ++i) {
            assertTrue(srcSession.send(createMessage("msg"), "myroute").isAccepted());
            Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(TIMEOUT_SECS);
            assertNotNull(reply);
            System.out.println(reply.getTrace());
            assertFalse(reply.hasErrors());
        }
    }

    @Test
    public void testRecipientsRemain() {
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("First", new CustomPolicyFactory(true, Arrays.asList("foo/bar"), Arrays.asList("foo/[Second]")));
        protocol.addPolicyFactory("Second", new CustomPolicyFactory(false, Arrays.asList("foo/bar"), Arrays.asList("foo/bar")));
        srcServer.mb.putProtocol(protocol);
        srcServer.setupRouting(new RoutingTableSpec(SimpleProtocol.NAME)
                .addRoute(new RouteSpec("myroute").addHop("myhop"))
                .addHop(new HopSpec("myhop", "[First]/[Second]")
                .addRecipient("foo/bar")));
        for (int i = 0; i < 2; ++i) {
            assertTrue(srcSession.send(createMessage("msg"), "myroute").isAccepted());
            Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(TIMEOUT_SECS);
            assertNotNull(reply);
            System.out.println(reply.getTrace());
            assertFalse(reply.hasErrors());
        }
    }

    @Test
    public void testToString() {
        assertEquals("node : null, directive: 1, errors: [], selectOnRetry: true context: null", new RoutingContext(null, 1).toString());
    }

    @Test
    public void testConstRoute() {
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("DocumentRouteSelector",
                                  new CustomPolicyFactory(true, Arrays.asList("dst"), Arrays.asList("dst")));
        srcServer.mb.putProtocol(protocol);
        srcServer.setupRouting(new RoutingTableSpec(SimpleProtocol.NAME)
                .addRoute(new RouteSpec("default").addHop("indexing"))
                .addHop(new HopSpec("indexing", "[DocumentRouteSelector]").addRecipient("dst"))
                .addHop(new HopSpec("dst", "dst/session")));
        for (int i = 0; i < 2; ++i) {
            assertTrue(srcSession.send(createMessage("msg"), Route.parse("route:default")).isAccepted());
            Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(TIMEOUT_SECS);
            assertNotNull(msg);
            dstSession.acknowledge(msg);
            Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(TIMEOUT_SECS);
            assertNotNull(reply);
            System.out.println(reply.getTrace());
            assertFalse(reply.hasErrors());
        }
    }

    private Message createMessage(String msg) {
        Message ret = new SimpleMessage(msg);
        ret.getTrace().setLevel(9);
        return ret;
    }

    private static class CustomPolicyFactory implements SimpleProtocol.PolicyFactory {

        final boolean forward;
        final List<String> expectedAll;
        final List<String> expectedMatched;

        public CustomPolicyFactory(boolean forward, List<String> all, List<String> matched) {
            this.forward = forward;
            this.expectedAll = all;
            this.expectedMatched = matched;
        }

        public RoutingPolicy create(String param) {
            return new CustomPolicy(this);
        }
    }

    private static class CustomPolicy implements RoutingPolicy {

        CustomPolicyFactory factory;

        public CustomPolicy(CustomPolicyFactory factory) {
            this.factory = factory;
        }

        public void select(RoutingContext ctx) {
            Reply reply = new EmptyReply();
            reply.getTrace().setLevel(9);

            List<Route> recipients = ctx.getAllRecipients();
            if (factory.expectedAll.size() == recipients.size()) {
                ctx.trace(1, "Got " + recipients.size() + " expected recipients.");
                for (Route route : recipients) {
                    if (factory.expectedAll.contains(route.toString())) {
                        ctx.trace(1, "Got expected recipient '" + route + "'.");
                    } else {
                        reply.addError(new Error(ErrorCode.APP_FATAL_ERROR,
                                                 "Recipient '" + route + "' not expected."));
                    }
                }
            } else {
                reply.addError(new Error(ErrorCode.APP_FATAL_ERROR,
                                         "Expected " + factory.expectedAll.size() + " recipients, got " + recipients.size() + "."));
            }

            if (ctx.getNumRecipients() == recipients.size()) {
                for (int i = 0; i < recipients.size(); ++i) {
                    if (recipients.get(i) == ctx.getRecipient(i)) {
                        ctx.trace(1, "getRecipient(" + i + ") matches getAllRecipients().get(" + i + ")");
                    } else {
                        reply.addError(new Error(ErrorCode.APP_FATAL_ERROR,
                                                 "getRecipient(" + i + ") differs from getAllRecipients().get(" + i + ")"));
                    }
                }
            } else {
                reply.addError(new Error(ErrorCode.APP_FATAL_ERROR,
                                         "getNumRecipients() differs from getAllRecipients().size()"));
            }

            recipients = ctx.getMatchedRecipients();
            if (factory.expectedMatched.size() == recipients.size()) {
                ctx.trace(1, "Got " + recipients.size() + " matched recipients.");
                for (Route route : recipients) {
                    if (factory.expectedMatched.contains(route.toString())) {
                        ctx.trace(1, "Got matched recipient '" + route + "'.");
                    } else {
                        reply.addError(new Error(ErrorCode.APP_FATAL_ERROR,
                                                 "Matched recipient '" + route + "' not expected."));
                    }
                }
            } else {
                reply.addError(new Error(ErrorCode.APP_FATAL_ERROR,
                                         "Expected " + factory.expectedAll.size() + " matched recipients, got " + recipients.size() + "."));
            }

            if (!reply.hasErrors() && factory.forward) {
                for (Route route : recipients) {
                    ctx.addChild(route);
                }
            } else {
                ctx.setReply(reply);
            }
        }

        public void merge(RoutingContext ctx) {
            Reply ret = new EmptyReply();
            for (RoutingNodeIterator it = ctx.getChildIterator();
                 it.isValid(); it.next()) {
                Reply reply = it.getReplyRef();
                for (int i = 0; i < reply.getNumErrors(); ++i) {
                    ret.addError(reply.getError(i));
                }
            }
            ctx.setReply(ret);
        }

        @Override
        public void destroy() {
        }
    }

}
