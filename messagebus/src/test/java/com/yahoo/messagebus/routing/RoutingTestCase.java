// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

import com.yahoo.component.Vtag;
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
import com.yahoo.messagebus.Trace;
import com.yahoo.messagebus.network.Identity;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.messagebus.network.rpc.test.TestServer;
import com.yahoo.messagebus.routing.test.CustomPolicy;
import com.yahoo.messagebus.routing.test.CustomPolicyFactory;
import com.yahoo.messagebus.test.Receptor;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleProtocol;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author havardpe
 * @author Simon Thoresen Hult
 */
public class RoutingTestCase {

    static final Logger log = Logger.getLogger(RoutingTestCase.class.getName());

    static Slobrok slobrok;
    static RetryTransientErrorsPolicy retryPolicy;
    static TestServer srcServer, dstServer;
    static int sessionNumber = 0;

    SourceSession srcSession;
    DestinationSession dstSession;
    String sessName = "session";
    String dstSessName = "dst/session";

    @BeforeClass
    public static void commonSetup() throws ListenFailedException {
        slobrok = new Slobrok();
        dstServer = new TestServer("dst", null, slobrok, new SimpleProtocol());
        retryPolicy = new RetryTransientErrorsPolicy();
        retryPolicy.setBaseDelay(0);
        srcServer = new TestServer(new MessageBusParams().setRetryPolicy(retryPolicy).addProtocol(new SimpleProtocol()),
                                   slobrok);
    }

    @AfterClass
    public static void commonTeardown() {
        dstServer.destroy();
        srcServer.destroy();
        slobrok.stop();
        dstServer = null;
        srcServer = null;
        slobrok = null;
    }

    @Before
    public void setUp() throws ListenFailedException, UnknownHostException {
        // reset some params:
        retryPolicy.setEnabled(true);
        retryPolicy.setBaseDelay(0);
        srcServer.mb.putProtocol(new SimpleProtocol());
        srcServer.setupRouting(new RoutingTableSpec(SimpleProtocol.NAME));
        // create sessions:
        ++sessionNumber;
        sessName = "session" + sessionNumber;
        dstSessName = "dst/" + sessName;
        dstSession = dstServer.mb.createDestinationSession(
                new DestinationSessionParams().setName(sessName).setMessageHandler(new Receptor()));
        srcSession = srcServer.mb.createSourceSession(
                new SourceSessionParams().setTimeout(600.0).setThrottlePolicy(null).setReplyHandler(new Receptor()));
        // wait for session register visible:
        assertTrue(srcServer.waitSlobrok(dstSessName, 1));
    }

    @After
    public void tearDown() {
        dstSession.destroy();
        srcSession.destroy();
        // wait for session unregister visible:
        assertTrue(srcServer.waitSlobrok(dstSessName, 0));
    }

    @Test
    public void requireThatNullRouteIsCaught() {
        log.log(Level.INFO, "Starting: requireThatNullRouteIsCaught");
        assertTrue(srcSession.send(createMessage("msg")).isAccepted());
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertEquals(1, reply.getNumErrors());
        assertEquals(ErrorCode.ILLEGAL_ROUTE, reply.getError(0).getCode());
        log.log(Level.INFO, "Finished: requireThatNullRouteIsCaught");
    }

    @Test
    public void requireThatEmptyRouteIsCaught() {
        log.log(Level.INFO, "Starting: requireThatEmptyRouteIsCaught");
        assertTrue(srcSession.send(createMessage("msg"), new Route()).isAccepted());
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertEquals(1, reply.getNumErrors());
        assertEquals(ErrorCode.ILLEGAL_ROUTE, reply.getError(0).getCode());
        log.log(Level.INFO, "Finished: requireThatEmptyRouteIsCaught");
    }

    @Test
    public void requireThatHopNameIsExpanded() {
        log.log(Level.INFO, "Starting: requireThatHopNameIsExpanded");
        srcServer.setupRouting(new RoutingTableSpec(SimpleProtocol.NAME)
                                       .addHop(new HopSpec("dst", dstSessName)));
        assertTrue(srcSession.send(createMessage("msg"), Route.parse("dst")).isAccepted());
        Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60);
        assertNotNull(msg);
        dstSession.acknowledge(msg);
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertFalse(reply.hasErrors());
        log.log(Level.INFO, "Finished: requireThatHopNameIsExpanded");
    }

    @Test
    public void requireThatRouteDirectiveWorks() {
        log.log(Level.INFO, "Starting: requireThatRouteDirectiveWorks");
        srcServer.setupRouting(new RoutingTableSpec(SimpleProtocol.NAME)
                                       .addRoute(new RouteSpec("dst").addHop(dstSessName))
                                       .addHop(new HopSpec("dir", "route:dst")));
        assertTrue(srcSession.send(createMessage("msg"), Route.parse("dir")).isAccepted());
        Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60);
        assertNotNull(msg);
        dstSession.acknowledge(msg);
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertFalse(reply.hasErrors());
        log.log(Level.INFO, "Finished: requireThatRouteDirectiveWorks");
    }

    @Test
    public void requireThatRouteNameIsExpanded() {
        log.log(Level.INFO, "Starting: requireThatRouteNameIsExpanded");
        srcServer.setupRouting(new RoutingTableSpec(SimpleProtocol.NAME)
                                       .addRoute(new RouteSpec("dst").addHop(dstSessName)));
        assertTrue(srcSession.send(createMessage("msg"), Route.parse("dst")).isAccepted());
        Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60);
        assertNotNull(msg);
        dstSession.acknowledge(msg);
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertFalse(reply.hasErrors());
        log.log(Level.INFO, "Finished: requireThatRouteNameIsExpanded");
    }

    @Test
    public void requireThatHopResolutionOverflowIsCaught() {
        log.log(Level.INFO, "Starting: requireThatHopResolutionOverflowIsCaught");
        srcServer.setupRouting(new RoutingTableSpec(SimpleProtocol.NAME)
                                       .addHop(new HopSpec("foo", "bar"))
                                       .addHop(new HopSpec("bar", "foo")));
        assertTrue(srcSession.send(createMessage("msg"), Route.parse("foo")).isAccepted());
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertEquals(1, reply.getNumErrors());
        assertEquals(ErrorCode.ILLEGAL_ROUTE, reply.getError(0).getCode());
        log.log(Level.INFO, "Finished: requireThatHopResolutionOverflowIsCaught");
    }

    @Test
    public void requireThatRouteResolutionOverflowIsCaught() {
        log.log(Level.INFO, "Starting: requireThatRouteResolutionOverflowIsCaught");
        srcServer.setupRouting(new RoutingTableSpec(SimpleProtocol.NAME)
                                       .addRoute(new RouteSpec("foo").addHop("route:foo")));
        assertTrue(srcSession.send(createMessage("msg"), "foo").isAccepted());
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertEquals(1, reply.getNumErrors());
        assertEquals(ErrorCode.ILLEGAL_ROUTE, reply.getError(0).getCode());
        log.log(Level.INFO, "Finished: requireThatRouteResolutionOverflowIsCaught");
    }

    @Test
    public void requireThatRouteExpansionOnlyReplacesFirstHop() {
        log.log(Level.INFO, "Starting: requireThatRouteExpansionOnlyReplacesFirstHop");
        srcServer.setupRouting(new RoutingTableSpec(SimpleProtocol.NAME)
                                       .addRoute(new RouteSpec("foo").addHop(dstSessName).addHop("bar")));
        assertTrue(srcSession.send(createMessage("msg"), Route.parse("route:foo baz")).isAccepted());
        Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60);
        assertNotNull(msg);
        assertEquals(2, msg.getRoute().getNumHops());
        assertEquals("bar", msg.getRoute().getHop(0).toString());
        assertEquals("baz", msg.getRoute().getHop(1).toString());
        dstSession.acknowledge(msg);
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertFalse(reply.hasErrors());
        log.log(Level.INFO, "Finished: requireThatRouteExpansionOnlyReplacesFirstHop");
    }

    @Test
    public void requireThatErrorDirectiveWorks() {
        log.log(Level.INFO, "Starting: requireThatErrorDirectiveWorks");
        Route route = Route.parse("foo/bar/baz");
        route.getHop(0).setDirective(1, new ErrorDirective("err"));
        assertTrue(srcSession.send(createMessage("msg"), route).isAccepted());
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertEquals(1, reply.getNumErrors());
        assertEquals(ErrorCode.ILLEGAL_ROUTE, reply.getError(0).getCode());
        assertEquals("err", reply.getError(0).getMessage());
        log.log(Level.INFO, "Finished: requireThatErrorDirectiveWorks");
    }

    @Test
    public void requireThatIllegalSelectIsCaught() {
        log.log(Level.INFO, "Starting: requireThatIllegalSelectIsCaught");
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("Custom", new CustomPolicyFactory());
        srcServer.mb.putProtocol(protocol);
        Route route = Route.parse("[Custom: ]");
        assertNotNull(route);
        assertTrue(srcSession.send(createMessage("msg"), route).isAccepted());
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertEquals(1, reply.getNumErrors());
        assertEquals(ErrorCode.NO_SERVICES_FOR_ROUTE, reply.getError(0).getCode());
        log.log(Level.INFO, "Finished: requireThatIllegalSelectIsCaught");
    }

    @Test
    public void requireThatEmptySelectIsCaught() {
        log.log(Level.INFO, "Starting: requireThatEmptySelectIsCaught");
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("Custom", new CustomPolicyFactory());
        srcServer.mb.putProtocol(protocol);
        assertTrue(srcSession.send(createMessage("msg"), Route.parse("[Custom]")).isAccepted());
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertEquals(1, reply.getNumErrors());
        assertEquals(ErrorCode.NO_SERVICES_FOR_ROUTE, reply.getError(0).getCode());
        log.log(Level.INFO, "Finished: requireThatEmptySelectIsCaught");
    }

    @Test
    public void requireThatPolicySelectWorks() {
        log.log(Level.INFO, "Starting: requireThatPolicySelectWorks");
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("Custom", new CustomPolicyFactory());
        srcServer.mb.putProtocol(protocol);
        assertTrue(srcSession.send(createMessage("msg"), Route.parse("[Custom:"+dstSessName+"]")).isAccepted());
        Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60);
        assertNotNull(msg);
        dstSession.acknowledge(msg);
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertFalse(reply.hasErrors());
        log.log(Level.INFO, "Finished: requireThatPolicySelectWorks");
    }

    @Test
    public void requireThatTransientErrorsAreRetried() {
        log.log(Level.INFO, "Starting: requireThatTransientErrorsAreRetried");
        assertTrue(srcSession.send(createMessage("msg"), Route.parse(dstSessName)).isAccepted());
        Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60);
        assertNotNull(msg);
        Reply reply = new EmptyReply();
        reply.swapState(msg);
        reply.addError(new Error(ErrorCode.APP_TRANSIENT_ERROR, "err1"));
        dstSession.reply(reply);
        assertNotNull(msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60));
        reply = new EmptyReply();
        reply.swapState(msg);
        reply.addError(new Error(ErrorCode.APP_TRANSIENT_ERROR, "err2"));
        dstSession.reply(reply);
        assertNotNull(msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60));
        dstSession.acknowledge(msg);
        assertNotNull(reply = ((Receptor)srcSession.getReplyHandler()).getReply(60));
        System.out.println(reply.getTrace());
        assertFalse(reply.hasErrors());
        assertTrace(Arrays.asList("[APP_TRANSIENT_ERROR @ localhost]: err1",
                                  "-[APP_TRANSIENT_ERROR @ localhost]: err1",
                                  "[APP_TRANSIENT_ERROR @ localhost]: err2",
                                  "-[APP_TRANSIENT_ERROR @ localhost]: err2"),
                    reply.getTrace());
        log.log(Level.INFO, "Finished: requireThatTransientErrorsAreRetried");
    }

    @Test
    public void requireThatTransientErrorsAreRetriedWithPolicy() {
        log.log(Level.INFO, "Starting: requireThatTransientErrorsAreRetriedWithPolicy");
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("Custom", new CustomPolicyFactory());
        srcServer.mb.putProtocol(protocol);
        assertTrue(srcSession.send(createMessage("msg"), Route.parse("[Custom:"+dstSessName+"]")).isAccepted());
        Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60);
        assertNotNull(msg);
        Reply reply = new EmptyReply();
        reply.swapState(msg);
        reply.addError(new Error(ErrorCode.APP_TRANSIENT_ERROR, "err1"));
        dstSession.reply(reply);
        assertNotNull(msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60));
        reply = new EmptyReply();
        reply.swapState(msg);
        reply.addError(new Error(ErrorCode.APP_TRANSIENT_ERROR, "err2"));
        dstSession.reply(reply);
        assertNotNull(msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60));
        dstSession.acknowledge(msg);
        assertNotNull(reply = ((Receptor)srcSession.getReplyHandler()).getReply(60));
        System.out.println(reply.getTrace());
        assertFalse(reply.hasErrors());
        assertTrace(Arrays.asList("Source session accepted a 3 byte message. 1 message(s) now pending.",
                                  "Running routing policy 'Custom'.",
                                  "Selecting ["+dstSessName+"].",
                                  "Component '"+dstSessName+"' selected by policy 'Custom'.",
                                  "Resolving '"+dstSessName+"'.",
                                  "Sending message (version ${VERSION}) from client to '"+dstSessName+"'",
                                  "Message (type 1) received at 'dst' for session '"+sessName+"'.",
                                  "[APP_TRANSIENT_ERROR @ localhost]: err1",
                                  "Sending reply (version ${VERSION}) from 'dst'.",
                                  "Reply (type 0) received at client.",
                                  "Routing policy 'Custom' merging replies.",
                                  "Merged ["+dstSessName+"].",
                                  "Message scheduled for retry 1 in 0.0 seconds.",
                                  "Resender resending message.",
                                  "Running routing policy 'Custom'.",
                                  "Selecting ["+dstSessName+"].",
                                  "Component '"+dstSessName+"' selected by policy 'Custom'.",
                                  "Resolving '"+dstSessName+"'.",
                                  "Sending message (version ${VERSION}) from client to '"+dstSessName+"'",
                                  "Message (type 1) received at 'dst' for session '"+sessName+"'.",
                                  "[APP_TRANSIENT_ERROR @ localhost]: err2",
                                  "Sending reply (version ${VERSION}) from 'dst'.",
                                  "Reply (type 0) received at client.",
                                  "Routing policy 'Custom' merging replies.",
                                  "Merged ["+dstSessName+"].",
                                  "Message scheduled for retry 2 in 0.0 seconds.",
                                  "Resender resending message.",
                                  "Running routing policy 'Custom'.",
                                  "Selecting ["+dstSessName+"].",
                                  "Component '"+dstSessName+"' selected by policy 'Custom'.",
                                  "Resolving '"+dstSessName+"'.",
                                  "Sending message (version ${VERSION}) from client to '"+dstSessName+"'",
                                  "Message (type 1) received at 'dst' for session '"+sessName+"'.",
                                  "Sending reply (version ${VERSION}) from 'dst'.",
                                  "Reply (type 0) received at client.",
                                  "Routing policy 'Custom' merging replies.",
                                  "Merged ["+dstSessName+"].",
                                  "Source session received reply. 0 message(s) now pending."),
                    reply.getTrace());
        log.log(Level.INFO, "Finished: requireThatTransientErrorsAreRetriedWithPolicy");
    }

    @Test
    public void requireThatRetryCanBeDisabled() {
        log.log(Level.INFO, "Starting: requireThatRetryCanBeDisabled");
        retryPolicy.setEnabled(false);
        assertTrue(srcSession.send(createMessage("msg"), Route.parse(dstSessName)).isAccepted());
        Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60);
        assertNotNull(msg);
        Reply reply = new EmptyReply();
        reply.swapState(msg);
        reply.addError(new Error(ErrorCode.APP_TRANSIENT_ERROR, "err"));
        dstSession.reply(reply);
        reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertEquals(1, reply.getNumErrors());
        assertEquals(ErrorCode.APP_TRANSIENT_ERROR, reply.getError(0).getCode());
        log.log(Level.INFO, "Finished: requireThatRetryCanBeDisabled");
    }

    @Test
    public void requireThatRetryCallsSelect() {
        log.log(Level.INFO, "Starting: requireThatRetryCallsSelect");
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("Custom", new CustomPolicyFactory());
        srcServer.mb.putProtocol(protocol);
        assertTrue(srcSession.send(createMessage("msg"), Route.parse("[Custom:"+dstSessName+"]")).isAccepted());
        Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60);
        assertNotNull(msg);
        Reply reply = new EmptyReply();
        reply.swapState(msg);
        reply.addError(new Error(ErrorCode.APP_TRANSIENT_ERROR, "err"));
        dstSession.reply(reply);
        assertNotNull(msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60));
        dstSession.acknowledge(msg);
        assertNotNull(reply = ((Receptor)srcSession.getReplyHandler()).getReply(60));
        System.out.println(reply.getTrace());
        assertFalse(reply.hasErrors());
        assertTrace(Arrays.asList("Selecting ["+dstSessName+"].",
                                  "[APP_TRANSIENT_ERROR @ localhost]",
                                  "-[APP_TRANSIENT_ERROR @ localhost]",
                                  "Merged ["+dstSessName+"].",
                                  "Selecting ["+dstSessName+"].",
                                  "Sending reply",
                                  "Merged ["+dstSessName+"]."),
                    reply.getTrace());
        log.log(Level.INFO, "Finished: requireThatRetryCallsSelect");
    }

    @Test
    public void requireThatPolicyCanDisableReselectOnRetry() {
        log.log(Level.INFO, "Starting: requireThatPolicyCanDisableReselectOnRetry");
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("Custom", new CustomPolicyFactory(false));
        srcServer.mb.putProtocol(protocol);
        assertTrue(srcSession.send(createMessage("msg"), Route.parse("[Custom:"+dstSessName+"]")).isAccepted());
        Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60);
        assertNotNull(msg);
        Reply reply = new EmptyReply();
        reply.swapState(msg);
        reply.addError(new Error(ErrorCode.APP_TRANSIENT_ERROR, "err"));
        dstSession.reply(reply);
        assertNotNull(msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60));
        dstSession.acknowledge(msg);
        assertNotNull(reply = ((Receptor)srcSession.getReplyHandler()).getReply(60));
        System.out.println(reply.getTrace());
        assertFalse(reply.hasErrors());
        assertTrace(Arrays.asList("Selecting ["+dstSessName+"].",
                                  "[APP_TRANSIENT_ERROR @ localhost]",
                                  "-[APP_TRANSIENT_ERROR @ localhost]",
                                  "Merged ["+dstSessName+"].",
                                  "-Selecting ["+dstSessName+"].",
                                  "Sending reply",
                                  "Merged ["+dstSessName+"]."),
                    reply.getTrace());
        log.log(Level.INFO, "Finished: requireThatPolicyCanDisableReselectOnRetry");
    }

    @Test
    public void requireThatPolicyCanConsumeErrors() {
        log.log(Level.INFO, "Starting: requireThatPolicyCanConsumeErrors");
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("Custom", new CustomPolicyFactory(true, ErrorCode.NO_ADDRESS_FOR_SERVICE));
        srcServer.mb.putProtocol(protocol);
        retryPolicy.setEnabled(false);
        assertTrue(srcSession.send(createMessage("msg"), Route.parse("[Custom:"+dstSessName+",dst/unknown]")).isAccepted());
        Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60);
        assertNotNull(msg);
        dstSession.acknowledge(msg);
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertEquals(1, reply.getNumErrors());
        assertEquals(ErrorCode.NO_ADDRESS_FOR_SERVICE, reply.getError(0).getCode());
        assertTrace(Arrays.asList("Selecting ["+dstSessName+", dst/unknown].",
                                  "[NO_ADDRESS_FOR_SERVICE @ localhost]",
                                  "Sending reply",
                                  "Merged ["+dstSessName+", dst/unknown]."),
                    reply.getTrace());
        log.log(Level.INFO, "Finished: requireThatPolicyCanConsumeErrors");
    }

    @Test
    public void requireThatPolicyOnlyConsumesDeclaredErrors() {
        log.log(Level.INFO, "Starting: requireThatPolicyOnlyConsumesDeclaredErrors");
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("Custom", new CustomPolicyFactory());
        srcServer.mb.putProtocol(protocol);
        retryPolicy.setEnabled(false);
        assertTrue(srcSession.send(createMessage("msg"), Route.parse("[Custom:dst/unknown]")).isAccepted());
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertEquals(1, reply.getNumErrors());
        assertEquals(ErrorCode.NO_ADDRESS_FOR_SERVICE, reply.getError(0).getCode());
        assertTrace(Arrays.asList("Selecting [dst/unknown].",
                                  "[NO_ADDRESS_FOR_SERVICE @ localhost]",
                                  "Merged [dst/unknown]."),
                    reply.getTrace());
        log.log(Level.INFO, "Finished: requireThatPolicyOnlyConsumesDeclaredErrors");
    }

    @Test
    public void requireThatPolicyCanExpandToPolicy() {
        log.log(Level.INFO, "Starting: requireThatPolicyCanExpandToPolicy");
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("Custom", new CustomPolicyFactory(true, ErrorCode.NO_ADDRESS_FOR_SERVICE));
        srcServer.mb.putProtocol(protocol);
        retryPolicy.setEnabled(false);
        assertTrue(srcSession.send(createMessage("msg"),
                                   Route.parse("[Custom:[Custom:"+dstSessName+"],[Custom:dst/unknown]]")).isAccepted());
        Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60);
        assertNotNull(msg);
        dstSession.acknowledge(msg);
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertEquals(1, reply.getNumErrors());
        assertEquals(ErrorCode.NO_ADDRESS_FOR_SERVICE, reply.getError(0).getCode());
        log.log(Level.INFO, "Finished: requireThatPolicyCanExpandToPolicy");
    }

    @Test
    public void requireThatReplyCanBeRemovedFromChildNodes() {
        log.log(Level.INFO, "Starting: requireThatReplyCanBeRemovedFromChildNodes");
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("Custom", new SimpleProtocol.PolicyFactory() {

            @Override
            public RoutingPolicy create(String param) {
                return new RemoveReplyPolicy(true,
                                             Arrays.asList(ErrorCode.NO_ADDRESS_FOR_SERVICE),
                                             CustomPolicyFactory.parseRoutes(param),
                                             0);
            }
        });
        srcServer.mb.putProtocol(protocol);
        retryPolicy.setEnabled(false);
        assertTrue(srcSession.send(createMessage("msg"),
                                   Route.parse("[Custom:[Custom:"+dstSessName+"],[Custom:dst/unknown]]")).isAccepted());
        Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60);
        assertNotNull(msg);
        dstSession.acknowledge(msg);
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertFalse(reply.hasErrors());
        assertTrace(Arrays.asList("[NO_ADDRESS_FOR_SERVICE @ localhost]",
                                  "-[NO_ADDRESS_FOR_SERVICE @ localhost]",
                                  "Sending message",
                                  "-Sending message"),
                    reply.getTrace());
        log.log(Level.INFO, "Finished: requireThatReplyCanBeRemovedFromChildNodes");
    }

    @Test
    public void requireThatSetReplyWorks() {
        log.log(Level.INFO, "Starting: requireThatSetReplyWorks");
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("Select", new CustomPolicyFactory(true, ErrorCode.APP_FATAL_ERROR));
        protocol.addPolicyFactory("SetReply", new SimpleProtocol.PolicyFactory() {

            @Override
            public RoutingPolicy create(String param) {
                return new SetReplyPolicy(true, Arrays.asList(ErrorCode.APP_FATAL_ERROR), param);
            }
        });
        srcServer.mb.putProtocol(protocol);
        retryPolicy.setEnabled(false);
        assertTrue(
                srcSession.send(createMessage("msg"), Route.parse("[Select:[SetReply:foo],"+dstSessName+"]")).isAccepted());
        Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60);
        assertNotNull(msg);
        dstSession.acknowledge(msg);
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertEquals(1, reply.getNumErrors());
        assertEquals(ErrorCode.APP_FATAL_ERROR, reply.getError(0).getCode());
        assertEquals("foo", reply.getError(0).getMessage());
        log.log(Level.INFO, "Finished: requireThatSetReplyWorks");
    }

    @Test
    public void requireThatReplyCanBeReusedOnRetry() {
        log.log(Level.INFO, "Starting: requireThatReplyCanBeReusedOnRetry");
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("ReuseReply", new SimpleProtocol.PolicyFactory() {

            @Override
            public RoutingPolicy create(String param) {
                return new ReuseReplyPolicy(false,
                                            Arrays.asList(ErrorCode.APP_FATAL_ERROR),
                                            CustomPolicyFactory.parseRoutes(param));
            }
        });
        protocol.addPolicyFactory("SetReply", new SimpleProtocol.PolicyFactory() {

            @Override
            public RoutingPolicy create(String param) {
                return new SetReplyPolicy(false,
                                          Arrays.asList(ErrorCode.APP_FATAL_ERROR),
                                          param);
            }
        });
        srcServer.mb.putProtocol(protocol);
        assertTrue(srcSession.send(createMessage("msg"),
                                   Route.parse("[ReuseReply:[SetReply:foo],"+dstSessName+"]")).isAccepted());
        Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60);
        assertNotNull(msg);
        Reply reply = new EmptyReply();
        reply.swapState(msg);
        reply.addError(new Error(ErrorCode.APP_TRANSIENT_ERROR, "dst"));
        dstSession.reply(reply);
        assertNotNull(msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60));
        dstSession.acknowledge(msg);
        assertNotNull(reply = ((Receptor)srcSession.getReplyHandler()).getReply(60));
        System.out.println(reply.getTrace());
        assertFalse(reply.hasErrors());
        log.log(Level.INFO, "Finished: requireThatReplyCanBeReusedOnRetry");
    }

    @Test
    public void requireThatReplyCanBeRemovedAndRetried() {
        log.log(Level.INFO, "Starting: requireThatReplyCanBeRemovedAndRetried");
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("RemoveReply", new SimpleProtocol.PolicyFactory() {

            @Override
            public RoutingPolicy create(String param) {
                return new RemoveReplyPolicy(false,
                                             Arrays.asList(ErrorCode.APP_TRANSIENT_ERROR),
                                             CustomPolicyFactory.parseRoutes(param),
                                             0);
            }
        });
        protocol.addPolicyFactory("SetReply", new SimpleProtocol.PolicyFactory() {

            @Override
            public RoutingPolicy create(String param) {
                return new SetReplyPolicy(false,
                                          Arrays.asList(ErrorCode.APP_TRANSIENT_ERROR, ErrorCode.APP_FATAL_ERROR),
                                          param);
            }
        });
        srcServer.mb.putProtocol(protocol);
        assertTrue(srcSession
                           .send(createMessage("msg"), Route.parse("[RemoveReply:[SetReply:foo],"+dstSessName+"]")).isAccepted());
        Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60);
        assertNotNull(msg);
        dstSession.acknowledge(msg);
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertEquals(1, reply.getNumErrors());
        assertEquals(ErrorCode.APP_FATAL_ERROR, reply.getError(0).getCode());
        assertEquals("foo", reply.getError(0).getMessage());
        assertTrace(Arrays.asList("Resolving '[SetReply:foo]'.",
                                  "Resolving '"+dstSessName+"'.",
                                  "Resender resending message.",
                                  "Resolving '"+dstSessName+"'.",
                                  "Resolving '[SetReply:foo]'."),
                    reply.getTrace());
        log.log(Level.INFO, "Finished: requireThatReplyCanBeRemovedAndRetried");
    }

    @Test
    public void requireThatIgnoreResultWorks() {
        log.log(Level.INFO, "Starting: requireThatIgnoreResultWorks");
        assertTrue(srcSession.send(createMessage("msg"), Route.parse("?"+dstSessName)).isAccepted());
        Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60);
        assertNotNull(msg);
        Reply reply = new EmptyReply();
        reply.swapState(msg);
        reply.addError(new Error(ErrorCode.APP_FATAL_ERROR, "dst"));
        dstSession.reply(reply);
        assertNotNull(reply = ((Receptor)srcSession.getReplyHandler()).getReply(60));
        System.out.println(reply.getTrace());
        assertFalse(reply.hasErrors());
        assertTrace(Arrays.asList("Not waiting for a reply from '"+dstSessName+"'."),
                    reply.getTrace());
        log.log(Level.INFO, "Finished: requireThatIgnoreResultWorks");
    }

    @Test
    public void requireThatIgnoreResultCanBeSetInHopBlueprint() {
        log.log(Level.INFO, "Starting: requireThatIgnoreResultCanBeSetInHopBlueprint");
        srcServer.setupRouting(new RoutingTableSpec(SimpleProtocol.NAME)
                                       .addHop(new HopSpec("foo", dstSessName).setIgnoreResult(true)));
        assertTrue(srcSession.send(createMessage("msg"), Route.parse("foo")).isAccepted());
        Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60);
        assertNotNull(msg);
        Reply reply = new EmptyReply();
        reply.swapState(msg);
        reply.addError(new Error(ErrorCode.APP_FATAL_ERROR, "dst"));
        dstSession.reply(reply);
        assertNotNull(reply = ((Receptor)srcSession.getReplyHandler()).getReply(60));
        System.out.println(reply.getTrace());
        assertFalse(reply.hasErrors());
        assertTrace(Arrays.asList("Not waiting for a reply from '"+dstSessName+"'."),
                    reply.getTrace());
        log.log(Level.INFO, "Finished: requireThatIgnoreResultCanBeSetInHopBlueprint");
    }

    @Test
    public void requireThatIgnoreFlagPersistsThroughHopLookup() {
        log.log(Level.INFO, "Starting: requireThatIgnoreFlagPersistsThroughHopLookup");
        setupRouting(new RoutingTableSpec(SimpleProtocol.NAME).addHop(new HopSpec("foo", "dst/unknown")));
        assertSend("?foo");
        assertTrace("Ignoring errors in reply.");
        log.log(Level.INFO, "Finished: requireThatIgnoreFlagPersistsThroughHopLookup");
    }

    @Test
    public void requireThatIgnoreFlagPersistsThroughRouteLookup() {
        log.log(Level.INFO, "Starting: requireThatIgnoreFlagPersistsThroughRouteLookup");
        setupRouting(new RoutingTableSpec(SimpleProtocol.NAME).addRoute(new RouteSpec("foo").addHop("dst/unknown")));
        assertSend("?foo");
        assertTrace("Ignoring errors in reply.");
        log.log(Level.INFO, "Finished: requireThatIgnoreFlagPersistsThroughRouteLookup");
    }

    @Test
    public void requireThatIgnoreFlagPersistsThroughPolicySelect() {
        log.log(Level.INFO, "Starting: requireThatIgnoreFlagPersistsThroughPolicySelect");
        setupPolicy("Custom", MyPolicy.newSelectAndMerge("dst/unknown"));
        assertSend("?[Custom]");
        assertTrace("Ignoring errors in reply.");
        log.log(Level.INFO, "Finished: requireThatIgnoreFlagPersistsThroughPolicySelect");
    }

    @Test
    public void requireThatIgnoreFlagIsSerializedWithMessage() {
        log.log(Level.INFO, "Starting: requireThatIgnoreFlagIsSerializedWithMessage");
        assertSend(dstSessName+" foo ?bar");
        Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60);
        assertNotNull(msg);
        Route route = msg.getRoute();
        assertEquals(2, route.getNumHops());
        Hop hop = route.getHop(0);
        assertEquals("foo", hop.toString());
        assertFalse(hop.getIgnoreResult());
        hop = route.getHop(1);
        assertEquals("?bar", hop.toString());
        assertTrue(hop.getIgnoreResult());
        dstSession.acknowledge(msg);
        assertTrace("-Ignoring errors in reply.");
        log.log(Level.INFO, "Finished: requireThatIgnoreFlagIsSerializedWithMessage");
    }

    @Test
    public void requireThatIgnoreFlagDoesNotInterfere() {
        log.log(Level.INFO, "Starting: requireThatIgnoreFlagDoesNotInterfere");
        setupPolicy("Custom", MyPolicy.newSelectAndMerge(dstSessName));
        assertSend("?[Custom]");
        assertTrace("-Ignoring errors in reply.");
        log.log(Level.INFO, "Finished: requireThatIgnoreFlagDoesNotInterfere");
    }

    @Test
    public void requireThatEmptySelectionCanBeIgnored() {
        log.log(Level.INFO, "Starting: requireThatEmptySelectionCanBeIgnored");
        setupPolicy("Custom", MyPolicy.newEmptySelection());
        assertSend("?[Custom]");
        assertTrace("Ignoring errors in reply.");
        log.log(Level.INFO, "Finished: requireThatEmptySelectionCanBeIgnored");
    }

    @Test
    public void requireThatSelectErrorCanBeIgnored() {
        log.log(Level.INFO, "Starting: requireThatSelectErrorCanBeIgnored");
        setupPolicy("Custom", MyPolicy.newSelectError(ErrorCode.APP_FATAL_ERROR, "foo"));
        assertSend("?[Custom]");
        assertTrace("Ignoring errors in reply.");
        log.log(Level.INFO, "Finished: requireThatSelectErrorCanBeIgnored");
    }

    @Test
    public void requireThatSelectExceptionCanBeIgnored() {
        log.log(Level.INFO, "Starting: requireThatSelectExceptionCanBeIgnored");
        setupPolicy("Custom", MyPolicy.newSelectException(new RuntimeException()));
        assertSend("?[Custom]");
        assertTrace("Ignoring errors in reply.");
        log.log(Level.INFO, "Finished: requireThatSelectExceptionCanBeIgnored");
    }

    @Test
    public void requireThatSelectAndThrowCanBeIgnored() {
        log.log(Level.INFO, "Starting: requireThatSelectAndThrowCanBeIgnored");
        setupPolicy("Custom", MyPolicy.newSelectAndThrow(dstSessName, new RuntimeException()));
        assertSend("?[Custom]");
        assertTrace("Ignoring errors in reply.");
        log.log(Level.INFO, "Finished: requireThatSelectAndThrowCanBeIgnored");
    }

    @Test
    public void requireThatEmptyMergeCanBeIgnored() {
        log.log(Level.INFO, "Starting: requireThatEmptyMergeCanBeIgnored");
        setupPolicy("Custom", MyPolicy.newEmptyMerge(dstSessName));
        assertSend("?[Custom]");
        assertAcknowledge();
        assertTrace("Ignoring errors in reply.");
        log.log(Level.INFO, "Finished: requireThatEmptyMergeCanBeIgnored");
    }

    @Test
    public void requireThatMergeErrorCanBeIgnored() {
        log.log(Level.INFO, "Starting: requireThatMergeErrorCanBeIgnored");
        setupPolicy("Custom", MyPolicy.newMergeError(dstSessName, ErrorCode.APP_FATAL_ERROR, "foo"));
        assertSend("?[Custom]");
        assertAcknowledge();
        assertTrace("Ignoring errors in reply.");
        log.log(Level.INFO, "Finished: requireThatMergeErrorCanBeIgnored");
    }

    @Test
    public void requireThatMergeExceptionCanBeIgnored() {
        log.log(Level.INFO, "Starting: requireThatMergeExceptionCanBeIgnored");
        setupPolicy("Custom", MyPolicy.newMergeException(dstSessName, new RuntimeException()));
        assertSend("?[Custom]");
        assertAcknowledge();
        assertTrace("Ignoring errors in reply.");
        log.log(Level.INFO, "Finished: requireThatMergeExceptionCanBeIgnored");
    }

    @Test
    public void requireThatMergeAndThrowCanBeIgnored() {
        log.log(Level.INFO, "Starting: requireThatMergeAndThrowCanBeIgnored");
        setupPolicy("Custom", MyPolicy.newMergeAndThrow(dstSessName, new RuntimeException()));
        assertSend("?[Custom]");
        assertAcknowledge();
        assertTrace("Ignoring errors in reply.");
        log.log(Level.INFO, "Finished: requireThatMergeAndThrowCanBeIgnored");
    }

    @Test
    public void requireThatAllocServiceAddressCanBeIgnored() {
        log.log(Level.INFO, "Starting: requireThatAllocServiceAddressCanBeIgnored");
        assertSend("?dst/unknown");
        assertTrace("Ignoring errors in reply.");
        log.log(Level.INFO, "Finished: requireThatAllocServiceAddressCanBeIgnored");
    }

    @Test
    public void requireThatDepthLimitCanBeIgnored() {
        log.log(Level.INFO, "Starting: requireThatDepthLimitCanBeIgnored");
        setupPolicy("Custom", MyPolicy.newSelectAndMerge("[Custom]"));
        assertSend("?[Custom]");
        assertTrace("Ignoring errors in reply.");
        log.log(Level.INFO, "Finished: requireThatDepthLimitCanBeIgnored");
    }

    @Test
    public void requireThatRouteCanBeEmptyInDestination() {
        log.log(Level.INFO, "Starting: requireThatRouteCanBeEmptyInDestination");
        assertTrue(srcSession.send(createMessage("msg"), Route.parse(dstSessName)).isAccepted());
        Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60);
        assertNotNull(msg);
        assertNull(msg.getRoute());
        dstSession.acknowledge(msg);
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        log.log(Level.INFO, "Finished: requireThatRouteCanBeEmptyInDestination");
    }

    @Test
    public void requireThatOnlyActiveNodesAreAborted() {
        log.log(Level.INFO, "Starting: requireThatOnlyActiveNodesAreAborted");
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("Custom", new CustomPolicyFactory(false));
        protocol.addPolicyFactory("SetReply", new SimpleProtocol.PolicyFactory() {

            @Override
            public RoutingPolicy create(String param) {
                return new SetReplyPolicy(false,
                                          Arrays.asList(ErrorCode.APP_TRANSIENT_ERROR,
                                                        ErrorCode.APP_TRANSIENT_ERROR,
                                                        ErrorCode.APP_FATAL_ERROR),
                                          param);
            }
        });
        srcServer.mb.putProtocol(protocol);
        assertTrue(srcSession.send(createMessage("msg"),
                                   Route.parse("[Custom:[SetReply:foo],?bar,"+dstSessName+"]")).isAccepted());
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertEquals(2, reply.getNumErrors());
        assertEquals(ErrorCode.APP_FATAL_ERROR, reply.getError(0).getCode());
        assertEquals(ErrorCode.SEND_ABORTED, reply.getError(1).getCode());
        log.log(Level.INFO, "Finished: requireThatOnlyActiveNodesAreAborted");
    }

    @Test
    public void requireThatTimeoutWorks() {
        log.log(Level.INFO, "Starting: requireThatTimeoutWorks");
        retryPolicy.setBaseDelay(0.01);
        srcSession.setTimeout(0.5);
        assertTrue(srcSession.send(createMessage("msg"), Route.parse("dst/unknown")).isAccepted());
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertEquals(2, reply.getNumErrors());
        assertEquals(ErrorCode.NO_ADDRESS_FOR_SERVICE, reply.getError(0).getCode());
        assertEquals(ErrorCode.TIMEOUT, reply.getError(1).getCode());
        log.log(Level.INFO, "Finished: requireThatTimeoutWorks");
    }

    @Test
    public void requireThatUnknownPolicyIsCaught() {
        log.log(Level.INFO, "Starting: requireThatUnknownPolicyIsCaught");
        assertTrue(srcSession.send(createMessage("msg"), Route.parse("[Unknown]")).isAccepted());
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertEquals(1, reply.getNumErrors());
        assertEquals(ErrorCode.UNKNOWN_POLICY, reply.getError(0).getCode());
        log.log(Level.INFO, "Finished: requireThatUnknownPolicyIsCaught");
    }

    private SimpleProtocol.PolicyFactory exceptionOnSelectThrowingMockFactory() {
        return new SimpleProtocol.PolicyFactory() {

            @Override
            public RoutingPolicy create(String param) {
                return new RoutingPolicy() {

                    @Override
                    public void select(RoutingContext context) {
                        throw new RuntimeException("69");
                    }

                    @Override
                    public void merge(RoutingContext context) {
                    }

                    @Override
                    public void destroy() {
                    }
                };
            }
        };
    }

    @Test
    public void requireThatSelectExceptionIsCaught() {
        log.log(Level.INFO, "Starting: requireThatSelectExceptionIsCaught");
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("Custom", exceptionOnSelectThrowingMockFactory());
        srcServer.mb.putProtocol(protocol);
        assertTrue(srcSession.send(createMessage("msg"), Route.parse("[Custom]")).isAccepted());
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertEquals(1, reply.getNumErrors());
        assertEquals(ErrorCode.POLICY_ERROR, reply.getError(0).getCode());
        assertTrue(reply.getError(0).getMessage().contains("69"));
        log.log(Level.INFO, "Finished: requireThatSelectExceptionIsCaught");
    }

    @Test
    public void selectExceptionIncludesStackTraceInMessage() {
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("Custom", exceptionOnSelectThrowingMockFactory());
        srcServer.mb.putProtocol(protocol);
        assertTrue(srcSession.send(createMessage("msg"), Route.parse("[Custom]")).isAccepted());
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertEquals(ErrorCode.POLICY_ERROR, reply.getError(0).getCode());
        // Attempting any sort of full matching of the stack trace is brittle, so
        // simplify by assuming any message which mentions the source file of the
        // originating exception is good to go.
        assertTrue(reply.getError(0).getMessage().contains("RoutingTestCase"));
    }

    @Test
    public void requireThatMergeExceptionIsCaught() {
        log.log(Level.INFO, "Starting: requireThatMergeExceptionIsCaught");
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("Custom", new SimpleProtocol.PolicyFactory() {

            @Override
            public RoutingPolicy create(String param) {
                return new RoutingPolicy() {

                    @Override
                    public void select(RoutingContext context) {
                        context.addChild(Route.parse(dstSessName));
                    }

                    @Override
                    public void merge(RoutingContext context) {
                        throw new RuntimeException("69");
                    }

                    @Override
                    public void destroy() {

                    }
                };
            }
        });
        srcServer.mb.putProtocol(protocol);
        assertTrue(srcSession.send(createMessage("msg"), Route.parse("[Custom]")).isAccepted());
        Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60);
        assertNotNull(msg);
        dstSession.acknowledge(msg);
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertEquals(1, reply.getNumErrors());
        assertEquals(ErrorCode.POLICY_ERROR, reply.getError(0).getCode());
        assertTrue(reply.getError(0).getMessage().contains("69"));
        log.log(Level.INFO, "Finished: requireThatMergeExceptionIsCaught");
    }

    ////////////////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    ////////////////////////////////////////////////////////////////////////////////

    private static Message createMessage(String msg) {
        SimpleMessage ret = new SimpleMessage(msg);
        ret.getTrace().setLevel(9);
        return ret;
    }

    private void setupRouting(RoutingTableSpec spec) {
        srcServer.setupRouting(spec);
    }

    private void setupPolicy(String policyName, SimpleProtocol.PolicyFactory policyFactory) {
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory(policyName, policyFactory);
        srcServer.mb.putProtocol(protocol);
    }

    private void assertSend(String route) {
        assertTrue(srcSession.send(createMessage("msg").setRoute(Route.parse(route))).isAccepted());
    }

    private void assertAcknowledge() {
        Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60);
        assertNotNull(msg);
        dstSession.acknowledge(msg);
    }

    private void assertTrace(String... expectedTrace) {
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        assertFalse(reply.hasErrors());
        assertTrace(Arrays.asList(expectedTrace), reply.getTrace());
    }

    public static void assertTrace(List<String> expected, Trace trace) {
        String actual = trace.toString();
        for (int i = 0, pos = -1; i < expected.size(); ++i) {
            String line = expected.get(i).replaceFirst("\\$\\{VERSION\\}", Vtag.currentVersion.toString());
            if (line.charAt(0) == '-') {
                String str = line.substring(1);
                assertTrue("Line " + i + " '" + str + "' not expected.",
                           actual.indexOf(str, pos + 1) < 0);
            } else {
                pos = actual.indexOf(line, pos + 1);
                assertTrue("Line " + i + " '" + line + "' missing.", pos >= 0);
            }
        }
    }

    private static class RemoveReplyPolicy extends CustomPolicy {

        final int idxRemove;

        public RemoveReplyPolicy(boolean selectOnRetry, List<Integer> consumableErrors, List<Route> routes,
                                 int idxRemove) {
            super(selectOnRetry, consumableErrors, routes);
            this.idxRemove = idxRemove;
        }

        public void merge(RoutingContext ctx) {
            ctx.setReply(ctx.getChildIterator().skip(idxRemove).removeReply());
        }

        @Override
        public void destroy() {
        }
    }

    private static class ReuseReplyPolicy extends CustomPolicy {

        final List<Integer> errorMask = new ArrayList<>();

        public ReuseReplyPolicy(boolean selectOnRetry, List<Integer> errorMask,
                                List<Route> routes) {
            super(selectOnRetry, errorMask, routes);
            this.errorMask.addAll(errorMask);
        }

        public void merge(RoutingContext ctx) {
            Reply ret = new EmptyReply();
            int idx = 0;
            int idxFirstOk = -1;
            for (RoutingNodeIterator it = ctx.getChildIterator();
                 it.isValid(); it.next(), ++idx) {
                Reply ref = it.getReplyRef();
                if (!ref.hasErrors()) {
                    if (idxFirstOk < 0) {
                        idxFirstOk = idx;
                    }
                } else {
                    for (int i = 0; i < ref.getNumErrors(); ++i) {
                        Error err = ref.getError(i);
                        if (!errorMask.contains(err.getCode())) {
                            ret.addError(err);
                        }
                    }
                }
            }
            if (ret.hasErrors()) {
                ctx.setReply(ret);
            } else {
                ctx.setReply(ctx.getChildIterator().skip(idxFirstOk).removeReply());
            }
        }

        @Override
        public void destroy() {
        }
    }

    private static class SetReplyPolicy implements RoutingPolicy {

        final boolean selectOnRetry;
        final List<Integer> errors = new ArrayList<>();
        final String param;
        int idx = 0;

        public SetReplyPolicy(boolean selectOnRetry, List<Integer> errors, String param) {
            this.selectOnRetry = selectOnRetry;
            this.errors.addAll(errors);
            this.param = param;
        }

        public void select(RoutingContext ctx) {
            int idx = this.idx++;
            int err = errors.get(idx < errors.size() ? idx : errors.size() - 1);
            if (err != ErrorCode.NONE) {
                ctx.setError(err, param);
            } else {
                ctx.setReply(new EmptyReply());
            }
            ctx.setSelectOnRetry(selectOnRetry);
        }

        public void merge(RoutingContext ctx) {
            Reply reply = new EmptyReply();
            reply.addError(new Error(ErrorCode.FATAL_ERROR,
                                     "Merge should not be called when select() sets a reply."));
            ctx.setReply(reply);
        }

        public void destroy() {
        }
    }

    private static class MyPolicy implements SimpleProtocol.PolicyFactory {

        final Route selectRoute;
        final Reply selectReply;
        final Reply mergeReply;
        final RuntimeException selectException;
        final RuntimeException mergeException;
        final boolean mergeFromChild;

        MyPolicy(Route selectRoute, Reply selectReply, RuntimeException selectException,
                 Reply mergeReply, RuntimeException mergeException, boolean mergeFromChild) {
            this.selectRoute = selectRoute;
            this.selectReply = selectReply;
            this.selectException = selectException;
            this.mergeReply = mergeReply;
            this.mergeException = mergeException;
            this.mergeFromChild = mergeFromChild;
        }

        @Override
        public RoutingPolicy create(String param) {
            return new RoutingPolicy() {

                @Override
                public void select(RoutingContext context) {
                    if (selectRoute != null) {
                        context.addChild(selectRoute);
                    }
                    if (selectReply != null) {
                        context.setReply(selectReply);
                    }
                    if (selectException != null) {
                        throw selectException;
                    }
                }

                @Override
                public void merge(RoutingContext context) {
                    if (mergeReply != null) {
                        context.setReply(mergeReply);
                    } else if (mergeFromChild) {
                        context.setReply(context.getChildIterator().removeReply());
                    }
                    if (mergeException != null) {
                        throw mergeException;
                    }
                }

                @Override
                public void destroy() {

                }
            };
        }

        static Reply newErrorReply(int errCode, String errMessage) {
            Reply reply = new EmptyReply();
            reply.addError(new Error(errCode, errMessage));
            return reply;
        }

        static MyPolicy newSelectAndMerge(String select) {
            return new MyPolicy(Route.parse(select), null, null, null, null, true);
        }

        static MyPolicy newEmptySelection() {
            return new MyPolicy(null, null, null, null, null, false);
        }

        static MyPolicy newSelectError(int errCode, String errMessage) {
            return new MyPolicy(null, newErrorReply(errCode, errMessage), null, null, null, false);
        }

        static MyPolicy newSelectException(RuntimeException e) {
            return new MyPolicy(null, null, e, null, null, false);
        }

        static MyPolicy newSelectAndThrow(String select, RuntimeException e) {
            return new MyPolicy(Route.parse(select), null, e, null, null, false);
        }

        static MyPolicy newEmptyMerge(String select) {
            return new MyPolicy(Route.parse(select), null, null, null, null, false);
        }

        static MyPolicy newMergeError(String select, int errCode, String errMessage) {
            return new MyPolicy(Route.parse(select), null, null, newErrorReply(errCode, errMessage), null, false);
        }

        static MyPolicy newMergeException(String select, RuntimeException e) {
            return new MyPolicy(Route.parse(select), null, null, null, e, false);
        }

        static MyPolicy newMergeAndThrow(String select, RuntimeException e) {
            return new MyPolicy(Route.parse(select), null, null, null, e, true);
        }
    }

}
