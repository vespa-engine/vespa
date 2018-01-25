// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.storage.searcher;

import com.yahoo.component.chain.Chain;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.feedhandler.NullFeedMetric;
import com.yahoo.jdisc.HeaderFields;
import com.yahoo.vespa.config.content.LoadTypeConfig;
import com.yahoo.document.*;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.Raw;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.GetDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.GetDocumentReply;
import com.yahoo.feedapi.FeedContext;
import com.yahoo.feedapi.MessagePropertyProcessor;
import com.yahoo.java7compat.Util;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.prelude.templates.SearchRendererAdaptor;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.rendering.RendererRegistry;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.vespaclient.ClusterList;
import com.yahoo.vespaclient.config.FeederConfig;

import org.junit.Test;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.*;

@SuppressWarnings("deprecation")
public class GetSearcherTestCase {

    private DocumentTypeManager docMan = null;
    private DocumentType docType;
    private FeederConfig defFeedCfg = new FeederConfig(new FeederConfig.Builder());
    private LoadTypeConfig defLoadTypeCfg = new LoadTypeConfig(new LoadTypeConfig.Builder());

    @org.junit.Before
    public void setUp() {
        docMan = new DocumentTypeManager();
        docType = new DocumentType("kittens");
        docType.addHeaderField("name", DataType.STRING);
        docType.addField("description", DataType.STRING);
        docType.addField("image", DataType.STRING);
        docType.addField("fluffiness", DataType.INT);
        docType.addField("foo", DataType.RAW);
        docMan.registerDocumentType(docType);
    }

    @org.junit.After
    public void tearDown() {
        docMan = null;
        docType = null;
    }

    private void assertHits(HitGroup hits, String... wantedHits) {
        assertEquals(wantedHits.length, hits.size());
        for (int i = 0; i < wantedHits.length; ++i) {
            assertTrue(hits.get(i) instanceof DocumentHit);
            DocumentHit hit = (DocumentHit)hits.get(i);
            assertEquals(wantedHits[i], hit.getDocument().getId().toString());
        }
    }

    @Test
    public void testGetSingleDocumentQuery() throws Exception {
        DocumentSessionFactory factory = new DocumentSessionFactory(docType); // Needs auto-reply
        GetSearcher searcher = new GetSearcher(new FeedContext(
                new MessagePropertyProcessor(defFeedCfg, defLoadTypeCfg),
                factory, docMan, new ClusterList(), new NullFeedMetric()));
        Chain<Searcher> searchChain = new Chain<>(searcher);

        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(newQuery("?id=userdoc:kittens:1:2"));
        System.out.println("HTTP request is " + result.getQuery().getHttpRequest());

        assertEquals(1, factory.messages.size());
        {
            Message m = factory.messages.get(0);
            assertEquals(DocumentProtocol.MESSAGE_GETDOCUMENT, m.getType());
            GetDocumentMessage gdm = (GetDocumentMessage)m;
            DocumentId d = gdm.getDocumentId();
            assertEquals("userdoc:kittens:1:2", d.toString());
            assertEquals("[all]", gdm.getFieldSet());
        }
        assertEquals(1, result.hits().size());
        assertHits(result.hits(), "userdoc:kittens:1:2");
        // By default, document hit should not have its hit fields set
        DocumentHit hit = (DocumentHit)result.hits().get(0);
        assertEquals(0, hit.fieldKeys().size());
    }

    @Test
    public void testGetMultipleDocumentsQuery() throws Exception {
        DocumentSessionFactory factory = new DocumentSessionFactory(docType);
        GetSearcher searcher = new GetSearcher(new FeedContext(
                new MessagePropertyProcessor(defFeedCfg, defLoadTypeCfg),
                factory, docMan, new ClusterList(), new NullFeedMetric()));
        Chain<Searcher> searchChain = new Chain<>(searcher);

        Query query = newQuery("?id[0]=userdoc:kittens:1:2&id[1]=userdoc:kittens:3:4");
        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(query);

        assertEquals(2, factory.messages.size());
        {
            Message m = factory.messages.get(0);
            assertEquals(DocumentProtocol.MESSAGE_GETDOCUMENT, m.getType());
            GetDocumentMessage gdm = (GetDocumentMessage)m;
            DocumentId d = gdm.getDocumentId();
            assertEquals("userdoc:kittens:1:2", d.toString());
            assertEquals("[all]", gdm.getFieldSet());
        }

        {
            Message m = factory.messages.get(1);
            assertEquals(DocumentProtocol.MESSAGE_GETDOCUMENT, m.getType());
            GetDocumentMessage gdm = (GetDocumentMessage)m;
            DocumentId d = gdm.getDocumentId();
            assertEquals("userdoc:kittens:3:4", d.toString());
            assertEquals("[all]", gdm.getFieldSet());
        }
        assertEquals(2, result.hits().size());
        assertNull(result.hits().getErrorHit());
        assertHits(result.hits(), "userdoc:kittens:1:2", "userdoc:kittens:3:4");
        assertEquals(2, query.getHits());
    }

    // Test that you can use both query string and POSTed IDs
    @Test
    public void testGetMultipleDocumentsQueryAndPOST() throws Exception {
        DocumentSessionFactory factory = new DocumentSessionFactory(docType);
        GetSearcher searcher = new GetSearcher(new FeedContext(
                new MessagePropertyProcessor(defFeedCfg, defLoadTypeCfg),
                factory, docMan, new ClusterList(), new NullFeedMetric()));
        Chain<Searcher> searchChain = new Chain<>(searcher);

        String data = "userdoc:kittens:5:6\nuserdoc:kittens:7:8\nuserdoc:kittens:9:10";
        MockHttpRequest request = new MockHttpRequest(data.getBytes("utf-8"), "/get/?id[0]=userdoc:kittens:1:2&id[1]=userdoc:kittens:3:4");
        Query query = new Query(request.toRequest());
        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(query);

        assertEquals(5, factory.messages.size());
        assertEquals(5, result.hits().size());
        assertNull(result.hits().getErrorHit());
        assertHits(result.hits(), "userdoc:kittens:1:2", "userdoc:kittens:3:4",
                "userdoc:kittens:5:6", "userdoc:kittens:7:8", "userdoc:kittens:9:10");
    }

    @Test
    public void testGetMultipleDocumentsQueryAndGZippedPOST() throws Exception {
        DocumentSessionFactory factory = new DocumentSessionFactory(docType);
        GetSearcher searcher = new GetSearcher(new FeedContext(
                new MessagePropertyProcessor(defFeedCfg, defLoadTypeCfg),
                factory, docMan, new ClusterList(), new NullFeedMetric()));
        Chain<Searcher> searchChain = new Chain<>(searcher);

        String data = "userdoc:kittens:5:6\nuserdoc:kittens:7:8\nuserdoc:kittens:9:10";

        // Create with automatic GZIP encoding
        MockHttpRequest request = new MockHttpRequest(data.getBytes("utf-8"), "/get/?id[0]=userdoc:kittens:1:2&id[1]=userdoc:kittens:3:4", true);
        Query query = new Query(request.toRequest());
        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(query);

        assertEquals(5, factory.messages.size());
        assertEquals(5, result.hits().size());
        assertNull(result.hits().getErrorHit());
        assertHits(result.hits(), "userdoc:kittens:1:2", "userdoc:kittens:3:4",
                "userdoc:kittens:5:6", "userdoc:kittens:7:8", "userdoc:kittens:9:10");
    }

    /* Test that a query without any ids is passed through to the next chain */
    @Test
    public void testQueryPassThrough() throws Exception {
        DocumentSessionFactory factory = new DocumentSessionFactory(docType);
        GetSearcher searcher = new GetSearcher(new FeedContext(
                new MessagePropertyProcessor(defFeedCfg, defLoadTypeCfg),
                factory, docMan, new ClusterList(), new NullFeedMetric()));
        HitGroup hits = new HitGroup("mock");
        hits.add(new Hit("blernsball"));
        Chain<Searcher> searchChain = new Chain<>(searcher, new MockBackend(hits));

        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(newQuery("?flarn=blern"));

        assertEquals(0, factory.messages.size());
        assertEquals(1, result.hits().size());
        assertNotNull(result.hits().get("blernsball"));
    }

    /* Test that a query will contain both document hits and hits from a searcher
     * further down the chain, iff the searcher returns a DocumentHit.
     */
    @Test
    public void testQueryPassThroughAndGet() throws Exception {
        Document doc1 = new Document(docType, new DocumentId("userdoc:kittens:1234:foo"));
        doc1.setFieldValue("name", new StringFieldValue("megacat"));
        doc1.setFieldValue("description", new StringFieldValue("supercat"));
        doc1.setFieldValue("fluffiness", new IntegerFieldValue(10000));
        GetDocumentReply[] replies = new GetDocumentReply[] {
                new GetDocumentReply(doc1)
        };

        DocumentSessionFactory factory = new DocumentSessionFactory(docType, null, false, replies);
        GetSearcher searcher = new GetSearcher(new FeedContext(
                new MessagePropertyProcessor(defFeedCfg, defLoadTypeCfg),
                factory, docMan, new ClusterList(), new NullFeedMetric()));
        DocumentHit backendHit = new DocumentHit(new Document(docType, new DocumentId("userdoc:kittens:5678:bar")), 5);
        Chain<Searcher> searchChain = new Chain<>(searcher, new MockBackend(backendHit));

        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(newQuery("?query=flarn&id=userdoc:kittens:1234:foo"));

        assertEquals(1, factory.messages.size());
        assertEquals(2, result.hits().size());
        assertNotNull(result.hits().get("userdoc:kittens:5678:bar"));
        assertNotNull(result.hits().get("userdoc:kittens:1234:foo"));
    }

    @Test
    public void testQueryPassThroughAndGetUnknownBackendHit() throws Exception {
        DocumentSessionFactory factory = new DocumentSessionFactory(docType);
        GetSearcher searcher = new GetSearcher(new FeedContext(
                new MessagePropertyProcessor(defFeedCfg, defLoadTypeCfg),
                factory, docMan, new ClusterList(), new NullFeedMetric()));
        HitGroup hits = new HitGroup("mock");
        hits.add(new Hit("blernsball"));
        Chain<Searcher> searchChain = new Chain<>(searcher, new MockBackend(hits));

        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(newQuery("?flarn=blern&id=userdoc:kittens:9:aaa"));

        assertEquals(0, factory.messages.size());
        assertNotNull(result.hits().getErrorHit());

        assertRendered("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<result>\n" +
                "<errors>\n" +
                "<error type=\"searcher\" code=\"18\" message=\"Internal server error.: " +
                "A backend searcher to com.yahoo.storage.searcher.GetSearcher returned a " +
                "hit that was not an instance of com.yahoo.storage.searcher.DocumentHit. " +
                "Only DocumentHit instances are supported in the backend hit result set " +
                "when doing queries that contain document identifier sets recognised by the " +
                "Get Searcher.\"/>\n" +
                "</errors>\n" +
                "</result>\n", result);
    }

    @Test
    public void testConfig() throws Exception {
        DocumentSessionFactory factory = new DocumentSessionFactory(docType);
        GetSearcher searcher = new GetSearcher(new FeedContext(
                new MessagePropertyProcessor(new FeederConfig(new FeederConfig.Builder().timeout(458).route("route66").retryenabled(false)), defLoadTypeCfg),
                factory, docMan, new ClusterList(), new NullFeedMetric()));
        Chain<Searcher> searchChain = new Chain<>(searcher);

        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(newQuery("?id=doc:batman:dahnahnahnah"));

        assertEquals(1, factory.messages.size());
        {
            Message m = factory.messages.get(0);
            assertEquals(DocumentProtocol.MESSAGE_GETDOCUMENT, m.getType());
            GetDocumentMessage gdm = (GetDocumentMessage)m;
            DocumentId d = gdm.getDocumentId();
            assertEquals("doc:batman:dahnahnahnah", d.toString());
            assertEquals("[all]", gdm.getFieldSet());
            assertEquals(Route.parse("route66"), gdm.getRoute());
            assertFalse(gdm.getRetryEnabled());
            assertEquals(458000, gdm.getTimeRemaining());
        }
    }

    @Test
    public void testConfigChanges() throws Exception {
        String config = "raw:timeout 458\nroute \"riksveg18\"\nretryenabled true";
        DocumentSessionFactory factory = new DocumentSessionFactory(docType);
        GetSearcher searcher = new GetSearcher(new FeedContext(
                new MessagePropertyProcessor(new FeederConfig(new FeederConfig.Builder().timeout(58).route("riksveg18").retryenabled(true)),
                            defLoadTypeCfg),
                factory, docMan, new ClusterList(), new NullFeedMetric()));
        Chain<Searcher> searchChain = new Chain<>(searcher);

        new Execution(searchChain, Execution.Context.createContextStub()).search(newQuery("?id=doc:batman:dahnahnahnah"));

        assertEquals(1, factory.messages.size());
        assertEquals(1, factory.getSessionsCreated());
        {
            Message m = factory.messages.get(0);
            assertEquals(DocumentProtocol.MESSAGE_GETDOCUMENT, m.getType());
            GetDocumentMessage gdm = (GetDocumentMessage)m;
            DocumentId d = gdm.getDocumentId();
            assertEquals("doc:batman:dahnahnahnah", d.toString());
            assertEquals("[all]", gdm.getFieldSet());
            assertEquals(Route.parse("riksveg18"), gdm.getRoute());
            assertTrue(gdm.getRetryEnabled());
            assertTrue(58000 >= gdm.getTimeRemaining());
        }

        factory.messages.clear();

        FeederConfig newConfig = new FeederConfig(new FeederConfig.Builder()
                .timeout(123)
                .route("e6")
                .retryenabled(false)
        );
        searcher.getMessagePropertyProcessor().configure(newConfig, defLoadTypeCfg);

        new Execution(searchChain, Execution.Context.createContextStub()).search(
                newQuery("?id=doc:spiderman:does_whatever_a_spider_can"));

        // riksveg18 is created again, and e6 is created as well.
        assertEquals(3, factory.getSessionsCreated());

        assertEquals(1, factory.messages.size());
        {
            Message m = factory.messages.get(0);
            assertEquals(DocumentProtocol.MESSAGE_GETDOCUMENT, m.getType());
            GetDocumentMessage gdm = (GetDocumentMessage)m;
            DocumentId d = gdm.getDocumentId();
            assertEquals("doc:spiderman:does_whatever_a_spider_can", d.toString());
            assertEquals("[all]", gdm.getFieldSet());
            assertEquals(Route.parse("e6"), gdm.getRoute());
            assertFalse(gdm.getRetryEnabled());
            assertTrue(123000 >= gdm.getTimeRemaining());
        }
    }

    @Test
    public void testQueryOverridesDefaults() throws Exception {
        DocumentSessionFactory factory = new DocumentSessionFactory(docType);
        GetSearcher searcher = new GetSearcher(new FeedContext(
                new MessagePropertyProcessor(defFeedCfg, defLoadTypeCfg),
                factory, docMan, new ClusterList(), new NullFeedMetric()));
        Chain<Searcher> searchChain = new Chain<>(searcher);

        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(
                newQuery("?id[0]=userdoc:kittens:1:2&id[1]=userdoc:kittens:3:4&priority=LOW_2&route=highwaytohell&timeout=58"));

        assertEquals(2, factory.messages.size());
        long lastTimeout = 58000;
        {
            Message m = factory.messages.get(0);
            assertEquals(DocumentProtocol.MESSAGE_GETDOCUMENT, m.getType());
            GetDocumentMessage gdm = (GetDocumentMessage)m;
            DocumentId d = gdm.getDocumentId();
            assertEquals("userdoc:kittens:1:2", d.toString());
            assertEquals("[all]", gdm.getFieldSet());
            assertEquals(DocumentProtocol.Priority.LOW_2, gdm.getPriority());
            assertEquals(Route.parse("highwaytohell"), gdm.getRoute());
            assertTrue(lastTimeout >= gdm.getTimeRemaining());
            lastTimeout = gdm.getTimeRemaining();
        }

        {
            Message m = factory.messages.get(1);
            assertEquals(DocumentProtocol.MESSAGE_GETDOCUMENT, m.getType());
            GetDocumentMessage gdm = (GetDocumentMessage)m;
            DocumentId d = gdm.getDocumentId();
            assertEquals("userdoc:kittens:3:4", d.toString());
            assertEquals("[all]", gdm.getFieldSet());
            assertEquals(DocumentProtocol.Priority.LOW_2, gdm.getPriority());
            assertEquals(Route.parse("highwaytohell"), gdm.getRoute());
            assertTrue(lastTimeout >= gdm.getTimeRemaining());
        }
    }

    @Test
    public void testQueryOverridesConfig() throws Exception {
        String config = "raw:timeout 458\nroute \"route66\"";
        DocumentSessionFactory factory = new DocumentSessionFactory(docType);
        GetSearcher searcher = new GetSearcher(new FeedContext(
                new MessagePropertyProcessor(defFeedCfg, defLoadTypeCfg),
                factory, docMan, new ClusterList(), new NullFeedMetric()));
        Chain<Searcher> searchChain = new Chain<>(searcher);

        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(
                newQuery("?id[0]=userdoc:kittens:1:2&id[1]=userdoc:kittens:3:4&priority=LOW_2&route=highwaytohell&timeout=123"));

        assertEquals(2, factory.messages.size());
        {
            Message m = factory.messages.get(0);
            assertEquals(DocumentProtocol.MESSAGE_GETDOCUMENT, m.getType());
            GetDocumentMessage gdm = (GetDocumentMessage)m;
            DocumentId d = gdm.getDocumentId();
            assertEquals("userdoc:kittens:1:2", d.toString());
            assertEquals("[all]", gdm.getFieldSet());
            assertEquals(DocumentProtocol.Priority.LOW_2, gdm.getPriority());
            assertEquals(Route.parse("highwaytohell"), gdm.getRoute());
            assertEquals(123000, gdm.getTimeRemaining());
        }

        {
            Message m = factory.messages.get(1);
            assertEquals(DocumentProtocol.MESSAGE_GETDOCUMENT, m.getType());
            GetDocumentMessage gdm = (GetDocumentMessage)m;
            DocumentId d = gdm.getDocumentId();
            assertEquals("userdoc:kittens:3:4", d.toString());
            assertEquals("[all]", gdm.getFieldSet());
            assertEquals(DocumentProtocol.Priority.LOW_2, gdm.getPriority());
            assertEquals(Route.parse("highwaytohell"), gdm.getRoute());
            assertEquals(123000, gdm.getTimeRemaining());
        }
    }

    @Test
    public void testBadPriorityValue() throws Exception {
        DocumentSessionFactory factory = new DocumentSessionFactory(docType);
        GetSearcher searcher = new GetSearcher(new FeedContext(
                new MessagePropertyProcessor(defFeedCfg, defLoadTypeCfg),
                factory, docMan, new ClusterList(), new NullFeedMetric()));
        Chain<Searcher> searchChain = new Chain<>(searcher);

        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(
                newQuery("?id=userdoc:kittens:1:2&priority=onkel_jubalon"));

        assertNotNull(result.hits().getErrorHit());

        assertRendered("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<result>\n" +
                "<errors>\n" +
                "<error type=\"searcher\" code=\"3\" message=\"Illegal query: " +
                "java.lang.IllegalArgumentException: No enum const" +
                (Util.isJava7Compatible() ? "ant " : " class ") +
                "com.yahoo.documentapi.messagebus.protocol.DocumentProtocol" +
                (Util.isJava7Compatible() ? "." : "$") +
                "Priority.onkel_jubalon\"/>\n" +
                "</errors>\n" +
                "</result>\n", result);
    }

    @Test
    public void testMultiIdBadArrayIndex() throws Exception {
        DocumentSessionFactory factory = new DocumentSessionFactory(docType);
        GetSearcher searcher = new GetSearcher(new FeedContext(
                new MessagePropertyProcessor(defFeedCfg, defLoadTypeCfg),
                factory, docMan, new ClusterList(), new NullFeedMetric()));
        Chain<Searcher> searchChain = new Chain<>(searcher);

        {
            Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(
                    newQuery("?id[1]=userdoc:kittens:1:2"));

            assertNotNull(result.hits().getErrorHit());

            assertRendered("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<result>\n" +
                    "<errors>\n" +
                    "<error type=\"searcher\" code=\"3\" message=\"Illegal query: " +
                    "java.lang.IllegalArgumentException: query contains document ID " +
                    "array that is not zero-based and/or linearly increasing\"/>\n" +
                    "</errors>\n" +
                    "</result>\n", result);
        }

        {
            Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(
                    newQuery("?id[0]=userdoc:kittens:1:2&id[2]=userdoc:kittens:2:3"));

            assertNotNull(result.hits().getErrorHit());

            assertRendered("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<result>\n" +
                    "<errors>\n" +
                    "<error type=\"searcher\" code=\"3\" message=\"Illegal query: " +
                    "java.lang.IllegalArgumentException: query contains document ID " +
                    "array that is not zero-based and/or linearly increasing\"/>\n" +
                    "</errors>\n" +
                    "</result>\n", result);
        }

        {
            Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(
                    newQuery("?id[1]=userdoc:kittens:2:3"));

            assertNotNull(result.hits().getErrorHit());

            assertRendered("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<result>\n" +
                    "<errors>\n" +
                    "<error type=\"searcher\" code=\"3\" message=\"Illegal query: " +
                    "java.lang.IllegalArgumentException: query contains document ID " +
                    "array that is not zero-based and/or linearly increasing\"/>\n" +
                    "</errors>\n" +
                    "</result>\n", result);
        }

        {
            Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(
                    newQuery("?id[0=userdoc:kittens:1:2"));

            assertNotNull(result.hits().getErrorHit());

            assertRendered("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<result>\n" +
                    "<errors>\n" +
                    "<error type=\"searcher\" code=\"3\" message=\"Illegal query: " +
                    "java.lang.IllegalArgumentException: Malformed document ID array parameter\"/>\n" +
                    "</errors>\n" +
                    "</result>\n", result);
        }
    }

    @Test
    public void testLegacyHeadersOnly() throws Exception {
        DocumentSessionFactory factory = new DocumentSessionFactory(docType); // Needs auto-reply
        GetSearcher searcher = new GetSearcher(new FeedContext(
                new MessagePropertyProcessor(defFeedCfg, defLoadTypeCfg),
                factory, docMan, new ClusterList(), new NullFeedMetric()));
        Chain<Searcher> searchChain = new Chain<>(searcher);

        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(
                newQuery("?id=userdoc:kittens:1:2&headersonly=true"));

        assertEquals(1, factory.messages.size());
        {
            Message m = factory.messages.get(0);
            assertEquals(DocumentProtocol.MESSAGE_GETDOCUMENT, m.getType());
            GetDocumentMessage gdm = (GetDocumentMessage)m;
            DocumentId d = gdm.getDocumentId();
            assertEquals("userdoc:kittens:1:2", d.toString());
            assertEquals("[header]", gdm.getFieldSet());
        }
        assertEquals(1, result.hits().size());
        assertHits(result.hits(), "userdoc:kittens:1:2");
    }

    @Test
    public void testFieldSet() throws Exception {
    }

    @Test
    public void testConsistentResultOrdering() throws Exception {
        GetDocumentReply[] replies = new GetDocumentReply[] {
                new GetDocumentReply(new Document(docType, new DocumentId("userdoc:kittens:1:2"))),
                new GetDocumentReply(new Document(docType, new DocumentId("userdoc:kittens:7:8"))),
                new GetDocumentReply(new Document(docType, new DocumentId("userdoc:kittens:555:123")))
        };

        // Use a predefined reply list to ensure messages are answered out of order
        DocumentSessionFactory factory = new DocumentSessionFactory(docType, null, false, replies);
        GetSearcher searcher = new GetSearcher(new FeedContext(
                new MessagePropertyProcessor(defFeedCfg, defLoadTypeCfg),
                factory, docMan, new ClusterList(), new NullFeedMetric()));
        Chain<Searcher> searchChain = new Chain<>(searcher);

        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(
                newQuery("?id[0]=userdoc:kittens:555:123&id[1]=userdoc:kittens:1:2&id[2]=userdoc:kittens:7:8"));

        assertEquals(3, factory.messages.size());
        assertEquals(3, result.hits().size());
        // Hits must be in the same order as their document IDs in the query
        assertHits(result.hits(), "userdoc:kittens:555:123", "userdoc:kittens:1:2", "userdoc:kittens:7:8");

        assertEquals(0, ((DocumentHit)result.hits().get(0)).getIndex());
        assertEquals(1, ((DocumentHit)result.hits().get(1)).getIndex());
        assertEquals(2, ((DocumentHit)result.hits().get(2)).getIndex());
    }

    @Test
    public void testResultWithSingleError() throws Exception {
        com.yahoo.messagebus.Error err = new com.yahoo.messagebus.Error(32, "Alas, it went poorly");
        DocumentSessionFactory factory = new DocumentSessionFactory(docType, err, true);
        GetSearcher searcher = new GetSearcher(new FeedContext(
                new MessagePropertyProcessor(defFeedCfg, defLoadTypeCfg),
                factory, docMan, new ClusterList(), new NullFeedMetric()));
        Chain<Searcher> searchChain = new Chain<>(searcher);

        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(
                newQuery("?id[0]=userdoc:kittens:1:2&id[1]=userdoc:kittens:3:4"));
        assertNotNull(result.hits().getErrorHit());

        assertRendered("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<result>\n" +
                "<errors>\n" +
                "<error type=\"messagebus\" code=\"32\" message=\"Alas, it went poorly\"/>\n" +
                "</errors>\n" +
                "</result>\n", result);
    }

    @Test
    public void testResultWithMultipleErrors() throws Exception {
        Document doc1 = new Document(docType, new DocumentId("userdoc:kittens:77:88"));
        Document doc2 = new Document(docType, new DocumentId("userdoc:kittens:99:111"));
        GetDocumentReply errorReply1 = new GetDocumentReply(doc1);
        errorReply1.addError(new com.yahoo.messagebus.Error(123, "userdoc:kittens:77:88 had fleas."));
        GetDocumentReply errorReply2 = new GetDocumentReply(doc2);
        errorReply2.addError(new com.yahoo.messagebus.Error(456, "userdoc:kittens:99:111 shredded the curtains."));
        GetDocumentReply[] replies = new GetDocumentReply[] {
                errorReply1,
                errorReply2
        };

        Chain<Searcher> searchChain = createSearcherChain(replies);

        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(
                newQuery("?id[0]=userdoc:kittens:77:88&id[1]=userdoc:kittens:99:111"));

        assertRendered("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<result>\n" +
                "<errors>\n" +
                "<error type=\"messagebus\" code=\"123\" message=\"userdoc:kittens:77:88 had fleas.\"/>\n" +
                "<error type=\"messagebus\" code=\"456\" message=\"userdoc:kittens:99:111 shredded the curtains.\"/>\n" +
                "</errors>\n" +
                "</result>\n", result);
    }

    @Test
    public void testResultWithNullDocument() throws Exception {
        DocumentSessionFactory factory = new DocumentSessionFactory(docType, null, true);
        factory.setNullReply(true);
        GetSearcher searcher = new GetSearcher(new FeedContext(
                new MessagePropertyProcessor(defFeedCfg, defLoadTypeCfg),
                factory, docMan, new ClusterList(), new NullFeedMetric()));
        Chain<Searcher> searchChain = new Chain<>(searcher);

        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(
                newQuery("?id[0]=userdoc:kittens:55:bad_document_id"));
        // Document not found does not produce any hit at all, error or otherwise
        assertNull(result.hits().getErrorHit());

        assertRendered("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<result>\n" +
                "</result>\n", result);
    }

    @Test
    public void testDefaultDocumentHitRendering() throws Exception {
        Document doc1 = new Document(docType, new DocumentId("userdoc:kittens:3:4"));
        doc1.setFieldValue("name", new StringFieldValue("mittens"));
        doc1.setFieldValue("description", new StringFieldValue("it's a cat"));
        doc1.setFieldValue("fluffiness", new IntegerFieldValue(8));
        Document doc2 = new Document(docType, new DocumentId("userdoc:kittens:1:2"));
        doc2.setFieldValue("name", new StringFieldValue("garfield"));
        doc2.setFieldValue("description",
                new StringFieldValue("preliminary research indicates <em>hatred</em> of mondays. caution advised"));
        doc2.setFieldValue("fluffiness", new IntegerFieldValue(2));
        Document doc3 = new Document(docType, new DocumentId("userdoc:kittens:77:88"));
        GetDocumentReply errorReply = new GetDocumentReply(doc3);
        errorReply.addError(new com.yahoo.messagebus.Error(123, "userdoc:kittens:77:88 had fleas."));
        GetDocumentReply[] replies = new GetDocumentReply[] {
                new GetDocumentReply(doc1),
                new GetDocumentReply(doc2),
                errorReply
        };

        // Use a predefined reply list to ensure messages are answered out of order
        Chain<Searcher> searchChain = createSearcherChain(replies);

        Result xmlResult = new Execution(searchChain, Execution.Context.createContextStub()).search(
                newQuery("?id[0]=userdoc:kittens:77:88&id[1]=userdoc:kittens:1:2&id[2]=userdoc:kittens:3:4"));

        assertRendered("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                       "<result>\n" +
                       "<errors>\n" +
                       "<error type=\"messagebus\" code=\"123\" message=\"userdoc:kittens:77:88 had fleas.\"/>\n" +
                       "</errors>\n" +
                       "<document documenttype=\"kittens\" documentid=\"userdoc:kittens:1:2\">\n" +
                       "  <name>garfield</name>\n" +
                       "  <description>preliminary research indicates &lt;em&gt;hatred&lt;/em&gt; of mondays. caution advised</description>\n" +
                       "  <fluffiness>2</fluffiness>\n" +
                       "</document>\n" +
                       "<document documenttype=\"kittens\" documentid=\"userdoc:kittens:3:4\">\n" +
                       "  <name>mittens</name>\n" +
                       "  <description>it's a cat</description>\n" +
                       "  <fluffiness>8</fluffiness>\n" +
                       "</document>\n" +
                       "</result>\n", xmlResult);
    }

    @Test
    public void testDocumentFieldNoContentType() throws Exception {
        Document doc1 = new Document(docType, new DocumentId("userdoc:kittens:5:1"));
        doc1.setFieldValue("name", "derrick");
        doc1.setFieldValue("description", "kommisar katze");
        doc1.setFieldValue("fluffiness", 0);
        GetDocumentReply[] replies = new GetDocumentReply[] {
                new GetDocumentReply(doc1),
        };
        Chain<Searcher> searchChain = createSearcherChain(replies);

        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(
                newQuery("?id=userdoc:kittens:5:1&field=description"));

        assertNull(result.hits().getErrorHit());
        assertEquals("text/xml", result.getTemplating().getTemplates().getMimeType());
        assertEquals("UTF-8", result.getTemplating().getTemplates().getEncoding());

        assertRendered("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<result>kommisar katze</result>\n", result);
    }

    @Test
    public void testDocumentFieldEscapeXML() throws Exception {
        Document doc1 = new Document(docType, new DocumentId("userdoc:kittens:5:1"));
        doc1.setFieldValue("name", "asfd");
        doc1.setFieldValue("description", "<script type=\"evil/madness\">horror & screams</script>");
        doc1.setFieldValue("fluffiness", 0);
        GetDocumentReply[] replies = new GetDocumentReply[] {
                new GetDocumentReply(doc1),
        };
        Chain<Searcher> searchChain = createSearcherChain(replies);

        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(
                newQuery("?id=userdoc:kittens:5:1&field=description"));

        assertNull(result.hits().getErrorHit());
        assertEquals("text/xml", result.getTemplating().getTemplates().getMimeType());
        assertEquals("UTF-8", result.getTemplating().getTemplates().getEncoding());

        assertRendered("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<result>&lt;script type=\"evil/madness\"&gt;horror &amp; screams&lt;/script&gt;</result>\n", result);
    }

    @Test
    public void testDocumentFieldRawContent() throws Exception {
        byte[] contentBytes = new byte[] { 0, -128, 127 };

        Document doc1 = new Document(docType, new DocumentId("userdoc:kittens:123:456"));
        doc1.setFieldValue("foo", new Raw(ByteBuffer.wrap(contentBytes)));
        GetDocumentReply[] replies = new GetDocumentReply[] {
                new GetDocumentReply(doc1)
        };

        Chain<Searcher> searchChain = createSearcherChain(replies);

        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(
                newQuery("?id=userdoc:kittens:123:456&field=foo"));

        assertNull(result.hits().getErrorHit());
        assertEquals("application/octet-stream", result.getTemplating().getTemplates().getMimeType());

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        SearchRendererAdaptor.callRender(stream, result);
        stream.flush();

        byte[] resultBytes = stream.toByteArray();
        assertEquals(contentBytes.length, resultBytes.length);
        for (int i = 0; i < resultBytes.length; ++i) {
            assertEquals(contentBytes[i], resultBytes[i]);
        }
    }

    @Test
    public void testDocumentFieldRawWithContentOverride() throws Exception {
        byte[] contentBytes = new byte[] { 0, -128, 127 };

        Document doc1 = new Document(docType, new DocumentId("userdoc:kittens:123:456"));
        doc1.setFieldValue("foo", new Raw(ByteBuffer.wrap(contentBytes)));
        GetDocumentReply[] replies = new GetDocumentReply[] {
                new GetDocumentReply(doc1)
        };

        Chain<Searcher> searchChain = createSearcherChain(replies);

        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(
                newQuery("?id=userdoc:kittens:123:456&field=foo&contenttype=text/fancy"));

        assertNull(result.hits().getErrorHit());
        assertEquals("text/fancy", result.getTemplating().getTemplates().getMimeType());

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        SearchRendererAdaptor.callRender(stream, result);
        stream.flush();

        byte[] resultBytes = stream.toByteArray();
        assertEquals(contentBytes.length, resultBytes.length);
        for (int i = 0; i < resultBytes.length; ++i) {
            assertEquals(contentBytes[i], resultBytes[i]);
        }
    }

    @Test
    public void testDocumentFieldWithMultipleIDs() throws Exception {
        DocumentSessionFactory factory = new DocumentSessionFactory(docType);
        GetSearcher searcher = new GetSearcher(new FeedContext(
                new MessagePropertyProcessor(defFeedCfg, defLoadTypeCfg),
                factory, docMan, new ClusterList(), new NullFeedMetric()));
        Chain<Searcher> searchChain = new Chain<>(searcher);

        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(
                newQuery("?id[0]=userdoc:kittens:1:2&id[1]=userdoc:kittens:3:4&field=name"));
        assertNotNull(result.hits().getErrorHit());

        assertRendered("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<result>\n" +
                "<errors>\n" +
                "<error type=\"searcher\" code=\"3\" message=\"Illegal query: " +
                "java.lang.IllegalArgumentException: Field only valid for single document id query\"/>\n" +
                "</errors>\n" +
                "</result>\n", result);
    }

    @Test
    public void testDocumentFieldNotSet() throws Exception {
        Document doc1 = new Document(docType, new DocumentId("userdoc:kittens:5:1"));
        doc1.setFieldValue("name", "asdf");
        doc1.setFieldValue("description", "fdsafsdf");
        doc1.setFieldValue("fluffiness", 10);
        GetDocumentReply[] replies = new GetDocumentReply[] {
                new GetDocumentReply(doc1),
        };
        Chain<Searcher> searchChain = createSearcherChain(replies);

        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(
                newQuery("?id=userdoc:kittens:5:1&field=image"));

        assertNotNull(result.hits().getErrorHit());
        assertEquals(1, result.hits().size());

        assertRendered("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<result>\n" +
                "<errors>\n" +
                "<error type=\"searcher\" code=\"16\" message=\"Resource not found.: " +
                "Field 'image' found in document type, but had no content in userdoc:kittens:5:1\"/>\n" +
                "</errors>\n" +
                "</result>\n", result);
    }


    @Test
    public void testDocumentFieldWithDocumentNotFound() throws Exception {
        DocumentSessionFactory factory = new DocumentSessionFactory(docType, null, true);
        factory.setNullReply(true);
        GetSearcher searcher = new GetSearcher(new FeedContext(
                new MessagePropertyProcessor(defFeedCfg, defLoadTypeCfg),
                factory, docMan, new ClusterList(), new NullFeedMetric()));
        Chain<Searcher> searchChain = new Chain<>(searcher);

        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(
                newQuery("?id=userdoc:kittens:1:2&field=name"));
        assertNotNull(result.hits().getErrorHit());

        assertRendered("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<result>\n" +
                "<errors>\n" +
                "<error type=\"searcher\" code=\"16\" message=\"Resource not found.: " +
                "Document not found, could not return field 'name'\"/>\n" +
                "</errors>\n" +
                "</result>\n", result);
    }

    @Test
    public void testDocumentFieldNotReachableWithHeadersOnly() throws Exception {
        Document doc1 = new Document(docType, new DocumentId("userdoc:kittens:5:1"));
        doc1.setFieldValue("name", "asdf");
        // don't set body fields
        GetDocumentReply[] replies = new GetDocumentReply[] {
                new GetDocumentReply(doc1),
        };
        Chain<Searcher> searchChain = createSearcherChain(replies);

        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(
                newQuery("?id=userdoc:kittens:5:1&field=description&headersonly=true"));

        assertNotNull(result.hits().getErrorHit());
        assertEquals(1, result.hits().size());

        assertRendered("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<result>\n" +
                "<errors>\n" +
                "<error type=\"searcher\" code=\"4\" message=\"Invalid query parameter: " +
                "Field 'description' is located in document body, but headersonly " +
                "prevents it from being retrieved in userdoc:kittens:5:1\"/>\n" +
                "</errors>\n" +
                "</result>\n", result);
    }

    @Test
    public void testVespaXMLTemplate() throws Exception {
        Document doc1 = new Document(docType, new DocumentId("userdoc:kittens:3:4"));
        doc1.setFieldValue("name", "mittens");
        doc1.setFieldValue("description", "it's a cat");
        doc1.setFieldValue("fluffiness", 8);
        Document doc2 = new Document(docType, new DocumentId("userdoc:kittens:1:2"));
        doc2.setFieldValue("name", "garfield");
        doc2.setFieldValue("description", "preliminary research indicates <em>hatred</em> of mondays. caution advised");
        doc2.setFieldValue("fluffiness", 2);
        Document doc3 = new Document(docType, new DocumentId("userdoc:kittens:77:88"));
        GetDocumentReply errorReply = new GetDocumentReply(doc3);
        errorReply.addError(new com.yahoo.messagebus.Error(123, "userdoc:kittens:77:88 lost in a <ni!>\"shrubbery\"</ni!>"));
        GetDocumentReply[] replies = new GetDocumentReply[] {
                new GetDocumentReply(doc1),
                new GetDocumentReply(doc2),
                errorReply
        };

        // Use a predefined reply list to ensure messages are answered out of order
        Chain<Searcher> searchChain = createSearcherChain(replies);

        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(
                newQuery("?id[0]=userdoc:kittens:77:88&id[1]=userdoc:kittens:1:2&id[2]=userdoc:kittens:3:4")); // TODO!

        assertRendered("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<result>\n" +
                "<errors>\n" +
                "<error type=\"messagebus\" code=\"123\" message=\"userdoc:kittens:77:88 lost in a &lt;ni!&gt;&quot;shrubbery&quot;&lt;/ni!&gt;\"/>\n"+
                "</errors>\n" +
                "<document documenttype=\"kittens\" documentid=\"userdoc:kittens:1:2\">\n" +
                "  <name>garfield</name>\n" +
                "  <description>preliminary research indicates &lt;em&gt;hatred&lt;/em&gt; of mondays. caution advised</description>\n" +
                "  <fluffiness>2</fluffiness>\n" +
                "</document>\n" +
                "<document documenttype=\"kittens\" documentid=\"userdoc:kittens:3:4\">\n" +
                "  <name>mittens</name>\n" +
                "  <description>it's a cat</description>\n" +
                "  <fluffiness>8</fluffiness>\n" +
                "</document>\n" +
                "</result>\n", result);
    }

    @Test
    public void testDocumentHitWithPopulatedHitFields() throws Exception {
        Document doc1 = new Document(docType, new DocumentId("userdoc:kittens:1234:foo"));
        doc1.setFieldValue("name", new StringFieldValue("megacat"));
        doc1.setFieldValue("description", new StringFieldValue("supercat"));
        doc1.setFieldValue("fluffiness", new IntegerFieldValue(10000));
        GetDocumentReply[] replies = new GetDocumentReply[] {
                new GetDocumentReply(doc1)
        };

        // Use a predefined reply list to ensure messages are answered out of order
        Chain<Searcher> searchChain = createSearcherChain(replies);

        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(
                newQuery("?id=userdoc:kittens:1234:foo&populatehitfields=true"));
        assertEquals(1, result.hits().size());
        assertHits(result.hits(), "userdoc:kittens:1234:foo");

        DocumentHit hit = (DocumentHit)result.hits().get(0);
        Iterator<Map.Entry<String, Object>> iter = hit.fieldIterator();
        Set<String> fieldSet = new TreeSet<>();
        while (iter.hasNext()) {
            Map.Entry<String, Object> kv = iter.next();
            StringBuilder field = new StringBuilder();
            field.append(kv.getKey()).append(" -> ").append(kv.getValue());
            fieldSet.add(field.toString());
        }
        StringBuilder fields = new StringBuilder();
        for (String s : fieldSet) {
            fields.append(s).append("\n");
        }
        assertEquals(
                "description -> supercat\n" +
                "documentid -> userdoc:kittens:1234:foo\n" +
                "fluffiness -> 10000\n" +
                "name -> megacat\n",
                fields.toString());
    }

    @Test
    public void deserializationExceptionsAreHandledGracefully() throws Exception {
        Document doc1 = new Document(docType, new DocumentId("userdoc:kittens:5:1"));
        GetDocumentReply[] replies = new GetDocumentReply[] {
                new MockFailingGetDocumentReply(doc1),
        };
        Chain<Searcher> searchChain = createSearcherChain(replies);
        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(newQuery("?id=userdoc:kittens:5:1"));
        assertRendered("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<result>\n" +
                "<errors>\n" +
                "<error type=\"searcher\" code=\"18\" message=\"Internal server error.: " +
                "Got exception of type java.lang.RuntimeException during document " +
                "deserialization: epic dragon attack\"/>\n"+
                "</errors>\n" +
                "</result>\n", result);
    }

    @Test
    public void testJsonRendererSetting() throws Exception {
        DocumentSessionFactory factory = new DocumentSessionFactory(docType); // Needs auto-reply
        GetSearcher searcher = new GetSearcher(new FeedContext(
                new MessagePropertyProcessor(defFeedCfg, defLoadTypeCfg),
                factory, docMan, new ClusterList(), new NullFeedMetric()));
        Chain<Searcher> searchChain = new Chain<>(searcher);

        Query query = newQuery("?id=userdoc:kittens:1:2&format=json");
        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(query);
        assertFalse(result.getTemplating().getTemplates() instanceof DocumentXMLTemplate);
    }

    private Query newQuery(String queryString) {
        return new Query(HttpRequest.createTestRequest(queryString, com.yahoo.jdisc.http.HttpRequest.Method.GET));
    }
    
    private Chain<Searcher> createSearcherChain(GetDocumentReply[] replies) throws Exception {
        DocumentSessionFactory factory = new DocumentSessionFactory(docType, null, false, replies);
        GetSearcher searcher = new GetSearcher(new FeedContext(
                new MessagePropertyProcessor(defFeedCfg, defLoadTypeCfg),
                factory, docMan, new ClusterList(), new NullFeedMetric()));
        return new Chain<>(searcher);
    }

    private static class MockFailingGetDocumentReply extends GetDocumentReply {
        private int countdown = 2;

        private MockFailingGetDocumentReply(Document doc) {
            super(doc);
        }

        @Override
        public Document getDocument() {
            // Reason for countdown is that the test DocumentSessionFactory calls
            // getDocument once internally before code can ever reach handleReply.
            if (--countdown == 0) {
                throw new RuntimeException("epic dragon attack");
            }
            return super.getDocument();
        }
    }

    private static class MockBackend extends Searcher {
        private Hit hitToReturn;

        public MockBackend(Hit hitToReturn) {
            this.hitToReturn = hitToReturn;
        }

        @Override
        public Result search(Query query, Execution execution) {
            Result result = new Result(query);
            result.hits().add(hitToReturn);
            return result;
        }
    }

    private class MockHttpRequest {

        private final String req;
        private byte[] data;
        private boolean gzip = false;

        MockHttpRequest(byte[] data, String req) {
            this.req = req;
            this.data = data;
        }

        MockHttpRequest(byte[] data, String req, boolean gzip) {
            this.data = data;
            this.req = req;
            this.gzip = gzip;
        }

        public InputStream getData() {
            if (gzip) {
                try {
                    ByteArrayOutputStream rawOut = new ByteArrayOutputStream();
                    GZIPOutputStream compressed = new GZIPOutputStream(rawOut);
                    compressed.write(data, 0, data.length);
                    compressed.finish();
                    compressed.flush();
                    rawOut.flush();
                    return new ByteArrayInputStream(rawOut.toByteArray());
                } catch (Exception e) {
                    return null;
                }
            }
            return new ByteArrayInputStream(data);
        }

        public void addHeaders(HeaderFields headers) {
            headers.add("Content-Type", "text/plain;encoding=UTF-8");
            if (gzip)
                headers.add("Content-Encoding", "gzip");
        }

        public com.yahoo.container.jdisc.HttpRequest toRequest() {
            com.yahoo.container.jdisc.HttpRequest request = com.yahoo.container.jdisc.HttpRequest.createTestRequest(req, com.yahoo.jdisc.http.HttpRequest.Method.GET, getData());
            addHeaders(request.getJDiscRequest().headers());
            return request;
        }

    }

    public static void assertRendered(String expected,Result result) throws Exception {
        assertRendered(expected,result,true);
    }

    public static void assertRendered(String expected,Result result,boolean checkFullEquality) throws Exception {
        if (checkFullEquality)
            assertEquals(expected, ResultRenderingUtil.getRendered(result));
        else
            assertTrue(ResultRenderingUtil.getRendered(result).startsWith(expected));
    }

}
