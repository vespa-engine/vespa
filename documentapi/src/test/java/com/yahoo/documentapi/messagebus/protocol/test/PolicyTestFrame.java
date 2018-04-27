// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol.test;

import com.yahoo.document.DocumentTypeManager;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.network.Identity;
import com.yahoo.messagebus.network.Network;
import com.yahoo.messagebus.network.ServiceAddress;
import com.yahoo.messagebus.network.rpc.RPCNetwork;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.messagebus.network.rpc.test.TestServer;
import com.yahoo.messagebus.routing.*;
import com.yahoo.messagebus.test.Receptor;
import com.yahoo.messagebus.test.SimpleProtocol;
import com.yahoo.messagebus.test.SimpleReply;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * This is a utility class to allow easier policy test cases. The most important reason to use this is to make sure that
 * each test uses a "clean" mbus and slobrok instance.
 *
 * @author Simon Thoresen
 */
@SuppressWarnings("deprecation")
public class PolicyTestFrame {

    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    private String identity;
    private Slobrok slobrok;
    private MessageBus mbus;
    private MyNetwork net;
    private Message msg = null;
    private HopSpec hop = null;
    private Receptor handler = new Receptor();

    /**
     * Create an anonymous test frame.
     *
     * @param documentMgr The document manager to use.
     */
    public PolicyTestFrame(DocumentTypeManager documentMgr) {
        this("anonymous", documentMgr);
    }

    /**
     * Create a named test frame.
     *
     * @param identity    The identity to use for the server.
     * @param documentMgr The document manager to use.
     */
    public PolicyTestFrame(String identity, DocumentTypeManager documentMgr) {
        this.identity = identity;
        try {
            slobrok = new Slobrok();
        } catch (ListenFailedException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        net = new MyNetwork(new RPCNetworkParams()
                .setIdentity(new Identity(identity))
                .setSlobrokConfigId(TestServer.getSlobrokConfig(slobrok)));
        mbus = new MessageBus(net, new MessageBusParams()
                .addProtocol(new DocumentProtocol(documentMgr)));
    }

    /**
     * Create a test frame running on the same slobrok and mbus as another.
     *
     * @param frame The frame whose internals to share.
     */
    public PolicyTestFrame(PolicyTestFrame frame) {
        identity = frame.identity;
        slobrok = frame.slobrok;
        net = frame.net;
        mbus = frame.mbus;
    }

    // Inherit doc from Object.
    @Override
    public void finalize() throws Throwable {
        destroy();
        super.finalize();
    }

    /**
     * Sets the destroyed flag to true. The very first time this method is called, it cleans up all its dependencies.
     * Even if you retain a reference to this object, all of its content is allowed to be garbage collected.
     */
    public void destroy() {
        if (!destroyed.getAndSet(true)) {
            mbus.destroy();
            mbus = null;
            net = null;

            slobrok.stop();
            slobrok = null;
        }
    }

    /**
     * Routes the contained message based on the current setup, and returns the leaf send contexts.
     *
     * @param numExpected The expected number of leaf nodes.
     * @return The list of selected send contexts.
     */
    public List<RoutingNode> select(int numExpected) {
        msg.setRoute(Route.parse(hop.getName()));
        new RoutingNode(mbus, net, null, handler, msg).send();
        List<RoutingNode> ret = net.removeNodes();
        assertEquals(numExpected, ret.size());
        return ret;
    }

    /**
     * Ensures that the current setup selects a given set of routes.
     *
     * @param expected A list of expected route leaf nodes.
     */
    public void assertSelect(List<String> expected) {
        if (expected == null) {
            assertEquals(0, select(0).size());
        } else {
            List<RoutingNode> selected = select(expected.size());
            for (RoutingNode node : selected) {
                assertTrue("Route '" + node.getRoute() + "' not selected.",
                           expected.contains(node.getRoute().toString()));
                node.handleReply(new EmptyReply());
            }
        }
        assertNotNull(handler.getReply(60));
    }

    /**
     * This is a convenience method for invoking {@link #assertMerge(Map,List,List)} with no expected value.
     *
     * @param replies        The errors to set in the leaf node replies.
     * @param expectedErrors The list of expected errors in the merged reply.
     */
    public void assertMergeError(Map<String, Integer> replies, List<Integer> expectedErrors) {
        assertMerge(replies, expectedErrors, null);
    }

    /**
     * This is a convenience method for invoking {@link this#assertMerge(Map,List,List)} with no expected errors.
     *
     * @param replies       The errors to set in the leaf node replies.
     * @param allowedValues The list of allowed values in the final reply.
     */
    public void assertMergeOk(Map<String, Integer> replies, List<String> allowedValues) {
        assertMerge(replies, null, allowedValues);
    }

    /**
     * Ensures that the current setup generates as many leaf nodes as there are members of the errors argument. Each
     * error is then given one of these errors, and the method then ensures that the single returned reply contains the
     * given list of expected errors. Finally, if the expected value argument is non-null, this method ensures that the
     * reply is a SimpleReply whose string value exists in the allowed list.
     *
     * @param replies        The errors to set in the leaf node replies.
     * @param expectedErrors The list of expected errors in the merged reply.
     * @param allowedValues  The list of allowed values in the final reply.
     */
    public void assertMerge(Map<String, Integer> replies, List<Integer> expectedErrors, List<String> allowedValues) {
        List<RoutingNode> selected = select(replies.size());

        for (RoutingNode node : selected) {
            String route = node.getRoute().toString();
            assertTrue(replies.containsKey(route));
            Reply ret = new SimpleReply(route);
            if (replies.get(route) != ErrorCode.NONE) {
                ret.addError(new com.yahoo.messagebus.Error(replies.get(route), route));
            }
            node.handleReply(ret);
        }

        Reply reply = handler.getReply(60);
        assertNotNull(reply);
        if (expectedErrors != null) {
            assertEquals(expectedErrors.size(), reply.getNumErrors());
            for (int i = 0; i < expectedErrors.size(); ++i) {
                assertTrue(expectedErrors.contains(reply.getError(i).getCode()));
            }
        } else if (reply.hasErrors()) {
            StringBuilder err = new StringBuilder("Got unexpected error(s):\n");
            for (int i = 0; i < reply.getNumErrors(); ++i) {
                err.append("\t").append(reply.getError(i).toString());
                if (i < reply.getNumErrors() - 1) {
                    err.append("\n");
                }
            }
            fail(err.toString());
        }
        if (allowedValues != null) {
            assertEquals(SimpleProtocol.REPLY, reply.getType());
            assertTrue(allowedValues.contains(((SimpleReply)reply).getValue()));
        } else {
            assertEquals(0, reply.getType());
        }
    }

    /**
     * Ensures that the current setup chooses a single recipient, and that it merges similarly to how the
     * {@link DocumentProtocol} would merge these.
     *
     * @param recipient The expected recipient.
     */
    public void assertMergeOneReply(String recipient) {
        assertSelect(Arrays.asList(recipient));

        Map<String, Integer> replies = new HashMap<>();
        replies.put(recipient, ErrorCode.NONE);
        assertMergeOk(replies, Arrays.asList(recipient));

        replies.put(recipient, ErrorCode.TRANSIENT_ERROR);
        assertMergeError(replies, Arrays.asList(ErrorCode.TRANSIENT_ERROR));
    }

    /**
     * Ensures that the current setup will choose the two given recipients, and that it merges similarly to how the
     * {@link DocumentProtocol} would merge these.
     *
     * @param recipientOne The first expected recipient.
     * @param recipientTwo The second expected recipient.
     */
    public void assertMergeTwoReplies(String recipientOne, String recipientTwo) {
        assertSelect(Arrays.asList(recipientOne, recipientTwo));

        Map<String, Integer> replies = new HashMap<>();
        replies.put(recipientOne, ErrorCode.NONE);
        replies.put(recipientTwo, ErrorCode.NONE);
        assertMergeOk(replies, Arrays.asList(recipientOne, recipientTwo));

        replies.put(recipientOne, ErrorCode.TRANSIENT_ERROR);
        replies.put(recipientTwo, ErrorCode.NONE);
        assertMergeError(replies, Arrays.asList(ErrorCode.TRANSIENT_ERROR));

        replies.put(recipientOne, ErrorCode.TRANSIENT_ERROR);
        replies.put(recipientTwo, ErrorCode.TRANSIENT_ERROR);
        assertMergeError(replies, Arrays.asList(ErrorCode.TRANSIENT_ERROR, ErrorCode.TRANSIENT_ERROR));

        replies.put(recipientOne, ErrorCode.NONE);
        replies.put(recipientTwo, DocumentProtocol.ERROR_MESSAGE_IGNORED);
        assertMergeOk(replies, Arrays.asList(recipientOne));

        replies.put(recipientOne, DocumentProtocol.ERROR_MESSAGE_IGNORED);
        replies.put(recipientTwo, ErrorCode.NONE);
        assertMergeOk(replies, Arrays.asList(recipientTwo));

        replies.put(recipientOne, DocumentProtocol.ERROR_MESSAGE_IGNORED);
        replies.put(recipientTwo, DocumentProtocol.ERROR_MESSAGE_IGNORED);
        assertMergeError(replies, Arrays.asList(DocumentProtocol.ERROR_MESSAGE_IGNORED,
                                                DocumentProtocol.ERROR_MESSAGE_IGNORED));
    }

    /**
     * Waits for a given service pattern to resolve to the given number of hits in the local slobrok.
     *
     * @param pattern The pattern to lookup.
     * @param cnt     The number of entries to wait for.
     * @return True if the expected number of entries was found.
     */
    public boolean waitSlobrok(String pattern, int cnt) {
        for (int i = 0; i < 1000 && !Thread.currentThread().isInterrupted(); ++i) {
            Mirror.Entry[] res = net.getMirror().lookup(pattern);
            if (res.length == cnt) {
                return true;
            }
            try { Thread.sleep(10); } catch (InterruptedException e) { /* ignore */ }
        }
        return false;
    }

    /**
     * Returns the identity of this frame.
     *
     * @return The ident string.
     */
    public String getIdentity() {
        return identity;
    }

    /**
     * Returns the private slobrok server.
     *
     * @return The slobrok.
     */
    public Slobrok getSlobrok() {
        return slobrok;
    }

    /**
     * Returns the private message bus.
     *
     * @return The bus.
     */
    public MessageBus getMessageBus() {
        return mbus;
    }

    /**
     * Returns the private network layer.
     *
     * @return The network.
     */
    public Network getNetwork() {
        return net;
    }

    /**
     * Returns the message being tested.
     *
     * @return The message.
     */
    public Message getMessage() {
        return msg;
    }

    /**
     * Sets the message being tested.
     *
     * @param msg The message to set.
     */
    public void setMessage(Message msg) {
        this.msg = msg;
    }

    /**
     * Sets the spec of the hop to test with.
     *
     * @param hop The spec to set.
     */
    public void setHop(HopSpec hop) {
        this.hop = hop;
        mbus.setupRouting(new RoutingSpec().addTable(new RoutingTableSpec(DocumentProtocol.NAME).addHop(hop)));
    }

    /**
     * Returns the reply receptor used by this frame. All messages tested are tagged with this receptor, so after a
     * successful select, the receptor should contain a non-null reply.
     *
     * @return The reply receptor.
     */
    public Receptor getReceptor() {
        return handler;
    }

    /**
     * Implements a dummy network.
     */
    private class MyNetwork extends RPCNetwork {

        private List<RoutingNode> nodes = new ArrayList<>();

        public MyNetwork(RPCNetworkParams params) {
            super(params);
        }

        @Override
        public boolean allocServiceAddress(RoutingNode recipient) {
            recipient.setServiceAddress(new ServiceAddress() { });
            return true;
        }

        @Override
        public void freeServiceAddress(RoutingNode recipient) {
            recipient.setServiceAddress(null);
        }

        @Override
        public void send(Message msg, List<RoutingNode> recipients) {
            this.nodes.addAll(recipients);
        }

        public List<RoutingNode> removeNodes() {
            List<RoutingNode> ret = nodes;
            nodes = new ArrayList<>();
            return ret;
        }
    }

}
