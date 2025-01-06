// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors;

import com.yahoo.data.access.Inspectable;
import com.yahoo.data.access.helpers.MatchFeatureData;
import com.yahoo.document.select.parser.TokenMgrException;
import com.yahoo.messagebus.Trace;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.prelude.fastsearch.ClusterParams;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.prelude.fastsearch.TimeoutException;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.Hit;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.searchlib.aggregation.Grouping;
import com.yahoo.vdslib.DocumentSummary;
import com.yahoo.vdslib.SearchResult;
import com.yahoo.vdslib.VisitorStatistics;
import com.yahoo.vespa.streamingvisitors.tracing.MockUtils;
import com.yahoo.vespa.streamingvisitors.tracing.MonotonicNanoClock;
import com.yahoo.vespa.streamingvisitors.tracing.SamplingStrategy;
import com.yahoo.vespa.streamingvisitors.tracing.TraceExporter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Ulf Carlin
 */
public class StreamingSearcherTestCase {

    public static final String USERDOC_ID_PREFIX = "id:namespace:mytype:n=1:userspecific";
    public static final String GROUPDOC_ID_PREFIX = "id:namespace:mytype:g=group1:userspecific";
    private static final ClusterParams CLUSTER_PARAMS = new ClusterParams("clusterName");

    private static class MockVisitor implements Visitor {
        private final Query query;
        final String searchCluster;
        final Route route;
        final String documentType;
        int totalHitCount;
        private final List<SearchResult.Hit> hits = new ArrayList<>();
        private final Map<String, DocumentSummary.Summary> summaryMap = new HashMap<>();
        private final List<Grouping> groupings = new ArrayList<>();
        int traceLevelOverride;

        MockVisitor(Query query, String searchCluster, Route route, String documentType, int traceLevelOverride) {
            this.query = query;
            this.searchCluster = searchCluster;
            this.route = route;
            this.documentType = documentType;
            this.traceLevelOverride = traceLevelOverride;
        }

        @Override
        public void doSearch() throws InterruptedException, ParseException, TimeoutException {
            String queryString = query.getModel().getQueryString();
            if (queryString.compareTo("parseexception") == 0) {
                throw new ParseException("Parsing failed");
            } else if (queryString.compareTo("tokenizeexception") == 0) {
                throw new TokenMgrException("Tokenization failed", 0);
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
            } else if (queryString.compareTo("match_features") == 0) {
                addResults(USERDOC_ID_PREFIX, 1, false);
                var matchFeatures = new MatchFeatureData(List.of("my_feature")).addHit();
                matchFeatures.set(0, 7.0);
                hits.get(0).setMatchFeatures(matchFeatures);
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

        @Override
        public Set<String> getErrors() { return Set.of(); }

        @Override
        public Trace getTrace() {
            return new Trace();
        }
    }

    private static class MockVisitorFactory implements VisitorFactory {

        public MockVisitor lastCreatedVisitor;

        @Override
        public Visitor createVisitor(Query query, String searchCluster, Route route, String documentType, int traceLevelOverride) {
            lastCreatedVisitor = new MockVisitor(query, searchCluster, route, documentType, traceLevelOverride);
            return lastCreatedVisitor;
        }
    }

    private static Result executeQuery(StreamingBackend searcher, Query query) {
        return searcher.doSearch2("test", query);
    }

    private static Query[] generateTestQueries(String queryString) {
        Query[] queries = new Query[4]; // Increase coverage
        for (int i = 0; i<queries.length; i++) {
            Query query = new Query(queryString);
            query.setTimeout(1000);
            if (i == 0) {
            } else if (i == 1) {
                query.getPresentation().setSummary("summary");
            } else if (i == 2) {
                query.getTrace().setLevel(100);
            } else if (i == 3) {
                query.getPresentation().setSummary("summary");
                query.getTrace().setLevel(100);
            }
            queries[i] = query;
        }
        return queries;
    }

    private static void checkError(StreamingBackend searcher, String queryString, String message, String detailedMessage) {
        for (Query query : generateTestQueries(queryString)) {
            Result result = executeQuery(searcher, query);
            assertNotNull(result.hits().getError());
            assertTrue(result.hits().getErrorHit().errors().iterator().next().getMessage().contains(message),
                    "Expected '" + message + "' to be contained in '"
                    + result.hits().getErrorHit().errors().iterator().next().getMessage() + "'");
            assertTrue(result.hits().getErrorHit().errors().iterator().next().getDetailedMessage().contains(detailedMessage),
                    "Expected '" + detailedMessage + "' to be contained in '"
                    + result.hits().getErrorHit().errors().iterator().next().getDetailedMessage() + "'");
        }
    }

    private static void checkSearch(StreamingBackend searcher, String queryString, int hitCount, String idPrefix) {
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

    private static void checkGrouping(StreamingBackend searcher, String queryString, int hitCount) {
        checkSearch(searcher, queryString, hitCount, null);
    }

    private static void checkMatchFeatures(StreamingBackend searcher) {
        String queryString = "/?streaming.selection=true&query=match_features";
        Result result = executeQuery(searcher, new Query(queryString));
        assertNull(result.hits().getError());
        assertEquals(result.hits().size(), 1);
        Hit hit = result.hits().get(0);
        var mf = hit.getField("matchfeatures");
        assertEquals(7.0, ((Inspectable) mf).inspect().field("my_feature").asDouble());
    }

    @Test
    void testBasics() {
        MockVisitorFactory factory = new MockVisitorFactory();
        var schema = new Schema.Builder("test")
                .add(new com.yahoo.search.schema.DocumentSummary.Builder("default").build())
                .add(new com.yahoo.search.schema.DocumentSummary.Builder("summary").build())
                .build();
        ClusterParams clusterParams = new ClusterParams("clusterName", "server.0", "default",
                new DocumentdbInfoConfig.Builder().documentdb(new DocumentdbInfoConfig.Documentdb.Builder().name("test")).build(),
                new SchemaInfo(List.of(schema), List.of()));
        StreamingBackend searcher = new StreamingBackend(clusterParams, "search-cluster-A", factory, "content-cluster-A");

        // Magic query values are used to trigger specific behaviors from mock visitor.
        checkError(searcher, "/?query=noselection",
                "Illegal query", "Streaming search requires either");
        checkError(searcher, "/?streaming.userid=1&query=parseexception",
                "Invalid query parameter", "Failed to parse document selection string");
        checkError(searcher, "/?streaming.userid=1&query=tokenizeexception",
                "Invalid query parameter", "Failed to tokenize document selection string");
        checkError(searcher, "/?streaming.userid=1&query=interruptedexception",
                "Backend communication error", "Interrupted");
        checkError(searcher, "/?streaming.userid=1&query=timeoutexception",
                "Timed out", "Timed out");
        checkError(searcher, "/?streaming.userid=1&query=nosummary",
                "Backend communication error", "Did not find summary for hit with document id");
        checkError(searcher, "/?streaming.userid=1&query=nosummarytofill",
                "Timed out", "Missing hit summary data for 1 hits");

        checkSearch(searcher, "/?streaming.userid=1&query=oneuserhit", 1, USERDOC_ID_PREFIX);
        checkSearch(searcher, "/?streaming.userid=1&query=oneuserhit&sorting=%2Bsurname", 1, USERDOC_ID_PREFIX);
        checkSearch(searcher, "/?streaming.selection=id.user%3D%3d1&query=twouserhits", 2, USERDOC_ID_PREFIX);
        checkSearch(searcher, "/?streaming.groupname=group1&query=twogrouphitsandoneuserhit", 2, GROUPDOC_ID_PREFIX);

        checkGrouping(searcher, "/?streaming.selection=true&query=onegroupinghit", 1);

        checkMatchFeatures(searcher);
    }

    @Test
    void testVerifyDocId() {
        Query generalQuery = new Query("/?streaming.selection=true&query=test");
        Query user1Query = new Query("/?streaming.userid=1&query=test");
        Query group1Query = new Query("/?streaming.groupname=group1&query=test");
        String userId1 = "id:namespace:mytype:n=1:userspecific";
        String userId2 = "id:namespace:mytype:n=2:userspecific";
        String groupId1 = "id:namespace:mytype:g=group1:userspecific";
        String groupId2 = "id:namespace:mytype:g=group2:userspecific";
        String badId = "unknowscheme:namespace:something";

        assertTrue(StreamingBackend.verifyDocId(userId1, generalQuery, true));

        assertTrue(StreamingBackend.verifyDocId(userId1, generalQuery, false));
        assertTrue(StreamingBackend.verifyDocId(userId2, generalQuery, false));
        assertTrue(StreamingBackend.verifyDocId(groupId1, generalQuery, false));
        assertTrue(StreamingBackend.verifyDocId(groupId2, generalQuery, false));
        assertFalse(StreamingBackend.verifyDocId(badId, generalQuery, false));

        assertTrue(StreamingBackend.verifyDocId(userId1, user1Query, false));
        assertFalse(StreamingBackend.verifyDocId(userId2, user1Query, false));
        assertFalse(StreamingBackend.verifyDocId(groupId1, user1Query, false));
        assertFalse(StreamingBackend.verifyDocId(groupId2, user1Query, false));
        assertFalse(StreamingBackend.verifyDocId(badId, user1Query, false));

        assertFalse(StreamingBackend.verifyDocId(userId1, group1Query, false));
        assertFalse(StreamingBackend.verifyDocId(userId2, group1Query, false));
        assertTrue(StreamingBackend.verifyDocId(groupId1, group1Query, false));
        assertFalse(StreamingBackend.verifyDocId(groupId2, group1Query, false));
        assertFalse(StreamingBackend.verifyDocId(badId, group1Query, false));
    }

    private static class TraceFixture {
        SamplingStrategy sampler = mock(SamplingStrategy.class);
        TraceExporter exporter = mock(TraceExporter.class);
        MonotonicNanoClock clock;
        TracingOptions options;

        MockVisitorFactory factory;
        StreamingBackend searcher;

        private TraceFixture(Long firstTimestamp, Long... additionalTimestamps) {
            clock = MockUtils.mockedClockReturning(firstTimestamp, additionalTimestamps);
            options = new TracingOptions(sampler, exporter, clock, 8, 2.0);
            factory = new MockVisitorFactory();
            searcher = new StreamingBackend(CLUSTER_PARAMS, "search-cluster-A", factory, "content-cluster-A", options);
        }

        private TraceFixture() {
            this(TimeUnit.SECONDS.toNanos(1), TimeUnit.SECONDS.toNanos(10));
        }

        static TraceFixture withSampledTrace(boolean shouldTrace) {
            var f = new TraceFixture();
            when(f.sampler.shouldSample()).thenReturn(shouldTrace);
            return f;
        }

        static TraceFixture withTracingAndClockSampledAt(long t1ms, long t2ms) {
            var f = new TraceFixture(TimeUnit.MILLISECONDS.toNanos(t1ms), TimeUnit.MILLISECONDS.toNanos(t2ms));
            when(f.sampler.shouldSample()).thenReturn(true);
            return f;
        }
    }

    @Test
    void trace_level_set_if_sampling_strategy_returns_true() {
        var f = TraceFixture.withSampledTrace(true);
        executeQuery(f.searcher, new Query("/?streaming.userid=1&query=timeoutexception"));

        assertNotNull(f.factory.lastCreatedVisitor);
        assertEquals(f.factory.lastCreatedVisitor.traceLevelOverride, 8);
    }

    @Test
    void trace_level_not_set_if_sampling_strategy_returns_false() {
        var f = TraceFixture.withSampledTrace(false);
        executeQuery(f.searcher, new Query("/?streaming.userid=1&query=timeoutexception"));

        assertNotNull(f.factory.lastCreatedVisitor);
        assertEquals(f.factory.lastCreatedVisitor.traceLevelOverride, 0);
    }

    @Test
    void trace_is_exported_if_timed_out_beyond_threshold() {
        // Default mock timeout threshold is 2x timeout
        var f = TraceFixture.withTracingAndClockSampledAt(1000, 3001);
        executeQuery(f.searcher, new Query("/?streaming.userid=1&query=timeoutexception&timeout=1.0"));

        verify(f.exporter, times(1)).maybeExport(any());
    }

    @Test
    void trace_is_not_exported_if_timed_out_less_than_threshold() {
        // Default mock timeout threshold is 2x timeout
        var f = TraceFixture.withTracingAndClockSampledAt(1000, 2999);
        executeQuery(f.searcher, new Query("/?streaming.userid=1&query=timeoutexception&timeout=1.0"));

        verify(f.exporter, times(0)).maybeExport(any());
    }

}
