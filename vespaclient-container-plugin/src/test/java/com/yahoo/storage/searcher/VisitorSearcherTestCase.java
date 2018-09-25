// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.storage.searcher;

import com.yahoo.component.chain.Chain;
import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.protect.Error;
import com.yahoo.documentapi.VisitorSession;
import com.yahoo.feedhandler.NullFeedMetric;
import com.yahoo.vespa.config.content.LoadTypeConfig;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.feedapi.FeedContext;
import com.yahoo.feedapi.MessagePropertyProcessor;
import com.yahoo.messagebus.StaticThrottlePolicy;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.vdslib.VisitorOrdering;
import com.yahoo.vespaclient.ClusterList;
import com.yahoo.vespaclient.config.FeederConfig;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("deprecation")
public class VisitorSearcherTestCase {

    private DocumentTypeManager docMan = null;
    private DocumentType docType;
    DocumentSessionFactory factory;

    @org.junit.Before
    public void setUp() {
        docMan = new DocumentTypeManager();
        docType = new DocumentType("kittens");
        docType.addHeaderField("name", DataType.STRING);
        docType.addField("description", DataType.STRING);
        docType.addField("image", DataType.RAW);
        docType.addField("fluffiness", DataType.INT);
        docType.addField("foo", DataType.RAW);
        docMan.registerDocumentType(docType);
        factory = new DocumentSessionFactory(docType);
    }

    public VisitSearcher create() throws Exception {
        ClusterListConfig.Storage.Builder storageCluster = new ClusterListConfig.Storage.Builder().configid("storage/cluster.foobar").name("foobar");
        ClusterListConfig clusterListCfg = new ClusterListConfig(new ClusterListConfig.Builder().storage(storageCluster));
        ClusterList clusterList = new ClusterList(clusterListCfg);
        return new VisitSearcher(new FeedContext(
                new MessagePropertyProcessor(new FeederConfig(new FeederConfig.Builder().timeout(458).route("riksveg18").retryenabled(true)),
                        new LoadTypeConfig(new LoadTypeConfig.Builder())),
                factory, docMan, clusterList, new NullFeedMetric()));
    }

    @Test
    public void testQueryParameters() throws Exception {
        VisitSearcher searcher = create();
        VisitorParameters params = searcher.getVisitorParameters(
                newQuery("visit?visit.selection=id.user=1234&visit.cluster=foobar" +
                         "&visit.dataHandler=othercluster&visit.fieldSet=[header]&visit.fromTimestamp=112&visit.toTimestamp=224" +
                         "&visit.maxBucketsPerVisitor=2&visit.maxPendingMessagesPerVisitor=7&visit.maxPendingVisitors=14" +
                         "&visit.ordering=ASCENDING&priority=NORMAL_1&tracelevel=7&visit.visitInconsistentBuckets&visit.visitRemoves"), null);

        assertEquals("id.user=1234", params.getDocumentSelection());
        assertEquals(7, params.getMaxPending());
        assertEquals(2, params.getMaxBucketsPerVisitor());
        assertEquals(14, ((StaticThrottlePolicy)params.getThrottlePolicy()).getMaxPendingCount());
        assertEquals("[Storage:cluster=foobar;clusterconfigid=storage/cluster.foobar]", params.getRoute().toString());
        assertEquals("othercluster", params.getRemoteDataHandler());
        assertEquals("[header]", params.fieldSet());
        assertEquals(112, params.getFromTimestamp());
        assertEquals(224, params.getToTimestamp());
        assertEquals(VisitorOrdering.ASCENDING, params.getVisitorOrdering());
        assertEquals(DocumentProtocol.Priority.NORMAL_1, params.getPriority());
        assertEquals(7, params.getTraceLevel());
        assertEquals(true, params.visitInconsistentBuckets());
        assertEquals(true, params.visitRemoves());
    }

    @Test
    public void timestampQueryParametersAreParsedAsLongs() throws Exception {
        VisitorParameters params = create().getVisitorParameters(
                newQuery("visit?visit.selection=id.user=1234&" +
                         "visit.fromTimestamp=1419021596000000&" +
                         "visit.toTimestamp=1419021597000000"), null);
        assertEquals(1419021596000000L, params.getFromTimestamp());
        assertEquals(1419021597000000L, params.getToTimestamp());
    }

    @Test
    public void testQueryParametersDefaults() throws Exception {
        VisitSearcher searcher = create();
        VisitorParameters params = searcher.getVisitorParameters(
                newQuery("visit?visit.selection=id.user=1234&hits=100"), null);

        assertEquals("id.user=1234", params.getDocumentSelection());
        assertEquals(1, params.getMaxBucketsPerVisitor());
        assertEquals(1, ((StaticThrottlePolicy)params.getThrottlePolicy()).getMaxPendingCount());
        assertEquals(1, params.getMaxFirstPassHits());
        assertEquals(1, params.getMaxTotalHits());
        assertEquals(32, params.getMaxPending());
        assertEquals(false, params.visitInconsistentBuckets());
    }

    @Test
    public void testWrongCluster() throws Exception {
        VisitSearcher searcher = create();

        try {
            searcher.getVisitorParameters(
                    newQuery("visit?visit.selection=id.user=1234&visit.cluster=unknown"), null);

            assertTrue(false);
        } catch (Exception e) {
            // e.printStackTrace();
        }
    }


    @Test(expected = IllegalArgumentException.class)
    public void testNoClusterParamWhenSeveralClusters() throws Exception {
        DocumentSessionFactory factory = new DocumentSessionFactory(docType);
        ClusterListConfig.Storage.Builder storageCluster1 = new ClusterListConfig.Storage.Builder().configid("storage/cluster.foo").name("foo");
        ClusterListConfig.Storage.Builder storageCluster2 = new ClusterListConfig.Storage.Builder().configid("storage/cluster.bar").name("bar");
        ClusterListConfig clusterListCfg = new ClusterListConfig(new ClusterListConfig.Builder().storage(Arrays.asList(storageCluster1, storageCluster2)));
        ClusterList clusterList = new ClusterList(clusterListCfg);
        VisitSearcher searcher = new VisitSearcher(new FeedContext(
                new MessagePropertyProcessor(new FeederConfig(new FeederConfig.Builder().timeout(100).route("whatever").retryenabled(true)),
                        new LoadTypeConfig(new LoadTypeConfig.Builder())),
                factory, docMan, clusterList, new NullFeedMetric()));

            searcher.getVisitorParameters(newQuery("visit?visit.selection=id.user=1234"), null);
    }

    @Test
    public void testSimple() throws Exception {
        Chain<Searcher> searchChain = new Chain<>(create());
        Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(newQuery("visit?visit.selection=id.user=1234&hits=100"));
        assertEquals(1, result.hits().size());
        assertRendered(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<result>\n" +
                "<document documenttype=\"kittens\" documentid=\"userdoc:foo:1234:bar\"/>\n" +
                "</result>\n", result);
    }

    private Result invokeVisitRemovesSearchChain() throws Exception {
        Chain<Searcher> searchChain = new Chain<>(create());
        return new Execution(searchChain, Execution.Context.createContextStub()).search(
                newQuery("visit?visit.selection=id.user=1234&hits=100&visit.visitRemoves=true"));
    }

    @Test
    public void visitRemovesIncludesRemoveEntriesInResultXml() throws Exception {
        Result result = invokeVisitRemovesSearchChain();
        assertEquals(2, result.hits().size());
        assertRendered(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<result>\n" +
                "<document documenttype=\"kittens\" documentid=\"userdoc:foo:1234:bar\"/>\n" +
                "<remove documentid=\"userdoc:foo:1234:removed\"/>\n" +
                "</result>\n", result);
    }

    @Test
    public void removedDocumentIdsAreXmlEscaped() throws Exception {
        factory = mock(DocumentSessionFactory.class);
        when(factory.createVisitorSession(any(VisitorParameters.class))).thenAnswer((p) -> {
            VisitorParameters params = (VisitorParameters)p.getArguments()[0];
            DummyVisitorSession session = new DummyVisitorSession(params, docType);
            session.clearAutoReplyMessages();
            session.addRemoveReply("userdoc:foo:1234:<rem\"o\"ved&stuff>");
            return session;
        });
        Result result = invokeVisitRemovesSearchChain();
        assertEquals(1, result.hits().size());
        assertRendered(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<result>\n" +
                "<remove documentid=\"userdoc:foo:1234:&lt;rem&quot;o&quot;ved&amp;stuff&gt;\"/>\n" +
                "</result>\n", result);
    }

    private Result invokeSearcherWithUserQuery() throws Exception {
        Chain<Searcher> searchChain = new Chain<>(create());
        return new Execution(searchChain, Execution.Context.createContextStub())
                .search(new Query("visit?visit.selection=id.user=1234&hits=100"));
    }

    @Test
    public void waitUntilDoneFailureReturnsTimeoutErrorHit() throws Exception {
        VisitorSession session = mock(VisitorSession.class);
        when(session.waitUntilDone(anyLong())).thenReturn(false);
        factory = mock(DocumentSessionFactory.class);
        when(factory.createVisitorSession(any(VisitorParameters.class))).thenReturn(session);

        Result result = invokeSearcherWithUserQuery();
        assertNotNull(result.hits().getErrorHit());
        assertEquals(Error.TIMEOUT.code, result.hits().getErrorHit().errors().iterator().next().getCode());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testRendererWiring() throws Exception {
        Chain<Searcher> searchChain = new Chain<>(create());
        {
            Query query = newQuery("visit?visit.selection=id.user=1234&hits=100&format=json");
            Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(query);
            assertEquals(com.yahoo.prelude.templates.DefaultTemplateSet.class, result.getTemplating().getTemplates().getClass());
        }
        {
            Query query = newQuery("visit?visit.selection=id.user=1234&hits=100&format=JsonRenderer");
            Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(query);
            assertEquals(com.yahoo.prelude.templates.DefaultTemplateSet.class, result.getTemplating().getTemplates().getClass());
        }
        {
            Query query = newQuery("visit?visit.selection=id.user=1234&hits=100");
            Result result = new Execution(searchChain, Execution.Context.createContextStub()).search(query);
            assertEquals(DocumentXMLTemplate.class, result.getTemplating().getTemplates().getClass());
        }
    }

    public static void assertRendered(String expected, Result result) throws Exception {
        assertEquals(expected, ResultRenderingUtil.getRendered(result));
    }

    private Query newQuery(String queryString) {
        return new Query(HttpRequest.createTestRequest(queryString, com.yahoo.jdisc.http.HttpRequest.Method.GET));
    }

}
