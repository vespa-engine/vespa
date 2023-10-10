// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.ForkingSearcher;
import com.yahoo.search.searchchain.SearchChain;
import com.yahoo.search.searchchain.SearchChainRegistry;

/**
 * A search chain consisting of two searchers.
 * @author bratseth
 * @author Tony Vaagenes
 */
public class SimpleSearchChain {

    private static abstract class BaseSearcher extends ForkingSearcher {

        public BaseSearcher(ComponentId id) {
            super();
            initId(id);
        }

        @Override
        public Result search(Query query,Execution execution) {
            return execution.search(query);
        }

        @Override
        public Collection<ForkingSearcher.CommentedSearchChain> getSearchChainsForwarded(SearchChainRegistry registry) {
            return Arrays.asList(
                    new ForkingSearcher.CommentedSearchChain("Reason for forwarding to this search chain.", dummySearchChain()),
                    new ForkingSearcher.CommentedSearchChain(null, dummySearchChain()));
        }

        private SearchChain dummySearchChain() {
            return new SearchChain(new ComponentId("child-chain"),
                    new DummySearcher(new ComponentId("child-searcher")) {});
        }

    }

    @Provides("Test")
    private static class TestSearcher extends BaseSearcher {

        public TestSearcher(ComponentId id) {
            super(id);
        }

    }

    private static class DummySearcher extends Searcher {

        public DummySearcher(ComponentId id) {
            super(id);
        }

        @Override
        public Result search(Query query,Execution execution) {
            return execution.search(query);
        }

    }

    @After("Test")
    private static class TestSearcher2 extends BaseSearcher {

        public TestSearcher2(ComponentId id) {
            super(id);
        }

        @Override
        public Result search(Query query,Execution execution) {
            return execution.search(query);
        }

    }

    private static List<Searcher> twoSearchers(String id1, String id2, boolean ordered) {
        List<Searcher> searchers=new ArrayList<>();
        searchers.add(new TestSearcher(new ComponentId(id1)));
        searchers.add(createSecondSearcher(new ComponentId(id2), ordered));
        return searchers;
    }

    private static Searcher createSecondSearcher(ComponentId componentId, boolean ordered) {
        if (ordered)
            return new TestSearcher2(componentId);
        else
            return new TestSearcher(componentId);
    }

    private static SearchChain createSearchChain(boolean ordered) {
        return new SearchChain(new ComponentId("test"), twoSearchers("one","two", ordered));
    }

    public static final SearchChain searchChain = createSearchChain(false);
    public static final SearchChain orderedChain = createSearchChain(true);

}
