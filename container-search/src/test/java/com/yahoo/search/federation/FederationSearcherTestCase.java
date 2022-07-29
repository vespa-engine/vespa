// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.SearchChain;
import com.yahoo.search.searchchain.SearchChainRegistry;
import com.yahoo.search.searchchain.model.federation.FederationOptions;
import com.yahoo.search.test.QueryTestCase;
import com.yahoo.yolean.trace.TraceNode;
import com.yahoo.yolean.trace.TraceVisitor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

    private void addChained(Searcher searcher, String sourceName) {
        builder.target(new FederationConfig.Target.Builder().
                id(sourceName).
                searchChain(new FederationConfig.Target.SearchChain.Builder().
                        searchChainId(sourceName).
                        timeoutMillis(10000).
                        useByDefault(true))
        );
        chainRegistry.register(new ComponentId(sourceName),
                createSearchChain(new ComponentId(sourceName), searcher));
    }

    private Searcher createFederationSearcher() {
        return new FederationSearcher(new FederationConfig(builder), new ComponentRegistry<>());
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
        Chain<Searcher> mainChain = twoTracingSources(false);

        Query q = new Query(com.yahoo.search.test.QueryTestCase.httpEncode("?query=test&traceLevel=1"));

        Execution execution = new Execution(mainChain, Execution.Context.createContextStub(chainRegistry));
        Result result = execution.search(q);
        assertNull(result.hits().getError());
        TwoSourceChecker lookForTraces = new TwoSourceChecker();
        execution.trace().accept(lookForTraces);
        assertTrue(lookForTraces.traceFromSource1);
        assertTrue(lookForTraces.traceFromSource2);
    }

    private Chain<Searcher> twoTracingSources(boolean strictContracts) {
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
                           new FederationSearcher(new FederationConfig(builder), new ComponentRegistry<>()));
    }

    @Test
    void testTraceOneSourceNoCloning() {
        Chain<Searcher> mainChain = twoTracingSources(true);

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
        Chain<Searcher> mainChain = twoTracingSources(false);

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

        return new FederationSearcher(new ComponentId("federation"), builder.build());
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

    private static class QueryCheckSearcher extends Searcher {

        private static final String STATUS = "status";
        public static final String FEDERATION_SEARCHER_HAS_CLONED_THE_QUERY = "FederationSearcher has cloned the query.";
        public static final String OK = "Got the correct query.";
        private final Query query;

        QueryCheckSearcher(Query query) {
            this.query = query;
        }

        @Override
        public Result search(Query query, Execution execution) {
            Result result = new Result(query);
            if (query != this.query) {
                result.hits().addError(ErrorMessage
                        .createErrorInPluginSearcher(FEDERATION_SEARCHER_HAS_CLONED_THE_QUERY));
            } else {
                final Hit h = new Hit("QueryCheckSearcher status hit");
                h.setField(STATUS, OK);
                result.hits().add(h);
            }
            return result;
        }
    }

}
