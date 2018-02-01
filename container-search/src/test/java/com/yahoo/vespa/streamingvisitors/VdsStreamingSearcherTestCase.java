// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors;

import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.document.select.parser.TokenMgrError;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.fs4.QueryPacket;
import com.yahoo.prelude.Ping;
import com.yahoo.prelude.Pong;
import com.yahoo.prelude.fastsearch.*;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.searchlib.aggregation.Grouping;
import com.yahoo.vdslib.DocumentSummary;
import com.yahoo.vdslib.SearchResult;
import com.yahoo.vdslib.VisitorStatistics;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:ulf@yahoo-inc.com">Ulf Carlin</a>
 */
public class VdsStreamingSearcherTestCase {
    public static final String USERDOC_ID_PREFIX = "userdoc:namespace:1:userspecific";
    public static final String GROUPDOC_ID_PREFIX = "groupdoc:namespace:group1:userspecific";

    private static class MockVisitor implements Visitor {
        private Query query;
        String searchCluster;
        Route route;
        int totalHitCount;
        private final List<SearchResult.Hit> hits = new ArrayList<>();
        private final Map<String, DocumentSummary.Summary> summaryMap = new HashMap<>();
        private final List<Grouping> groupings = new ArrayList<>();

        MockVisitor(Query query, String searchCluster, Route route) {
            this.query = query;
            this.searchCluster = searchCluster;
            this.route = route;
        }

        @Override
        public void doSearch() throws InterruptedException, ParseException, TimeoutException {
            String queryString = query.getModel().getQueryString();
            if (queryString.compareTo("parseexception") == 0) {
                throw new ParseException("Parsing failed");
            } else if (queryString.compareTo("tokenizeexception") == 0) {
                throw new TokenMgrError("Tokenization failed", 0);
            } else if (queryString.compareTo("interruptedexception") == 0) {
                throw new InterruptedException("Interrupted");
            } else if (queryString.compareTo("timeoutexception") == 0) {
                throw new TimeoutException("Timed out");
            } else if (queryString.compareTo("illegalargumentexception") == 0) {
                throw new IllegalArgumentException("Illegal argument");
            } else if (queryString.compareTo("nosummary") == 0) {
                String docId = USERDOC_ID_PREFIX + 0;
                totalHitCount = 1;
                hits.add(new SearchResult.Hit(docId, 1.0));
            } else if (queryString.compareTo("nosummarytofill") == 0) {
                addResults(USERDOC_ID_PREFIX, 1, true);
            } else if (queryString.compareTo("oneuserhit") == 0) {
                addResults(USERDOC_ID_PREFIX, 1, false);
            } else if (queryString.compareTo("twouserhits") == 0) {
                addResults(USERDOC_ID_PREFIX, 2, false);
            } else if (queryString.compareTo("twogrouphitsandoneuserhit") == 0) {
                addResults(GROUPDOC_ID_PREFIX, 2, false);
                addResults(USERDOC_ID_PREFIX, 1, false);
            } else if (queryString.compareTo("onegroupinghit") == 0) {
                groupings.add(new Grouping());
            }

        }

        private void addResults(String idPrefix, int hitCount, boolean emptyDocsum) {
            totalHitCount += hitCount;
            for (int i=0; i<hitCount; ++i) {
                String docId = idPrefix + i;
                byte[] summary;
                if (emptyDocsum) {
                    summary = new byte[] {};
                } else {
                    summary = new byte[] { 0x55, 0x55, 0x55, 0x55 }; // Fake docsum data
                }
                hits.add(new SearchResult.Hit(docId, 1.0));
                summaryMap.put(docId, new DocumentSummary.Summary(docId, summary));
            }
        }

        @Override
        public VisitorStatistics getStatistics() {
            return new VisitorStatistics();
        }

        @Override
        public List<SearchResult.Hit> getHits() {
            return hits;
        }

        @Override
        public Map<String, DocumentSummary.Summary> getSummaryMap() {
            return summaryMap;
        }

        @Override
        public int getTotalHitCount() {
            return totalHitCount;
        }

        @Override
        public List<Grouping> getGroupings() {
            return groupings;
        }
    }

    private static class MockVisitorFactory implements VisitorFactory {
        @Override
        public Visitor createVisitor(Query query, String searchCluster, Route route) {
            return new MockVisitor(query, searchCluster, route);
        }
    }

    private static Result executeQuery(VdsStreamingSearcher searcher, Query query) {
        QueryPacket queryPacket = QueryPacket.create(query);
        CacheKey cacheKey = new CacheKey(queryPacket);
        Execution execution = new Execution(new Execution.Context(null, null, null, null, null));
        return searcher.doSearch2(query, queryPacket, cacheKey, execution);
    }

    private static Query[] generateTestQueries(String queryString) {
        Query[] queries = new Query[4]; // Increase coverage
        for (int i = 0; i<queries.length; i++) {
            Query query = new Query(queryString);
            if (i == 0) {
            } else if (i == 1) {
                query.getPresentation().setSummary("summary");
            } else if (i == 2) {
                query.setTraceLevel(100);
            } else if (i == 3) {
                query.getPresentation().setSummary("summary");
                query.setTraceLevel(100);
            }
            queries[i] = query;
        }
        return queries;
    }

    private static void checkError(VdsStreamingSearcher searcher, String queryString, String message, String detailedMessage) {
        for (Query query : generateTestQueries(queryString)) {
            Result result = executeQuery(searcher, query);
            assertNotNull(result.hits().getError());
            assertTrue("Expected '" + message + "' to be contained in '"
                    + result.hits().getErrorHit().errors().iterator().next().getMessage() + "'",
                    result.hits().getErrorHit().errors().iterator().next().getMessage().contains(message));
            assertTrue("Expected '" + detailedMessage + "' to be contained in '"
                    + result.hits().getErrorHit().errors().iterator().next().getDetailedMessage() + "'",
                    result.hits().getErrorHit().errors().iterator().next().getDetailedMessage().contains(detailedMessage));
        }
    }

    private static void checkSearch(VdsStreamingSearcher searcher, String queryString, int hitCount, String idPrefix) {
        for (Query query : generateTestQueries(queryString)) {
            Result result = executeQuery(searcher, query);
            assertNull(result.hits().getError());
            assertEquals(result.hits().size(), hitCount);
            for (int i=0; i<result.hits().size(); ++i) {
                Hit hit = result.hits().get(i);
                if (idPrefix != null) {
                    assertEquals("clusterName", hit.getSource());
                    assertEquals(idPrefix + i, hit.getId().toString());
                } else {
                    assertNull(hit.getSource());
                    assertEquals("meta:grouping", hit.getId().toString());
                }
            }
        }
    }

    private static void checkGrouping(VdsStreamingSearcher searcher, String queryString, int hitCount) {
        checkSearch(searcher, queryString, hitCount, null);
    }

    @Test
    public void testBasics() {
        MockVisitorFactory factory = new MockVisitorFactory();
        VdsStreamingSearcher searcher = new VdsStreamingSearcher(factory);

        ConfigGetter<DocumentdbInfoConfig> getter = new ConfigGetter<>(DocumentdbInfoConfig.class);
        DocumentdbInfoConfig config = getter.getConfig("file:src/test/java/com/yahoo/prelude/fastsearch/test/documentdb-info.cfg");
        searcher.init(new SummaryParameters("default"),
                new ClusterParams("clusterName"),
                new CacheParams(100, 1e64),
                config);

        // Magic query values are used to trigger specific behaviors from mock visitor.
        checkError(searcher, "/?query=noselection",
                "Backend communication error", "Streaming search needs one and only one");
        checkError(searcher, "/?streaming.userid=1&query=parseexception",
                "Backend communication error", "Failed to parse document selection string");
        checkError(searcher, "/?streaming.userid=1&query=tokenizeexception",
                "Backend communication error", "Failed to tokenize document selection string");
        checkError(searcher, "/?streaming.userid=1&query=interruptedexception",
                "Backend communication error", "Interrupted");
        checkError(searcher, "/?streaming.userid=1&query=timeoutexception",
                "Timed out", "Timed out");
        checkError(searcher, "/?streaming.userid=1&query=illegalargumentexception",
                "Backend communication error", "Illegal argument");
        checkError(searcher, "/?streaming.userid=1&query=nosummary",
                "Backend communication error", "Did not find summary for hit with document id");
        checkError(searcher, "/?streaming.userid=1&query=nosummarytofill",
                "Timed out", "Missing hit summary data for 1 hits");

        checkSearch(searcher, "/?streaming.userid=1&query=oneuserhit", 1, USERDOC_ID_PREFIX);
        checkSearch(searcher, "/?streaming.userid=1&query=oneuserhit&sorting=%2Bsurname", 1, USERDOC_ID_PREFIX);
        checkSearch(searcher, "/?streaming.selection=id.user%3D%3d1&query=twouserhits", 2, USERDOC_ID_PREFIX);
        checkSearch(searcher, "/?streaming.groupname=group1&query=twogrouphitsandoneuserhit", 2, GROUPDOC_ID_PREFIX);

        checkGrouping(searcher, "/?streaming.selection=true&query=onegroupinghit", 1);
    }

    @Test
    public void testTrivialitiesToIncreaseCoverage() {
        VdsStreamingSearcher searcher = new VdsStreamingSearcher();

        assertNull(searcher.getSearchClusterConfigId());
        String searchClusterConfigId = "searchClusterConfigId";
        searcher.setSearchClusterConfigId(searchClusterConfigId);
        assertEquals(searchClusterConfigId, searcher.getSearchClusterConfigId());

        assertNull(searcher.getStorageClusterRouteSpec());
        String storageClusterRouteSpec = "storageClusterRouteSpec";
        searcher.setStorageClusterRouteSpec(storageClusterRouteSpec);
        assertEquals(storageClusterRouteSpec, searcher.getStorageClusterRouteSpec());

        Pong pong = searcher.ping(new Ping(), new Execution(new Execution.Context(null, null, null, null, null)));
        assertEquals(0, pong.getErrorSize());
    }

    @Test
    public void testVerifyDocId() {
        Query generalQuery = new Query("/?streaming.selection=true&query=test");
        Query user1Query = new Query("/?streaming.userid=1&query=test");
        Query group1Query = new Query("/?streaming.groupname=group1&query=test");
        String userId1 = "userdoc:namespace:1:userspecific";
        String userId2 = "userdoc:namespace:2:userspecific";
        String groupId1 = "groupdoc:namespace:group1:userspecific";
        String groupId2 = "groupdoc:namespace:group2:userspecific";
        String orderIdGroup1 = "orderdoc(3,1):storage_test:group1:0:userspecific";
        String orderIdGroup2 = "orderdoc(5,2):storage_test:group2:0:userspecific";
        String orderIdUser1 = "orderdoc(3,1):storage_test:1:0:userspecific";
        String orderIdUser2 = "orderdoc(5,2):storage_test:2:0:userspecific";
        String badId = "unknowscheme:namespace:something";

        assertTrue(VdsStreamingSearcher.verifyDocId(userId1, generalQuery, true));

        assertTrue(VdsStreamingSearcher.verifyDocId(userId1, generalQuery, false));
        assertTrue(VdsStreamingSearcher.verifyDocId(userId2, generalQuery, false));
        assertTrue(VdsStreamingSearcher.verifyDocId(groupId1, generalQuery, false));
        assertTrue(VdsStreamingSearcher.verifyDocId(groupId2, generalQuery, false));
        assertTrue(VdsStreamingSearcher.verifyDocId(orderIdGroup1, generalQuery, false));
        assertTrue(VdsStreamingSearcher.verifyDocId(orderIdGroup2, generalQuery, false));
        assertTrue(VdsStreamingSearcher.verifyDocId(orderIdUser1, generalQuery, false));
        assertTrue(VdsStreamingSearcher.verifyDocId(orderIdUser2, generalQuery, false));
        assertFalse(VdsStreamingSearcher.verifyDocId(badId, generalQuery, false));

        assertTrue(VdsStreamingSearcher.verifyDocId(userId1, user1Query, false));
        assertFalse(VdsStreamingSearcher.verifyDocId(userId2, user1Query, false));
        assertFalse(VdsStreamingSearcher.verifyDocId(groupId1, user1Query, false));
        assertFalse(VdsStreamingSearcher.verifyDocId(groupId2, user1Query, false));
        assertFalse(VdsStreamingSearcher.verifyDocId(orderIdGroup1, user1Query, false));
        assertFalse(VdsStreamingSearcher.verifyDocId(orderIdGroup2, user1Query, false));
        assertTrue(VdsStreamingSearcher.verifyDocId(orderIdUser1, user1Query, false));
        assertFalse(VdsStreamingSearcher.verifyDocId(orderIdUser2, user1Query, false));
        assertFalse(VdsStreamingSearcher.verifyDocId(badId, user1Query, false));

        assertFalse(VdsStreamingSearcher.verifyDocId(userId1, group1Query, false));
        assertFalse(VdsStreamingSearcher.verifyDocId(userId2, group1Query, false));
        assertTrue(VdsStreamingSearcher.verifyDocId(groupId1, group1Query, false));
        assertFalse(VdsStreamingSearcher.verifyDocId(groupId2, group1Query, false));
        assertTrue(VdsStreamingSearcher.verifyDocId(orderIdGroup1, group1Query, false));
        assertFalse(VdsStreamingSearcher.verifyDocId(orderIdGroup2, group1Query, false));
        assertFalse(VdsStreamingSearcher.verifyDocId(orderIdUser1, group1Query, false));
        assertFalse(VdsStreamingSearcher.verifyDocId(orderIdUser2, group1Query, false));
        assertFalse(VdsStreamingSearcher.verifyDocId(badId, group1Query, false));
    }
}
