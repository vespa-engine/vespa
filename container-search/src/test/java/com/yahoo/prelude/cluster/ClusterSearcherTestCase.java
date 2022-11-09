// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.cluster;

import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.concurrent.InThreadExecutorService;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.handler.ClustersStatus;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.config.ClusterConfig;
import com.yahoo.search.dispatch.Dispatcher;
import com.yahoo.search.dispatch.rpc.RpcResourcePool;
import com.yahoo.search.result.Hit;
import com.yahoo.search.schema.RankProfile;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.vespa.config.search.DispatchConfig;
import org.junit.jupiter.api.Test;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests cluster monitoring
 *
 * @author bratseth
 */
public class ClusterSearcherTestCase {

    private static final double DELTA = 0.0000000000000001;

    @Test
    void testNoBackends() {
        ClusterSearcher cluster = new ClusterSearcher(new LinkedHashSet<>(List.of("dummy")));
        try {
            Execution execution = new Execution(cluster, Execution.Context.createContextStub());
            Query query = new Query("query=hello");
            query.setHits(10);
            com.yahoo.search.Result result = execution.search(query);
            assertNotNull(result.hits().getError());
            assertEquals("No backends in service. Try later", result.hits().getError().getMessage());
        } finally {
            cluster.deconstruct();
        }
    }

    private IndexFacts createIndexFacts() {
        Map<String, List<String>> clusters = new LinkedHashMap<>();
        clusters.put("cluster1", List.of("type1", "type2", "type3"));
        clusters.put("cluster2", List.of("type4", "type5"));
        clusters.put("type1", List.of("type6"));
        Collection<SearchDefinition> searchDefs = List.of(
                new SearchDefinition("type1"),
                new SearchDefinition("type2"),
                new SearchDefinition("type3"),
                new SearchDefinition("type4"),
                new SearchDefinition("type5"),
                new SearchDefinition("type6"));
        return new IndexFacts(new IndexModel(clusters, searchDefs));
    }

    private Set<String> resolve(ClusterSearcher searcher, String query) {
        return searcher.resolveSchemas(new Query("?query=hello" + query), createIndexFacts());
    }

    @Test
    void testThatDocumentTypesAreResolved() {
        ClusterSearcher cluster1 = new ClusterSearcher(new LinkedHashSet<>(List.of("type1", "type2", "type3")));
        try {
            ClusterSearcher type1 = new ClusterSearcher(new LinkedHashSet<>(List.of("type6")));
            try {
                assertEquals(new LinkedHashSet<>(List.of("type1", "type2", "type3")), resolve(cluster1, ""));
                assertEquals(new LinkedHashSet<>(List.of("type6")), resolve(type1, ""));
                { // specify restrict
                    assertEquals(new LinkedHashSet<>(List.of("type1")), resolve(cluster1, "&restrict=type1"));
                    assertEquals(new LinkedHashSet<>(List.of("type2")), resolve(cluster1, "&restrict=type2"));
                    assertEquals(new LinkedHashSet<>(List.of("type2", "type3")), resolve(cluster1, "&restrict=type2,type3"));
                    assertEquals(new LinkedHashSet<>(List.of("type2")), resolve(cluster1, "&restrict=type2,type4"));
                    assertEquals(new LinkedHashSet<>(List.of()), resolve(cluster1, "&restrict=type4"));
                }
                { // specify sources
                    assertEquals(new LinkedHashSet<>(List.of("type1", "type2", "type3")), resolve(cluster1, "&sources=cluster1"));
                    assertEquals(new LinkedHashSet<>(List.of()), resolve(cluster1, "&sources=cluster2"));
                    assertEquals(new LinkedHashSet<>(List.of()), resolve(cluster1, "&sources=type1"));
                    assertEquals(new LinkedHashSet<>(List.of("type6")), resolve(type1, "&sources=type1"));
                    assertEquals(new LinkedHashSet<>(List.of("type2")), resolve(cluster1, "&sources=type2"));
                    assertEquals(new LinkedHashSet<>(List.of("type2", "type3")), resolve(cluster1, "&sources=type2,type3"));
                    assertEquals(new LinkedHashSet<>(List.of("type2")), resolve(cluster1, "&sources=type2,type4"));
                    assertEquals(new LinkedHashSet<>(List.of()), resolve(cluster1, "&sources=type4"));
                }
                { // specify both
                    assertEquals(new LinkedHashSet<>(List.of("type1")), resolve(cluster1, "&sources=cluster1&restrict=type1"));
                    assertEquals(new LinkedHashSet<>(List.of("type2")), resolve(cluster1, "&sources=cluster1&restrict=type2"));
                    assertEquals(new LinkedHashSet<>(List.of("type2", "type3")), resolve(cluster1, "&sources=cluster1&restrict=type2,type3"));
                    assertEquals(new LinkedHashSet<>(List.of("type2")), resolve(cluster1, "&sources=cluster2&restrict=type2"));
                }
            } finally {
                type1.deconstruct();
            }
        } finally {
            cluster1.deconstruct();
        }
    }

    @Test
    void testThatDocumentTypesAreResolvedTODO_REMOVE() {
        ClusterSearcher cluster1 = new ClusterSearcher(new LinkedHashSet<>(List.of("type1", "type2", "type3")));
        try {
            ClusterSearcher type1 = new ClusterSearcher(new LinkedHashSet<>(List.of("type6")));
            try {
                assertEquals(new LinkedHashSet<>(List.of()), resolve(cluster1, "&sources=cluster2"));
            } finally {
                type1.deconstruct();
            }
        } finally {
            cluster1.deconstruct();
        }
    }

    private static class MyMockSearcher extends VespaBackEndSearcher {

        private final String type1 = "type1";
        private final String type2 = "type2";
        private final String type3 = "type3";
        private final Map<String, List<Hit>> results = new LinkedHashMap<>();
        private final boolean expectAttributePrefetch;
        static final String ATTRIBUTE_PREFETCH = "attributeprefetch";

        private String getId(String type, int i) {
            return "id:ns:" + type + "::" + i;
        }

        private Hit createHit(String id, double relevancy) {
            return createHit(null, id, relevancy);
        }

        private Hit createHit(Query query, String id, double relevancy) {
            Hit hit = new FastHit();
            hit.setId(id);
            hit.setRelevance(relevancy);
            hit.setQuery(query);
            hit.setFillable();
            return hit;
        }

        private Hit createHit(Query query, Hit hit) {
            Hit retval = new FastHit();
            retval.setId(hit.getId());
            retval.setRelevance(hit.getRelevance());
            retval.setQuery(query);
            retval.setFillable();
            return retval;
        }

        private List<Hit> getHits(Query query) {
            Set<String> restrict = query.getModel().getRestrict();
            if (restrict.size() == 1) {
                return results.get(restrict.iterator().next());
            }
            return null;
        }

        private void init() {
            results.put(type1, List.of(createHit(getId(type1, 0), 9),
                                             createHit(getId(type1, 1), 6),
                                             createHit(getId(type1, 2), 3)));

            results.put(type2, List.of(createHit(getId(type2, 0), 10),
                                             createHit(getId(type2, 1), 7),
                                             createHit(getId(type2, 2), 4)));

            results.put(type3, List.of(createHit(getId(type3, 0), 11),
                                             createHit(getId(type3, 1), 8),
                                             createHit(getId(type3, 2), 5)));
        }

        MyMockSearcher(boolean expectAttributePrefetch) {
            this.expectAttributePrefetch = expectAttributePrefetch;
            init();
        }

        @Override
        protected com.yahoo.search.Result doSearch2(Query query, Execution execution) {
            return null; // search() is overriden, this should never be called
        }

        @Override
        public com.yahoo.search.Result search(Query query, Execution execution) {
            com.yahoo.search.Result result = new com.yahoo.search.Result(query);
            List<Hit> hits = getHits(query);
            if (hits != null) {
                if (result.getHitOrderer() == null) { // order by relevancy
                    for (int i = query.getOffset(); i < Math.min(hits.size(), query.getOffset() + query.getHits()); ++i) {
                        result.hits().add(createHit(query, hits.get(i)));
                    }
                } else { // order by ascending relevancy
                    for (int i = hits.size() - 1 + query.getOffset(); i >= 0; --i) {
                        result.hits().add(createHit(query, hits.get(i)));
                    }
                }
                result.setTotalHitCount(hits.size());
            } else if (query.getModel().getRestrict().isEmpty()) {
                result.hits().add(createHit(query, getId(type1, 3), 2));
                result.setTotalHitCount(1);
            }
            return result;
        }

        @Override
        protected void doPartialFill(com.yahoo.search.Result result, String summaryClass) {
            if (summaryClass.equals(ATTRIBUTE_PREFETCH) && !expectAttributePrefetch) {
                throw new IllegalArgumentException("Got summary class '" + ATTRIBUTE_PREFETCH + "' when not expected");
            }
            Set<String> restrictSet = new LinkedHashSet<>();
            for (Iterator<Hit> hits = result.hits().unorderedDeepIterator(); hits.hasNext(); ) {
                Hit hit = hits.next();
                restrictSet.addAll(hit.getQuery().getModel().getRestrict());
            }
            if (restrictSet.size() != 1) {
                throw new IllegalArgumentException("Expected 1 doctype, got " + restrictSet.size() + ": " + Arrays.toString(restrictSet.toArray()));
            }
            // Generate summary content
            for (Iterator<Hit> hits = result.hits().unorderedDeepIterator(); hits.hasNext(); ) {
                Hit hit = hits.next();
                if (summaryClass.equals(ATTRIBUTE_PREFETCH)) {
                    hit.setField("asc-score", hit.getRelevance().getScore());
                } else {
                    hit.setField("score", "score: " + hit.getRelevance().getScore());
                }
                hit.setFilled(summaryClass);
            }
        }
    }

    private Execution createExecution() {
        return createExecution(List.of("type1", "type2", "type3"), false);
    }

    private Execution createExecution(boolean expectAttributePrefetch) {
        return createExecution(List.of("type1", "type2", "type3"), expectAttributePrefetch);
    }

    private Execution createExecution(List<String> docTypesList, boolean expectAttributePrefetch) {
        Set<String> documentTypes = new LinkedHashSet<>(docTypesList);
        ClusterSearcher cluster = new ClusterSearcher(documentTypes,
                                                      new MyMockSearcher(expectAttributePrefetch),
                                                      new InThreadExecutorService());
        try {
            List<Schema> schemas = new ArrayList<>();
            for (String docType : docTypesList) {
                var schemaBuilder = new Schema.Builder(docType);
                schemaBuilder.add(new RankProfile.Builder("default").build());
                if (docType.equals("type1"))
                    schemaBuilder.add(new RankProfile.Builder("testprofile").build());
                schemas.add(schemaBuilder.build());
            }
            return new Execution(cluster, Execution.Context.createContextStub(new SchemaInfo(schemas, Map.of())));
        } finally {
            cluster.deconstruct();
        }
    }

    @Test
    void testThatSingleDocumentTypeCanBeSearched() {
        { // Explicit 1 type in restrict set
            Execution execution = createExecution();
            Query query = new Query("?query=hello&restrict=type1");
            com.yahoo.search.Result result = execution.search(query);
            assertEquals(3, result.getTotalHitCount());
            List<Hit> hits = result.hits().asList();
            assertEquals(3, hits.size());
            assertEquals(9.0, hits.get(0).getRelevance().getScore(), DELTA);
            assertEquals(6.0, hits.get(1).getRelevance().getScore(), DELTA);
            assertEquals(3.0, hits.get(2).getRelevance().getScore(), DELTA);
        }
        { // Only 1 registered type in cluster searcher, empty restrict set
            // NB ! Empty restrict sets does not exist below the cluster searcher.
            // restrict set is set by cluster searcher to tell which documentdb is used.
            // Modify test to mirror that change.
            Execution execution = createExecution(List.of("type1"), false);
            Query query = new Query("?query=hello");
            com.yahoo.search.Result result = execution.search(query);
            assertEquals(3, result.getTotalHitCount());
            List<Hit> hits = result.hits().asList();
            assertEquals(3, hits.size());
            assertEquals(9.0, hits.get(0).getRelevance().getScore(), DELTA);
        }
    }

    @Test
    void testThatSubsetOfDocumentTypesCanBeSearched() {
        Execution execution = createExecution();
        Query query = new Query("?query=hello&restrict=type1,type3");

        com.yahoo.search.Result result = execution.search(query);
        assertEquals(6, result.getTotalHitCount());
        List<Hit> hits = result.hits().asList();
        assertEquals(6, hits.size());
        assertEquals(11.0, hits.get(0).getRelevance().getScore(), DELTA);
        assertEquals(9.0,  hits.get(1).getRelevance().getScore(), DELTA);
        assertEquals(8.0,  hits.get(2).getRelevance().getScore(), DELTA);
        assertEquals(6.0,  hits.get(3).getRelevance().getScore(), DELTA);
        assertEquals(5.0,  hits.get(4).getRelevance().getScore(), DELTA);
        assertEquals(3.0,  hits.get(5).getRelevance().getScore(), DELTA);
    }

    @Test
    void testThatMultipleDocumentTypesCanBeSearchedAndFilled() {
        Execution execution = createExecution();
        Query query = new Query("?query=hello");

        com.yahoo.search.Result result = execution.search(query);
        assertEquals(9, result.getTotalHitCount());
        List<Hit> hits = result.hits().asList();
        assertEquals(9, hits.size());
        assertEquals(11.0, hits.get(0).getRelevance().getScore(), DELTA);
        assertEquals(10.0, hits.get(1).getRelevance().getScore(), DELTA);
        assertEquals(9.0,  hits.get(2).getRelevance().getScore(), DELTA);
        assertEquals(8.0,  hits.get(3).getRelevance().getScore(), DELTA);
        assertEquals(7.0,  hits.get(4).getRelevance().getScore(), DELTA);
        assertEquals(6.0,  hits.get(5).getRelevance().getScore(), DELTA);
        assertEquals(5.0,  hits.get(6).getRelevance().getScore(), DELTA);
        assertEquals(4.0,  hits.get(7).getRelevance().getScore(), DELTA);
        assertEquals(3.0,  hits.get(8).getRelevance().getScore(), DELTA);
        for (int i = 0; i < 9; ++i) {
            assertNull(hits.get(i).getField("score"));
        }

        execution.fill(result, "summary");

        hits = result.hits().asList();
        assertEquals("score: 11.0", hits.get(0).getField("score"));
        assertEquals("score: 10.0", hits.get(1).getField("score"));
        assertEquals("score: 9.0",  hits.get(2).getField("score"));
        assertEquals("score: 8.0",  hits.get(3).getField("score"));
        assertEquals("score: 7.0",  hits.get(4).getField("score"));
        assertEquals("score: 6.0",  hits.get(5).getField("score"));
        assertEquals("score: 5.0",  hits.get(6).getField("score"));
        assertEquals("score: 4.0",  hits.get(7).getField("score"));
        assertEquals("score: 3.0",  hits.get(8).getField("score"));
    }

    private com.yahoo.search.Result getResult(int offset, int hits, Execution execution) {
        Result result = getResult(offset, hits, null, execution);
        assertEquals(0, result.getQuery().getOffset());
        return result;
    }

    private com.yahoo.search.Result getResult(int offset, int hits, String extra, Execution execution) {
        Query query = new Query("?query=hello" + (extra != null ? (extra) : ""));
        query.setOffset(offset);
        query.setHits(hits);
        return execution.search(query);
    }

    private void assertResult(int totalHitCount, List<Double> expHits, com.yahoo.search.Result result) {
        assertEquals(totalHitCount, result.getTotalHitCount());
        List<Hit> hits = result.hits().asList();
        assertEquals(expHits.size(), hits.size());
        for (int i = 0; i < expHits.size(); ++i) {
            assertEquals(expHits.get(i),  hits.get(i).getRelevance().getScore(), 0.0000001);
        }
    }

    @Test
    void testThatWeCanSpecifyNumHitsAndHitOffset() {
        Execution ex = createExecution();

        // all types
        assertResult(9, List.of(11.0, 10.0), getResult(0, 2, ex));
        assertResult(9, List.of(10.0, 9.0),  getResult(1, 2, ex));
        assertResult(9, List.of(9.0, 8.0),   getResult(2, 2, ex));
        assertResult(9, List.of(8.0, 7.0),   getResult(3, 2, ex));
        assertResult(9, List.of(7.0, 6.0),   getResult(4, 2, ex));
        assertResult(9, List.of(6.0, 5.0),   getResult(5, 2, ex));
        assertResult(9, List.of(5.0, 4.0),   getResult(6, 2, ex));
        assertResult(9, List.of(4.0, 3.0),   getResult(7, 2, ex));
        assertResult(9, List.of(3.0),        getResult(8, 2, ex));
        assertResult(9, List.of(), getResult(9, 2, ex));
        assertResult(9, List.of(11.0, 10.0, 9.0, 8.0, 7.0), getResult(0, 5, ex));
        assertResult(9, List.of(6.0, 5.0, 4.0, 3.0),        getResult(5, 5, ex));

        // restrict=type1
        assertResult(3, List.of(9.0, 6.0), getResult(0, 2, "&restrict=type1", ex));
        assertResult(3, List.of(6.0, 3.0), getResult(1, 2, "&restrict=type1", ex));
        assertResult(3, List.of(3.0),      getResult(2, 2, "&restrict=type1", ex));
        assertResult(3, List.of(), getResult(3, 2, "&restrict=type1", ex));
    }

    @Test
    void testThatWeCanSpecifyNumHitsAndHitOffsetWhenSorting() {
        Execution ex = createExecution(true);

        String extra = "&restrict=type1,type2&sorting=%2Basc-score";
        com.yahoo.search.Result result = getResult(0, 2, extra, ex);
        assertEquals(3.0, result.hits().asList().get(0).getField("asc-score"));
        assertEquals(4.0, result.hits().asList().get(1).getField("asc-score"));
        assertResult(6, List.of(3.0, 4.0),  getResult(0, 2, extra, ex));
        assertResult(6, List.of(4.0, 6.0),  getResult(1, 2, extra, ex));
        assertResult(6, List.of(6.0, 7.0),  getResult(2, 2, extra, ex));
        assertResult(6, List.of(7.0, 9.0),  getResult(3, 2, extra, ex));
        assertResult(6, List.of(9.0, 10.0), getResult(4, 2, extra, ex));
        assertResult(6, List.of(10.0),      getResult(5, 2, extra, ex));
        assertResult(6, List.of(),  getResult(6, 2, extra, ex));
    }

    private static ClusterSearcher createSearcher(String clusterName, Double maxQueryTimeout, Double maxQueryCacheTimeout,
                                                  boolean streamingMode, VipStatus vipStatus) {
        QrSearchersConfig.Builder qrSearchersConfig = new QrSearchersConfig.Builder();
        QrSearchersConfig.Searchcluster.Builder searchClusterConfig = new QrSearchersConfig.Searchcluster.Builder();
        searchClusterConfig.name(clusterName);
        if (streamingMode) {
            searchClusterConfig.indexingmode(QrSearchersConfig.Searchcluster.Indexingmode.Enum.STREAMING);
            searchClusterConfig.searchdef("streaming_sd");
        }
        qrSearchersConfig.searchcluster(searchClusterConfig);

        ClusterConfig.Builder clusterConfig = new ClusterConfig.Builder().clusterName(clusterName);
        if (maxQueryTimeout != null)
            clusterConfig.maxQueryTimeout(maxQueryTimeout);
        if (maxQueryCacheTimeout != null)
            clusterConfig.maxQueryCacheTimeout(maxQueryCacheTimeout);

        DocumentdbInfoConfig.Builder documentDbConfig = new DocumentdbInfoConfig.Builder();
        documentDbConfig.documentdb(new DocumentdbInfoConfig.Documentdb.Builder().name("type1"));
        var schema = new Schema.Builder("type1");

        DispatchConfig dispatchConfig = new DispatchConfig.Builder().build();
        Dispatcher dispatcher = new Dispatcher(new RpcResourcePool(dispatchConfig),
                                               ComponentId.createAnonymousComponentId("test-id"),
                                               dispatchConfig,
                                               vipStatus);
        ComponentRegistry<Dispatcher> dispatchers = new ComponentRegistry<>();
        dispatchers.register(new ComponentId("dispatcher." + clusterName), dispatcher);

        return new ClusterSearcher(new ComponentId("test-id"),
                                   new InThreadExecutorService(),
                                   qrSearchersConfig.build(),
                                   clusterConfig.build(),
                                   documentDbConfig.build(),
                                   new SchemaInfo(List.of(schema.build()), Map.of()),
                                   dispatchers,
                                   vipStatus,
                                   null);
    }

    private static class QueryTimeoutFixture {
        ClusterSearcher searcher;
        Execution exec;
        Query query;
        QueryTimeoutFixture(Double maxQueryTimeout, Double maxQueryCacheTimeout) {
            String clusterName = "test-cluster";
            VipStatus vipStatus = new VipStatus(new QrSearchersConfig.Builder().searchcluster(new QrSearchersConfig.Searchcluster.Builder().name(clusterName)).build(), new ClustersStatus());
            searcher = createSearcher(clusterName, maxQueryTimeout, maxQueryCacheTimeout, false, vipStatus);
            exec = new Execution(searcher, Execution.Context.createContextStub());
            query = new Query("?query=hello&restrict=type1");
        }
        void search() {
            searcher.search(query, exec);
        }
    }

    @Test
    void testThatVipStatusIsSetUpForStreamingSearch() {
        String clusterName = "test-cluster";
        VipStatus vipStatus = new VipStatus(new QrSearchersConfig.Builder().searchcluster(new QrSearchersConfig.Searchcluster.Builder().name(clusterName)).build(), new ClustersStatus());
        assertFalse(vipStatus.isInRotation());
        ClusterSearcher searcher = createSearcher(clusterName, 1.0, 10.0, true, vipStatus);
        assertTrue(vipStatus.isInRotation());
    }

    @Test
    void testThatQueryTimeoutIsCappedWithDefaultMax() {
        QueryTimeoutFixture f = new QueryTimeoutFixture(null, null);
        f.query.setTimeout(600001);
        f.search();
        assertEquals(600000, f.query.getTimeout());
    }

    @Test
    void testThatQueryTimeoutIsNotCapped() {
        QueryTimeoutFixture f = new QueryTimeoutFixture(null, null);
        f.query.setTimeout(599999);
        f.search();
        assertEquals(599999, f.query.getTimeout());
    }

    @Test
    void testThatQueryTimeoutIsCappedWithSpecifiedMax() {
        QueryTimeoutFixture f = new QueryTimeoutFixture(70.0, null);
        f.query.setTimeout(70001);
        f.search();
        assertEquals(70000, f.query.getTimeout());
    }

    @Test
    void testThatQueryCacheIsDisabledIfTimeoutIsLargerThanMax() {
        QueryTimeoutFixture f = new QueryTimeoutFixture(null, null);
        f.query.setTimeout(10001);
        f.query.getRanking().setQueryCache(true);
        f.search();
        assertFalse(f.query.getRanking().getQueryCache());
    }

    @Test
    void testThatQueryCacheIsNotDisabledIfTimeoutIsOk() {
        QueryTimeoutFixture f = new QueryTimeoutFixture(null, null);
        f.query.setTimeout(10000);
        f.query.getRanking().setQueryCache(true);
        f.search();
        assertTrue(f.query.getRanking().getQueryCache());
    }

    @Test
    void testThatQueryCacheIsDisabledIfTimeoutIsLargerThanConfiguredMax() {
        QueryTimeoutFixture f = new QueryTimeoutFixture(null, 5.0);
        f.query.setTimeout(5001);
        f.query.getRanking().setQueryCache(true);
        f.search();
        assertFalse(f.query.getRanking().getQueryCache());
    }

}
