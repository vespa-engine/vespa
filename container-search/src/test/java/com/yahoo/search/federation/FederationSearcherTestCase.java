// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.federation.sourceref.SearchChainResolver;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.schema.Cluster;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.SearchChain;
import com.yahoo.search.searchchain.SearchChainRegistry;
import com.yahoo.search.searchchain.model.federation.FederationOptions;
import com.yahoo.search.test.QueryTestCase;
import com.yahoo.search.yql.MinimalQueryInserter;
import com.yahoo.yolean.trace.TraceNode;
import com.yahoo.yolean.trace.TraceVisitor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for federation searcher. The searcher is also tested in
 * com.yahoo.prelude.searcher.test.BlendingSearcherTestCase.
 *
 * @author Arne Bergene Fossaa
 */
public class FederationSearcherTestCase {

    static final String SOURCE1 = "source1";
    static final String SOURCE2 = "source2";

    public static class TwoSourceChecker extends TraceVisitor {
        public boolean traceFromSource1 = false;
        public boolean traceFromSource2 = false;

        @Override
        public void visit(TraceNode node) {
            if (SOURCE1.equals(node.payload())) {
                traceFromSource1 = true;
            } else if (SOURCE2.equals(node.payload())) {
                traceFromSource2 = true;
            }
        }

    }

    private FederationConfig.Builder builder;
    private SearchChainRegistry chainRegistry;

    @BeforeEach
    public void setUp() throws Exception {
        builder = new FederationConfig.Builder();
        chainRegistry = new SearchChainRegistry();
    }

    @AfterEach
    public void tearDown() {
        builder = null;
        chainRegistry = null;
    }

    private void addChained(Searcher searcher, String sourceName, String ... documentTypes) {
        builder.target(new FederationConfig.Target.Builder().
                id(sourceName).
                searchChain(new FederationConfig.Target.SearchChain.Builder().
                        searchChainId(sourceName).
                        timeoutMillis(10000).
                        useByDefault(true).
                        documentTypes(List.of(documentTypes)))
        );
        chainRegistry.register(new ComponentId(sourceName),
                createSearchChain(new ComponentId(sourceName), searcher));
    }

    private Searcher createFederationSearcher() {
        return createFederationSearcher(SchemaInfo.empty());
    }

    private Searcher createFederationSearcher(SchemaInfo schemaInfo) {
        return new FederationSearcher(new FederationConfig(builder), schemaInfo, new ComponentRegistry<>());
    }

    private SearchChain createSearchChain(ComponentId chainId,Searcher searcher) {
        return new SearchChain(chainId, searcher);
    }

    @Test
    void testQueryProfileNestedReferencing() {
        addChained(new MockSearcher(), "mySource1");
        addChained(new MockSearcher(), "mySource2");
        Chain<Searcher> mainChain = new Chain<>("default", createFederationSearcher());

        QueryProfile defaultProfile = new QueryProfile("default");
        defaultProfile.set("source.mySource1.hits", "%{hits}", null);
        defaultProfile.freeze();
        Query q = new Query(QueryTestCase.httpEncode("?query=test"), defaultProfile.compile(null));

        Result result = new Execution(mainChain, Execution.Context.createContextStub(chainRegistry)).search(q);
        assertNull(result.hits().getError());
        assertEquals("source:mySource1", result.hits().get(0).getId().stringValue());
        assertEquals("source:mySource2", result.hits().get(1).getId().stringValue());
    }

    @Test
    void testTraceTwoSources() {
        Chain<Searcher> mainChain = twoTracingSources();

        Query q = new Query(com.yahoo.search.test.QueryTestCase.httpEncode("?query=test&traceLevel=1"));

        Execution execution = new Execution(mainChain, Execution.Context.createContextStub(chainRegistry));
        Result result = execution.search(q);
        assertNull(result.hits().getError());
        TwoSourceChecker lookForTraces = new TwoSourceChecker();
        execution.trace().accept(lookForTraces);
        assertTrue(lookForTraces.traceFromSource1);
        assertTrue(lookForTraces.traceFromSource2);
    }

    private Chain<Searcher> twoTracingSources() {
        addChained(new Searcher() {
            @Override
            public Result search(Query query, Execution execution) {
                query.trace(SOURCE1, 1);
                return execution.search(query);
            }

        }, SOURCE1);

        addChained(new Searcher() {
            @Override
            public Result search(Query query, Execution execution) {
                query.trace(SOURCE2, 1);
                return execution.search(query);
            }

        }, SOURCE2);

        return new Chain<>("default",
                           new FederationSearcher(new FederationConfig(builder), SchemaInfo.empty(), new ComponentRegistry<>()));
    }

    @Test
    void testTraceOneSourceNoCloning() {
        Chain<Searcher> mainChain = twoTracingSources();

        Query q = new Query(com.yahoo.search.test.QueryTestCase.httpEncode("?query=test&traceLevel=1&sources=source1"));

        Execution execution = new Execution(mainChain, Execution.Context.createContextStub(chainRegistry));
        Result result = execution.search(q);
        assertNull(result.hits().getError());
        TwoSourceChecker lookForTraces = new TwoSourceChecker();
        execution.trace().accept(lookForTraces);
        assertTrue(lookForTraces.traceFromSource1);
        assertFalse(lookForTraces.traceFromSource2);
    }

    @Test
    void testTraceOneSourceWithCloning() {
        Chain<Searcher> mainChain = twoTracingSources();

        Query q = new Query(com.yahoo.search.test.QueryTestCase.httpEncode("?query=test&traceLevel=1&sources=source1"));

        Execution execution = new Execution(mainChain, Execution.Context.createContextStub(chainRegistry));
        Result result = execution.search(q);
        assertNull(result.hits().getError());
        TwoSourceChecker lookForTraces = new TwoSourceChecker();
        execution.trace().accept(lookForTraces);
        assertTrue(lookForTraces.traceFromSource1);
        assertFalse(lookForTraces.traceFromSource2);

    }

    @Test
    void testPropertyPropagation() {
        Result result = searchWithPropertyPropagation();

        assertEquals("source:mySource1", result.hits().get(0).getId().stringValue());
        assertEquals("source:mySource2", result.hits().get(1).getId().stringValue());
        assertEquals("nalle", result.hits().get(0).getQuery().getPresentation().getSummary());
        assertEquals("foo", result.hits().get(0).getQuery().properties().get("customSourceProperty"));
        assertNull(result.hits().get(1).getQuery().properties().get("customSourceProperty"));
        assertNull(result.hits().get(0).getQuery().properties().get("custom.source.property"));
        assertEquals("bar", result.hits().get(1).getQuery().properties().get("custom.source.property"));
        assertEquals(13, result.hits().get(0).getQuery().properties().get("hits"));
        assertEquals(1, result.hits().get(0).getQuery().properties().get("offset"));
        assertEquals(10, result.hits().get(1).getQuery().properties().get("hits"));
        assertEquals(0, result.hits().get(1).getQuery().properties().get("offset"));

        assertNull(result.hits().get(1).getQuery().getPresentation().getSummary());
    }

    private Result searchWithPropertyPropagation() {
        addChained(new MockSearcher(), "mySource1");
        addChained(new MockSearcher(), "mySource2");
        Chain<Searcher> mainChain = new Chain<>("default", createFederationSearcher());

        Query q = new Query(QueryTestCase.httpEncode("?query=test&source.mySource1.presentation.summary=nalle&source.mySource1.customSourceProperty=foo&source.mySource2.custom.source.property=bar&source.mySource1.hits=13&source.mySource1.offset=1"));

        Result result = new Execution(mainChain, Execution.Context.createContextStub(chainRegistry)).search(q);
        assertNull(result.hits().getError());
        return result;
    }

    @Test
    void testTopLevelHitGroupFieldPropagation() {
        addChained(new MockSearcher(), "mySource1");
        addChained(new AnotherMockSearcher(), "mySource2");
        Chain<Searcher> mainChain = new Chain<>("default", createFederationSearcher());

        Query q = new Query("?query=test");

        Result result = new Execution(mainChain, Execution.Context.createContextStub(chainRegistry)).search(q);
        assertNull(result.hits().getError());
        assertEquals("source:mySource1", result.hits().get(0).getId().stringValue());
        assertEquals("source:mySource2", result.hits().get(1).getId().stringValue());
        assertEquals(
                AnotherMockSearcher.IS_THIS_PROPAGATED,
                result.hits().get(1).getField(AnotherMockSearcher.PROPAGATION_KEY));
    }

    private Result searchWithTwoSources(String queryString) {
        addChained(new MockSearcher(), "mySource1");
        addChained(new MockSearcher(), "mySource2");
        Chain<Searcher> mainChain = new Chain<>("default", createFederationSearcher());
        Query query = new Query(QueryTestCase.httpEncode(queryString));
        return new Execution(mainChain, Execution.Context.createContextStub(chainRegistry)).search(query);
    }

    private static List<String> sourceGroupIds(Result result) {
        List<String> ids = new ArrayList<>();
        for (Hit hit : result.hits()) {
            if (hit.getId().stringValue().startsWith("source:")) {
                ids.add(hit.getId().stringValue());
            }
        }
        return ids;
    }

    @Test
    void testExcludedSourceIsNotSearched() {
        Result result = searchWithTwoSources("?query=test&model.sources=-mySource1");
        assertNull(result.hits().getError());
        assertEquals(List.of("source:mySource2"), sourceGroupIds(result));
    }

    @Test
    void testExclusionWinsOverSelection() {
        Result result = searchWithTwoSources("?query=test&model.sources=mySource1,mySource2,-mySource1");
        assertNull(result.hits().getError());
        assertEquals(List.of("source:mySource2"), sourceGroupIds(result));
    }

    @Test
    void testExcludingAllSelectedSourcesGivesEmptyResultWithoutError() {
        Result result = searchWithTwoSources("?query=test&model.sources=mySource1,-mySource1");
        assertNull(result.hits().getError());
        assertEquals(List.of(), sourceGroupIds(result));
    }

    @Test
    void testExcludingUnknownSourceGivesErrorButSearchesTheRest() {
        Result result = searchWithTwoSources("?query=test&model.sources=-nonexistent");
        ErrorMessage error = result.hits().getError();
        assertNotNull(error);
        assertTrue(error.getDetailedMessage().startsWith("Could not resolve source ref '-nonexistent'."),
                   error.getDetailedMessage());
        assertEquals(List.of("source:mySource1", "source:mySource2"), sourceGroupIds(result));
    }

    @Test
    void testExcludedSourceSurvivesYqlSelectFromAllSources() {
        addChained(new MockSearcher(), "mySource1");
        addChained(new MockSearcher(), "mySource2");
        Chain<Searcher> mainChain = new Chain<>("default", new MinimalQueryInserter(), createFederationSearcher());
        Query query = new Query(QueryTestCase.httpEncode("?yql=select * from sources * where title contains \"test\"&model.sources=-mySource1"));

        Result result = new Execution(mainChain, Execution.Context.createContextStub(chainRegistry)).search(query);
        assertNull(result.hits().getError());
        assertTrue(query.getModel().getSources().isEmpty());
        assertEquals(Set.of("mySource1"), query.getModel().getExcludedSources());
        assertEquals(List.of("source:mySource2"), sourceGroupIds(result));
    }

    @Test
    void testSourceNamedInYqlOverridesItsExclusion() {
        addChained(new MockSearcher(), "mySource1");
        addChained(new MockSearcher(), "mySource2");
        Chain<Searcher> mainChain = new Chain<>("default", new MinimalQueryInserter(), createFederationSearcher());
        Execution.Context context = Execution.Context.createContextStub(chainRegistry);

        Result result = new Execution(mainChain, context).search(new Query(QueryTestCase.httpEncode(
                "?yql=select * from sources mySource1 where title contains \"test\"&model.sources=-mySource1")));
        assertNull(result.hits().getError());
        assertEquals(List.of("source:mySource1"), sourceGroupIds(result));

        // Selecting one source in YQL does not lift the exclusion of another
        result = new Execution(mainChain, context).search(new Query(QueryTestCase.httpEncode(
                "?yql=select * from sources mySource1 where title contains \"test\"&model.sources=mySource2,-mySource2")));
        assertNull(result.hits().getError());
        assertEquals(List.of("source:mySource1"), sourceGroupIds(result));
    }

    @Test
    void testClusterSchemaSourceNamedInYqlOverridesClusterExclusion() {
        RecordingSearcher recorder = new RecordingSearcher();
        addChained(recorder, "cluster1", "s1", "s2");
        Chain<Searcher> mainChain = new Chain<>("default", new MinimalQueryInserter(), createFederationSearcher());
        Execution.Context context = Execution.Context.createContextStub(chainRegistry);

        new Execution(mainChain, context).search(new Query(QueryTestCase.httpEncode(
                "?yql=select * from sources cluster1.s1 where title contains \"test\"&model.sources=-cluster1")));
        assertEquals(List.of(Set.of("s1")), recorder.restricts);

        // The same selection through model.sources does not override the exclusion
        recorder.restricts.clear();
        new Execution(mainChain, context).search(new Query(QueryTestCase.httpEncode(
                "?yql=select * from sources * where title contains \"test\"&model.sources=cluster1.s1,-cluster1")));
        assertEquals(List.of(), recorder.restricts);

        // Naming the cluster in YQL does not override the exclusion of one schema within it:
        // The cluster is searched, and the cluster searcher removes the schema
        recorder.restricts.clear();
        recorder.excludedSources.clear();
        new Execution(mainChain, context).search(new Query(QueryTestCase.httpEncode(
                "?yql=select * from sources cluster1 where title contains \"test\"&model.sources=-cluster1.s1")));
        assertEquals(List.of(Set.of()), recorder.restricts);
        assertEquals(List.of(Set.of("cluster1.s1")), recorder.excludedSources);
    }

    @Test
    void testExcludingAllSchemasOfClusterPrunesIt() {
        addChained(new MockSearcher(), "cluster1", "child");
        addChained(new MockSearcher(), "cluster2", "parent", "child");
        SchemaInfo schemaInfo = new SchemaInfo(List.of(new Schema.Builder("parent").build(), new Schema.Builder("child").build()),
                                               List.of(new Cluster.Builder("cluster1").addSchema("child").build(),
                                                       new Cluster.Builder("cluster2").addSchema("parent").addSchema("child").build()));
        Chain<Searcher> mainChain = new Chain<>("default", createFederationSearcher(schemaInfo));
        Execution.Context context = Execution.Context.createContextStub(chainRegistry);

        Result result = new Execution(mainChain, context).search(new Query(QueryTestCase.httpEncode("?query=test&model.sources=-child")));
        assertNull(result.hits().getError());
        assertEquals(List.of("source:cluster2"), sourceGroupIds(result));

        result = new Execution(mainChain, context).search(new Query(QueryTestCase.httpEncode("?query=test&model.sources=-child,-parent")));
        assertNull(result.hits().getError());
        assertEquals(List.of(), sourceGroupIds(result));

        // Excluding a schema does not exclude clusters which also have other schemas
        result = new Execution(mainChain, context).search(new Query(QueryTestCase.httpEncode("?query=test&model.sources=-parent")));
        assertNull(result.hits().getError());
        assertEquals(List.of("source:cluster1", "source:cluster2"), sourceGroupIds(result));
    }

    @Test
    void testExcludingClusterExcludesItsClusterSchemaTargets() {
        RecordingSearcher recorder = new RecordingSearcher();
        addChained(recorder, "cluster1", "s1", "s2");
        Chain<Searcher> mainChain = new Chain<>("default", createFederationSearcher());
        Execution.Context context = Execution.Context.createContextStub(chainRegistry);

        new Execution(mainChain, context).search(new Query(QueryTestCase.httpEncode("?query=test&model.sources=cluster1.s1,-cluster1")));
        assertEquals(List.of(), recorder.restricts);

        // Excluding a cluster.schema does not exclude the cluster itself: The cluster searcher removes the schema
        new Execution(mainChain, context).search(new Query(QueryTestCase.httpEncode("?query=test&model.sources=cluster1,-cluster1.s1")));
        assertEquals(List.of(Set.of()), recorder.restricts);

        // ... unless all its schemas are excluded
        recorder.restricts.clear();
        new Execution(mainChain, context).search(new Query(QueryTestCase.httpEncode("?query=test&model.sources=cluster1,-cluster1.s1,-s2")));
        assertEquals(List.of(), recorder.restricts);

        recorder.restricts.clear();
        new Execution(mainChain, context).search(new Query(QueryTestCase.httpEncode("?query=test&model.sources=cluster1.s1,cluster1.s2,-cluster1.s1")));
        assertEquals(List.of(Set.of("s2")), recorder.restricts);
    }

    private static class RecordingSearcher extends Searcher {

        final List<Set<String>> restricts = new ArrayList<>();
        final List<Set<String>> excludedSources = new ArrayList<>();

        @Override
        public Result search(Query query, Execution execution) {
            restricts.add(Set.copyOf(query.getModel().getRestrict()));
            excludedSources.add(Set.copyOf(query.getModel().getExcludedSources()));
            return new Result(query);
        }

    }

    private static class MockSearcher extends Searcher {

        @Override
        public Result search(Query query, Execution execution) {
            String sourceName = query.properties().getString("sourceName", "unknown");
            Result result = new Result(query);
            for (int i = 1; i <= query.getHits(); i++) {
                Hit hit = new Hit(sourceName + ":" + i, 1d / i);
                hit.setSource(sourceName);
                result.hits().add(hit);
            }
            return result;
        }

    }

    private static class AnotherMockSearcher extends Searcher {

        private static final String PROPAGATION_KEY = "hello";
        private static final String IS_THIS_PROPAGATED = "is this propagated?";

        @Override
        public Result search(Query query, Execution execution) {
            final Result result = new Result(query);
            result.hits().setField(PROPAGATION_KEY, IS_THIS_PROPAGATED);
            return result;
        }
    }

    @Test
    void testProviderSelectionFromQueryProperties() {
        SearchChainRegistry registry = new SearchChainRegistry();
        registry.register(new Chain<>("provider1", new MockProvider("provider1")));
        registry.register(new Chain<>("provider2", new MockProvider("provider2")));
        registry.register(new Chain<>("default", createMultiProviderFederationSearcher()));
        assertSelects("provider1", registry);
        assertSelects("provider2", registry);
    }

    private void assertSelects(String providerName, SearchChainRegistry registry) {
        QueryProfile profile = new QueryProfile("test");
        profile.set("source.news.provider", providerName, null);
        Query query = new Query(QueryTestCase.httpEncode("?query=test&model.sources=news"), profile.compile(null));
        Result result = new Execution(registry.getComponent("default"), Execution.Context.createContextStub(registry)).search(query);
        assertEquals(1, result.hits().size());
        assertNotNull(result.hits().get(providerName + ":1"));
    }

    private FederationSearcher createMultiProviderFederationSearcher() {
        FederationOptions options = new FederationOptions();
        SearchChainResolver.Builder builder = new SearchChainResolver.Builder();

        ComponentId provider1 = new ComponentId("provider1");
        ComponentId provider2 = new ComponentId("provider2");
        ComponentId news = new ComponentId("news");
        builder.addSearchChain(provider1, options, List.of());
        builder.addSearchChain(provider2, options, List.of());
        builder.addSourceForProvider(news, provider1, provider1, true, options, List.of());
        builder.addSourceForProvider(news, provider2, provider2, false, options, List.of());

        return new FederationSearcher(builder.build(), Map.of());
    }

    private static class MockProvider extends Searcher {

        private final String name;

        public MockProvider(String name) {
            this.name = name;
        }

        @Override
        public Result search(Query query, Execution execution) {
            Result result = new Result(query);
            result.hits().add(new Hit(name + ":1"));
            return result;
        }

    }

}
