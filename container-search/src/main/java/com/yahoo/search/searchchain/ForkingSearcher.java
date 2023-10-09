// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.search.Searcher;

import java.util.Collection;

/**
 * Searchers which invokes other search chains should override this.
 *
 * @author bratseth
 */
public abstract class ForkingSearcher extends Searcher {

    public ForkingSearcher() {}

    /** A search chain with a comment about when it is used. */
    public static class CommentedSearchChain {
        public final String comment;
        public final Chain<Searcher> searchChain;

        public CommentedSearchChain(String comment, Chain<Searcher> searchChain) {
            this.comment = comment;
            this.searchChain = searchChain;
        }
    }

    /** Returns which searchers this searcher may forward to, for debugging and tracing */
    public abstract Collection<CommentedSearchChain> getSearchChainsForwarded(SearchChainRegistry registry);

}
