// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol.test;

import com.yahoo.document.*;
import com.yahoo.documentapi.messagebus.protocol.*;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.api.IMirror;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.network.rpc.test.TestServer;
import com.yahoo.messagebus.routing.*;
import com.yahoo.messagebus.test.Receptor;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
@SuppressWarnings("deprecation")
public class PolicyTestCase {

    private static final int TIMEOUT = 300;
    private static final TimeUnit TIMEOUT_UNIT = TimeUnit.SECONDS;
    private static final long TIMEOUT_MILLIS = TIMEOUT_UNIT.toMillis(TIMEOUT);
    private final DocumentTypeManager manager = new DocumentTypeManager();

    @Before
    public void setUp() {
        DocumentTypeManagerConfigurer.configure(manager, "file:./test/cfg/testdoc.cfg");
    }

    @Test
    public void testProtocol() {
        DocumentProtocol protocol = new DocumentProtocol(manager);

        RoutingPolicy policy = protocol.createPolicy("AND", null);
        assertTrue(policy instanceof ANDPolicy);

        policy = new DocumentProtocol(manager).createPolicy("DocumentRouteSelector", "raw:route[0]\n");
        assertTrue(policy instanceof DocumentRouteSelectorPolicy);

        policy = new DocumentProtocol(manager).createPolicy("Extern", "foo;bar/baz");
        assertTrue(policy instanceof ExternPolicy);

        policy = new DocumentProtocol(manager).createPolicy("LocalService", null);
        assertTrue(policy instanceof LocalServicePolicy);

        policy = new DocumentProtocol(manager).createPolicy("RoundRobin", null);
        assertTrue(policy instanceof RoundRobinPolicy);

        policy = new DocumentProtocol(manager).createPolicy("SubsetService", null);
        assertTrue(policy instanceof SubsetServicePolicy);

        policy = new DocumentProtocol(manager).createPolicy("LoadBalancer", null);
        assertTrue(policy instanceof LoadBalancerPolicy);
    }

    @Test
    public void testAND() {
        PolicyTestFrame frame = new PolicyTestFrame(manager);
        frame.setMessage(new PutDocumentMessage(new DocumentPut(new Document(manager.getDocumentType("testdoc"),
                                                             new DocumentId("doc:scheme:")))));
        frame.setHop(new HopSpec("test", "[AND]")
                     .addRecipient("foo")
                     .addRecipient("bar"));
        frame.assertSelect(Arrays.asList("foo", "bar"));

        frame.setHop(new HopSpec("test", "[AND:baz]")
                     .addRecipient("foo")
                     .addRecipient("bar"));
        frame.assertSelect(Arrays.asList("baz")); // param precedes recipients

        frame.setHop(new HopSpec("test", "[AND:foo]"));
        frame.assertMergeOneReply("foo");

        frame.setHop(new HopSpec("test", "[AND:foo bar]"));
        frame.assertMergeTwoReplies("foo", "bar");
        frame.destroy();
    }

    @Test
    public void requireThatExternPolicyWithIllegalParamIsAnErrorPolicy() throws ListenFailedException {
        Slobrok slobrok = new Slobrok();
        String spec = "tcp/localhost:" + slobrok.port();
        assertTrue(new DocumentProtocol(manager).createPolicy("Extern", null) instanceof ErrorPolicy);
        assertTrue(new DocumentProtocol(manager).createPolicy("Extern", "") instanceof ErrorPolicy);
        assertTrue(new DocumentProtocol(manager).createPolicy("Extern", spec) instanceof ErrorPolicy);
        assertTrue(new DocumentProtocol(manager).createPolicy("Extern", spec + ";") instanceof ErrorPolicy);
        assertTrue(new DocumentProtocol(manager).createPolicy("Extern", spec + ";bar") instanceof ErrorPolicy);
    }

    @Test
    public void requireThatExternPolicyWithUnknownPatternSelectsNone() throws Exception {
        PolicyTestFrame frame = newPutDocumentFrame("doc:scheme:");
        setupExternPolicy(frame, new Slobrok(), "foo/bar");
        frame.assertSelect(null);
    }

    @Test
    public void requireThatExternPolicySelectsFromExternSlobrok() throws Exception {
        PolicyTestFrame frame = newPutDocumentFrame("doc:scheme:");
        Slobrok slobrok = new Slobrok();
        List<TestServer> servers = new ArrayList<>();
        for (int i = 0; i < 10; ++i) {
            TestServer server = new TestServer("docproc/cluster.default/" + i, null, slobrok,
                                               new DocumentProtocol(manager));
            server.net.registerSession("chain.default");
            servers.add(server);
        }
        setupExternPolicy(frame, slobrok, "docproc/cluster.default/*/chain.default", 10);
        Set<String> lst = new HashSet<>();
        for (int i = 0; i < 10; ++i) {
            RoutingNode leaf = frame.select(1).get(0);
            String recipient = leaf.getRoute().toString();
            lst.add(recipient);

            leaf.handleReply(new EmptyReply());
            assertNotNull(frame.getReceptor().getReply(TIMEOUT));
        }
        assertEquals(10, lst.size());
        for (TestServer server : servers) {
            server.destroy();
        }
        frame.destroy();
    }

    @Test
    public void requireThatExternPolicyMergesOneReplyAsProtocol() throws Exception {
        PolicyTestFrame frame = newPutDocumentFrame("doc:scheme:");
        Slobrok slobrok = new Slobrok();
        TestServer server = new TestServer("docproc/cluster.default/0", null, slobrok,
                                           new DocumentProtocol(manager));
        server.net.registerSession("chain.default");
        setupExternPolicy(frame, slobrok, "docproc/cluster.default/*/chain.default", 1);
        frame.assertMergeOneReply(server.net.getConnectionSpec() + "/chain.default");
        server.destroy();
        frame.destroy();
    }

    @Test
    public void testExternSend() throws Exception {
        // Setup local source node.
        Slobrok local = new Slobrok();
        TestServer src = new TestServer("src", null, local, new DocumentProtocol(manager));
        SourceSession ss = src.mb.createSourceSession(new Receptor(), new SourceSessionParams().setTimeout(TIMEOUT));

        // Setup remote cluster with routing config.
        Slobrok slobrok = new Slobrok();
        TestServer itr = new TestServer("itr",
                                        new RoutingTableSpec(DocumentProtocol.NAME)
                                                .addRoute(new RouteSpec("default").addHop("dst"))
                                                .addHop(new HopSpec("dst", "dst/session")),
                                        slobrok, new DocumentProtocol(manager));
        IntermediateSession is = itr.mb.createIntermediateSession("session", true, new Receptor(), new Receptor());
        TestServer dst = new TestServer("dst", null, slobrok, new DocumentProtocol(manager));
        DestinationSession ds = dst.mb.createDestinationSession("session", true, new Receptor());

        // Send message from local node to remote cluster and resolve route there.
        Message msg = new RemoveDocumentMessage(new DocumentId("doc:scheme:"));
        msg.getTrace().setLevel(9);
        msg.setRoute(Route.parse("[Extern:tcp/localhost:" + slobrok.port() + ";itr/session] default"));

        assertTrue(ss.send(msg).isAccepted());
        assertNotNull(msg = ((Receptor)is.getMessageHandler()).getMessage(TIMEOUT));
        is.forward(msg);
        assertNotNull(msg = ((Receptor)ds.getMessageHandler()).getMessage(TIMEOUT));
        ds.acknowledge(msg);
        Reply reply = ((Receptor)is.getReplyHandler()).getReply(TIMEOUT);
        assertNotNull(reply);
        is.forward(reply);
        assertNotNull(reply = ((Receptor)ss.getReplyHandler()).getReply(TIMEOUT));

        System.out.println(reply.getTrace().toString());

        // Perform necessary cleanup.
        src.destroy();
        itr.destroy();
        dst.destroy();
        slobrok.stop();
        local.stop();
    }

    @Test
    public void testExternMultipleSlobroks() throws ListenFailedException {
        Slobrok local = new Slobrok();
        TestServer srcServer = new TestServer("src", null, local, new DocumentProtocol(manager));
        SourceSession srcSession =
                srcServer.mb.createSourceSession(new Receptor(), new SourceSessionParams().setTimeout(TIMEOUT));

        Slobrok extern = new Slobrok();
        String spec = "tcp/localhost:" + extern.port();

        TestServer dstServer = new TestServer("dst", null, extern, new DocumentProtocol(manager));
        Receptor dstHandler = new Receptor();
        DestinationSession dstSession = dstServer.mb.createDestinationSession("session", true, dstHandler);

        Message msg = new RemoveDocumentMessage(new DocumentId("doc:scheme:"));
        msg.setRoute(Route.parse("[Extern:" + spec + ";dst/session]"));
        assertTrue(srcSession.send(msg).isAccepted());
        assertNotNull(msg = dstHandler.getMessage(TIMEOUT));
        dstSession.acknowledge(msg);
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(TIMEOUT);
        assertNotNull(reply);

        extern.stop();
        dstSession.destroy();
        dstServer.destroy();
        dstHandler.reset();
        assertNull(dstHandler.getMessage(0));

        extern = new Slobrok();
        spec += ",tcp/localhost:" + extern.port();

        dstServer = new TestServer("dst", null, extern, new DocumentProtocol(manager));
        dstHandler = new Receptor();
        dstSession = dstServer.mb.createDestinationSession("session", true, dstHandler);

        msg = new RemoveDocumentMessage(new DocumentId("doc:scheme:"));
        msg.setRoute(Route.parse("[Extern:" + spec + ";dst/session]"));
        assertTrue(srcSession.send(msg).isAccepted());
        assertNotNull(msg = dstHandler.getMessage(TIMEOUT));
        dstSession.acknowledge(msg);
        reply = ((Receptor)srcSession.getReplyHandler()).getReply(TIMEOUT);
        assertNotNull(reply);

        extern.stop();
        dstSession.destroy();
        dstServer.destroy();

        local.stop();
        srcSession.destroy();
        srcServer.destroy();
    }

    @Test
    public void testLocalService() {
        // Test select with proper address.
        PolicyTestFrame frame = new PolicyTestFrame("docproc/cluster.default", manager);
        frame.setMessage(new PutDocumentMessage(new DocumentPut(new Document(manager.getDocumentType("testdoc"),
                                                             new DocumentId("doc:scheme:0")))));
        for (int i = 0; i < 10; ++i) {
            frame.getNetwork().registerSession(i + "/chain.default");
        }
        assertTrue(frame.waitSlobrok("docproc/cluster.default/*/chain.default", 10));
        frame.setHop(new HopSpec("test", "docproc/cluster.default/[LocalService]/chain.default"));

        Set<String> lst = new HashSet<>();
        for (int i = 0; i < 10; ++i) {
            RoutingNode leaf = frame.select(1).get(0);
            String recipient = leaf.getRoute().toString();
            lst.add(recipient);

            leaf.handleReply(new EmptyReply());
            assertNotNull(frame.getReceptor().getReply(TIMEOUT));
        }
        assertEquals(10, lst.size());

        // Test select with broken address.
        lst.clear();
        frame.setHop(new HopSpec("test", "docproc/cluster.default/[LocalService:broken]/chain.default"));
        for (int i = 0; i < 10; ++i) {
            RoutingNode leaf = frame.select(1).get(0);
            String recipient = leaf.getRoute().toString();
            assertTrue(recipient.equals("docproc/cluster.default/*/chain.default"));
            lst.add(recipient);

            leaf.handleReply(new EmptyReply());
            assertNotNull(frame.getReceptor().getReply(TIMEOUT));
        }
        assertEquals(1, lst.size());

        // Test merge behavior.
        frame.setMessage(new PutDocumentMessage(new DocumentPut(new Document(manager.getDocumentType("testdoc"),
                                                             new DocumentId("doc:scheme:")))));
        frame.setHop(new HopSpec("test", "[LocalService]"));
        frame.assertMergeOneReply("*");

        frame.destroy();
    }

    @Test
    public void testLocalServiceCache() {
        PolicyTestFrame fooFrame = new PolicyTestFrame("docproc/cluster.default", manager);
        HopSpec fooHop = new HopSpec("foo", "docproc/cluster.default/[LocalService]/chain.foo");
        fooFrame.setMessage(new RemoveDocumentMessage(new DocumentId("doc:scheme:foo")));
        fooFrame.setHop(fooHop);

        PolicyTestFrame barFrame = new PolicyTestFrame(fooFrame);
        HopSpec barHop = new HopSpec("bar", "docproc/cluster.default/[LocalService]/chain.bar");
        barFrame.setMessage(new RemoveDocumentMessage(new DocumentId("doc:scheme:bar")));
        barFrame.setHop(barHop);

        fooFrame.getMessageBus().setupRouting(
                new RoutingSpec().addTable(new RoutingTableSpec(DocumentProtocol.NAME)
                                                   .addHop(fooHop)
                                                   .addHop(barHop)));

        fooFrame.getNetwork().registerSession("0/chain.foo");
        fooFrame.getNetwork().registerSession("0/chain.bar");
        assertTrue(fooFrame.waitSlobrok("docproc/cluster.default/0/*", 2));

        RoutingNode fooChild = fooFrame.select(1).get(0);
        assertEquals("docproc/cluster.default/0/chain.foo", fooChild.getRoute().getHop(0).toString());
        RoutingNode barChild = barFrame.select(1).get(0);
        assertEquals("docproc/cluster.default/0/chain.bar", barChild.getRoute().getHop(0).toString());

        barChild.handleReply(new EmptyReply());
        fooChild.handleReply(new EmptyReply());

        assertNotNull(barFrame.getReceptor().getReply(TIMEOUT));
        assertNotNull(fooFrame.getReceptor().getReply(TIMEOUT));
    }

    @Test
    public void multipleGetRepliesAreMergedToFoundDocument() {
        PolicyTestFrame frame = new PolicyTestFrame(manager);
        frame.setHop(new HopSpec("test", getDocumentRouteSelectorRawConfig())
                .addRecipient("foo").addRecipient("bar"));
        frame.setMessage(new GetDocumentMessage(new DocumentId("doc:scheme:yarn"), "[all]"));
        List<RoutingNode> selected = frame.select(2);
        for (int i = 0, len = selected.size(); i < len; ++i) {
            Document doc = null;
            if (i == 0) {
                doc = new Document(manager.getDocumentType("testdoc"),
                                   new DocumentId("doc:scheme:yarn"));
                doc.setLastModified(123456L);
            }
            GetDocumentReply reply = new GetDocumentReply(null);
            reply.setDocument(doc);
            selected.get(i).handleReply(reply);
        }
        Reply reply = frame.getReceptor().getReply(TIMEOUT);
        assertNotNull(reply);
        assertEquals(DocumentProtocol.REPLY_GETDOCUMENT, reply.getType());
        assertEquals(123456L, ((GetDocumentReply)reply).getLastModified());
    }

    private String getDocumentRouteSelectorRawConfig() {
        return "[DocumentRouteSelector:raw:" +
                "route[2]\n" +
                "route[0].name \"foo\"\n" +
                "route[0].selector \"testdoc\"\n" +
                "route[0].feed \"myfeed\"\n" +
                "route[1].name \"bar\"\n" +
                "route[1].selector \"other\"\n" +
                "route[1].feed \"myfeed\"\n]";
    }

    @Test
    public void testSubsetService() {
        PolicyTestFrame frame = new PolicyTestFrame("docproc/cluster.default", manager);
        frame.setMessage(new PutDocumentMessage(new DocumentPut(new DocumentPut(new Document(manager.getDocumentType("testdoc"),
                                                             new DocumentId("doc:scheme:"))))));

        // Test requerying for adding nodes.
        frame.setHop(new HopSpec("test", "docproc/cluster.default/[SubsetService:2]/chain.default"));
        Set<String> lst = new HashSet<>();
        for (int i = 1; i <= 10; ++i) {
            frame.getNetwork().registerSession(i + "/chain.default");
            assertTrue(frame.waitSlobrok("docproc/cluster.default/*/chain.default", i));

            RoutingNode leaf = frame.select(1).get(0);
            lst.add(leaf.getRoute().toString());
            leaf.handleReply(new EmptyReply());
            assertNotNull(frame.getReceptor().getReply(TIMEOUT));
        }
        assertTrue(lst.size() > 1); // must have requeried

        // Test load balancing.
        String prev = null;
        for (int i = 1; i <= 10; ++i) {
            RoutingNode leaf = frame.select(1).get(0);
            String next = leaf.getRoute().toString();
            if (prev == null) {
                assertNotNull(next);
            } else {
                assertFalse(prev.equals(next));
            }
            prev = next;
            leaf.handleReply(new EmptyReply());
            assertNotNull(frame.getReceptor().getReply(TIMEOUT));
        }

        // Test requerying for dropping nodes.
        lst.clear();
        for (int i = 1; i <= 10; ++i) {
            RoutingNode leaf = frame.select(1).get(0);
            String route = leaf.getRoute().toString();
            lst.add(route);

            frame.getNetwork().unregisterSession(route.substring(frame.getIdentity().length() + 1));
            assertTrue(frame.waitSlobrok("docproc/cluster.default/*/chain.default", 10 - i));

            Reply reply = new EmptyReply();
            reply.addError(new Error(ErrorCode.NO_ADDRESS_FOR_SERVICE, route));
            leaf.handleReply(reply);
            assertNotNull(frame.getReceptor().getReply(TIMEOUT));
        }
        assertEquals(10, lst.size());

        // Test merge behavior.
        frame.setHop(new HopSpec("test", "[SubsetService]"));
        frame.assertMergeOneReply("*");

        frame.destroy();
    }

    @Test
    public void testSubsetServiceCache() {
        PolicyTestFrame fooFrame = new PolicyTestFrame("docproc/cluster.default", manager);
        HopSpec fooHop = new HopSpec("foo", "docproc/cluster.default/[SubsetService:2]/chain.foo");
        fooFrame.setMessage(new RemoveDocumentMessage(new DocumentId("doc:scheme:foo")));
        fooFrame.setHop(fooHop);

        PolicyTestFrame barFrame = new PolicyTestFrame(fooFrame);
        HopSpec barHop = new HopSpec("bar", "docproc/cluster.default/[SubsetService:2]/chain.bar");
        barFrame.setMessage(new RemoveDocumentMessage(new DocumentId("doc:scheme:bar")));
        barFrame.setHop(barHop);

        fooFrame.getMessageBus().setupRouting(
                new RoutingSpec().addTable(new RoutingTableSpec(DocumentProtocol.NAME)
                                                   .addHop(fooHop)
                                                   .addHop(barHop)));

        fooFrame.getNetwork().registerSession("0/chain.foo");
        fooFrame.getNetwork().registerSession("0/chain.bar");
        assertTrue(fooFrame.waitSlobrok("docproc/cluster.default/0/*", 2));

        RoutingNode fooChild = fooFrame.select(1).get(0);
        assertEquals("docproc/cluster.default/0/chain.foo", fooChild.getRoute().getHop(0).toString());
        RoutingNode barChild = barFrame.select(1).get(0);
        assertEquals("docproc/cluster.default/0/chain.bar", barChild.getRoute().getHop(0).toString());

        barChild.handleReply(new EmptyReply());
        fooChild.handleReply(new EmptyReply());

        assertNotNull(barFrame.getReceptor().getReply(TIMEOUT));
        assertNotNull(fooFrame.getReceptor().getReply(TIMEOUT));
    }

    @Test
    public void testDocumentRouteSelector() {
        // Test policy usage safeguard.
        String okConfig = "raw:route[0]\n";
        String errConfig = "raw:" +
                           "route[1]\n" +
                           "route[0].name \"foo\"\n" +
                           "route[0].selector \"foo bar\"\n" +
                           "route[0].feed \"baz\"\n";

        DocumentProtocol protocol = new DocumentProtocol(manager, okConfig);
        assertTrue(protocol.createPolicy("DocumentRouteSelector", null) instanceof DocumentRouteSelectorPolicy);
        assertTrue(protocol.createPolicy("DocumentRouteSelector", "") instanceof DocumentRouteSelectorPolicy);
        assertTrue(protocol.createPolicy("DocumentRouteSelector", errConfig) instanceof ErrorPolicy);

        protocol = new DocumentProtocol(manager, errConfig);
        assertTrue(protocol.createPolicy("DocumentRouteSelector", null) instanceof ErrorPolicy);
        assertTrue(protocol.createPolicy("DocumentRouteSelector", "") instanceof ErrorPolicy);
        assertTrue(protocol.createPolicy("DocumentRouteSelector", okConfig) instanceof DocumentRouteSelectorPolicy);

        // Test policy with proper config.
        PolicyTestFrame frame = new PolicyTestFrame(manager);
        frame.setHop(new HopSpec("test", "[DocumentRouteSelector:raw:" +
                                         "route[2]\n" +
                                         "route[0].name \"foo\"\n" +
                                         "route[0].selector \"testdoc\"\n" +
                                         "route[0].feed \"myfeed\"\n" +
                                         "route[1].name \"bar\"\n" +
                                         "route[1].selector \"other\"\n" +
                                         "route[1].feed \"myfeed\"\n]").addRecipient("foo").addRecipient("bar"));

        frame.setMessage(new GetDocumentMessage(new DocumentId("doc:scheme:"), "fieldSet"));
        frame.assertSelect(Arrays.asList("bar", "foo"));

        Message put = new PutDocumentMessage(new DocumentPut(new Document(manager.getDocumentType("testdoc"),
                                                          new DocumentId("doc:scheme:"))));
        frame.setMessage(put);
        frame.assertSelect(Arrays.asList("foo"));

        frame.setMessage(new RemoveDocumentMessage(new DocumentId("doc:scheme:")));
        frame.assertSelect(Arrays.asList("bar", "foo"));

        frame.setMessage(new UpdateDocumentMessage(new DocumentUpdate(manager.getDocumentType("testdoc"),
                                                                      new DocumentId("doc:scheme:"))));
        frame.assertSelect(Arrays.asList("foo"));

        frame.setMessage(put);
        frame.assertMergeOneReply("foo");

        frame.destroy();
    }


    @Test
    public void testDocumentRouteSelectorIgnore() {
        PolicyTestFrame frame = new PolicyTestFrame(manager);
        frame.setHop(new HopSpec("test", "[DocumentRouteSelector:raw:" +
                                         "route[1]\n" +
                                         "route[0].name \"docproc/cluster.foo\"\n" +
                                         "route[0].selector \"testdoc and testdoc.stringfield == 'foo'\"\n" +
                                         "route[0].feed \"myfeed\"\n]").addRecipient("docproc/cluster.foo"));

        frame.setMessage(new PutDocumentMessage(
                new DocumentPut(new Document(manager.getDocumentType("testdoc"),
                             new DocumentId("id:yarn:testdoc:n=1234:fluff")))));
        frame.select(0);
        Reply reply = frame.getReceptor().getReply(TIMEOUT);
        assertNotNull(reply);
        assertEquals(DocumentProtocol.REPLY_DOCUMENTIGNORED, reply.getType());
        assertEquals(0, reply.getNumErrors());

        frame.setMessage(new UpdateDocumentMessage(new DocumentUpdate(manager.getDocumentType("testdoc"),
                                                                      new DocumentId("doc:scheme:"))));
        frame.assertSelect(Arrays.asList("docproc/cluster.foo"));

        frame.destroy();
    }

    @Test
    public void testLoadBalancer() {
        PolicyTestFrame frame = new PolicyTestFrame("docproc/cluster.default", manager);
        frame.setMessage(new PutDocumentMessage(new DocumentPut(new Document(manager.getDocumentType("testdoc"),
                                                             new DocumentId("doc:scheme:")))));
        frame.getNetwork().registerSession("0/chain.default");
        assertTrue(frame.waitSlobrok("docproc/cluster.default/*/chain.default", 1));
        frame.setHop(new HopSpec("test", "[LoadBalancer:cluster=docproc/cluster.default;session=chain.default]"));

        assertSelect(frame, 1, Arrays.asList(frame.getNetwork().getConnectionSpec() + "/chain.default"));
    }

    @Test
    public void testRoundRobin() {
        // Test select with proper address.
        PolicyTestFrame frame = new PolicyTestFrame("docproc/cluster.default", manager);
        frame.setMessage(new PutDocumentMessage(new DocumentPut(new Document(manager.getDocumentType("testdoc"),
                                                             new DocumentId("doc:scheme:")))));
        for (int i = 0; i < 10; ++i) {
            frame.getNetwork().registerSession(i + "/chain.default");
        }
        assertTrue(frame.waitSlobrok("docproc/cluster.default/*/chain.default", 10));
        frame.setHop(new HopSpec("test", "[RoundRobin]")
                .addRecipient("docproc/cluster.default/3/chain.default")
                .addRecipient("docproc/cluster.default/6/chain.default")
                .addRecipient("docproc/cluster.default/9/chain.default"));
        assertSelect(frame, 32, Arrays.asList("docproc/cluster.default/3/chain.default",
                                              "docproc/cluster.default/6/chain.default",
                                              "docproc/cluster.default/9/chain.default"));
        frame.getNetwork().unregisterSession("6/chain.default");
        assertTrue(frame.waitSlobrok("docproc/cluster.default/*/chain.default", 9));
        assertSelect(frame, 32, Arrays.asList("docproc/cluster.default/3/chain.default",
                                              "docproc/cluster.default/9/chain.default"));
        frame.getNetwork().unregisterSession("3/chain.default");
        assertTrue(frame.waitSlobrok("docproc/cluster.default/*/chain.default", 8));
        assertSelect(frame, 32, Arrays.asList("docproc/cluster.default/9/chain.default"));
        frame.getNetwork().unregisterSession("9/chain.default");
        assertTrue(frame.waitSlobrok("docproc/cluster.default/*/chain.default", 7));
        assertSelect(frame, 32, new ArrayList<String>());

        // Test merge behavior.
        frame.setHop(new HopSpec("test", "[RoundRobin]").addRecipient("docproc/cluster.default/0/chain.default"));
        frame.assertMergeOneReply("docproc/cluster.default/0/chain.default");

        frame.destroy();
    }

    @Test
    public void testRoundRobinCache() {
        PolicyTestFrame fooFrame = new PolicyTestFrame("docproc/cluster.default", manager);
        HopSpec fooHop = new HopSpec("foo", "[RoundRobin]").addRecipient("docproc/cluster.default/0/chain.foo");
        fooFrame.setMessage(new RemoveDocumentMessage(new DocumentId("doc:scheme:foo")));
        fooFrame.setHop(fooHop);

        PolicyTestFrame barFrame = new PolicyTestFrame(fooFrame);
        HopSpec barHop = new HopSpec("bar", "[RoundRobin]").addRecipient("docproc/cluster.default/0/chain.bar");
        barFrame.setMessage(new RemoveDocumentMessage(new DocumentId("doc:scheme:bar")));
        barFrame.setHop(barHop);

        fooFrame.getMessageBus().setupRouting(
                new RoutingSpec().addTable(new RoutingTableSpec(DocumentProtocol.NAME)
                                                   .addHop(fooHop)
                                                   .addHop(barHop)));

        fooFrame.getNetwork().registerSession("0/chain.foo");
        fooFrame.getNetwork().registerSession("0/chain.bar");
        assertTrue(fooFrame.waitSlobrok("docproc/cluster.default/0/*", 2));

        RoutingNode fooChild = fooFrame.select(1).get(0);
        assertEquals("docproc/cluster.default/0/chain.foo", fooChild.getRoute().toString());
        RoutingNode barChild = barFrame.select(1).get(0);
        assertEquals("docproc/cluster.default/0/chain.bar", barChild.getRoute().toString());

        barChild.handleReply(new EmptyReply());
        fooChild.handleReply(new EmptyReply());

        assertNotNull(barFrame.getReceptor().getReply(TIMEOUT));
        assertNotNull(fooFrame.getReceptor().getReply(TIMEOUT));
    }

    /**
     * Ensures that the given number of select passes on the given frame produces an expected list of recipients.
     *
     * @param frame      The frame to select on.
     * @param numSelects The number of selects to perform.
     * @param expected   The list of expected recipients.
     */
    private static void assertSelect(PolicyTestFrame frame, int numSelects, List<String> expected) {
        Set<String> lst = new TreeSet<>();

        for (int i = 0; i < numSelects; ++i) {
            if (!expected.isEmpty()) {
                RoutingNode leaf = frame.select(1).get(0);
                String recipient = leaf.getRoute().toString();
                lst.add(recipient);
                leaf.handleReply(new EmptyReply());
            } else {
                frame.select(0);
            }
            assertNotNull(frame.getReceptor().getReply(TIMEOUT));
        }

        assertEquals(expected.size(), lst.size());
        Iterator<String> it = lst.iterator();
        for (String recipient : expected) {
            assertEquals(recipient, it.next());
        }
    }

    private static void assertMirrorReady(Mirror slobrok)
            throws InterruptedException, TimeoutException
    {
        for (int i = 0; i < TIMEOUT_MILLIS / 10; ++i) {
            if (slobrok.ready()) {
                return;
            }
            Thread.sleep(10);
        }
        throw new TimeoutException();
    }

    private static void assertMirrorContains(IMirror slobrok, String pattern, int numEntries)
            throws InterruptedException, TimeoutException
    {
        for (int i = 0; i < TIMEOUT_MILLIS / 10; ++i) {
            if (slobrok.lookup(pattern).length == numEntries) {
                return;
            }
            Thread.sleep(10);
        }
        throw new TimeoutException();
    }

    private void setupExternPolicy(PolicyTestFrame frame, Slobrok slobrok, String pattern)
            throws InterruptedException, TimeoutException
    {
        setupExternPolicy(frame, slobrok, pattern, -1);
    }

    private void setupExternPolicy(PolicyTestFrame frame, Slobrok slobrok, String pattern, int numEntries)
            throws InterruptedException, TimeoutException
    {
        String param = "tcp/localhost:" + slobrok.port() + ";" + pattern;
        frame.setHop(new HopSpec("test", "[Extern:" + param + "]"));
        MessageBus mbus = frame.getMessageBus();
        HopBlueprint hop = mbus.getRoutingTable(DocumentProtocol.NAME).getHop("test");
        PolicyDirective dir = (PolicyDirective)hop.getDirective(0);
        ExternPolicy policy = (ExternPolicy)mbus.getRoutingPolicy(DocumentProtocol.NAME, dir.getName(), dir.getParam());
        assertMirrorReady(policy.getMirror());
        if (numEntries >= 0) {
            assertMirrorContains(policy.getMirror(), pattern, numEntries);
        }
    }

    private PolicyTestFrame newFrame() {
        return new PolicyTestFrame(manager);
    }

    private PolicyTestFrame newFrame(Message msg) {
        PolicyTestFrame frame = newFrame();
        frame.setMessage(msg);
        return frame;
    }

    private PutDocumentMessage newPutDocument(String documentId) {
        return new PutDocumentMessage(new DocumentPut(new Document(manager.getDocumentType("testdoc"),
                                                                   new DocumentId(documentId))));
    }

    private PolicyTestFrame newPutDocumentFrame(String documentId) {
        return newFrame(newPutDocument(documentId));
    }
}
