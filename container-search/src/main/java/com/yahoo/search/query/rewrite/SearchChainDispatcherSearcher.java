// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.rewrite;

import com.yahoo.component.chain.Chain;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.search.*;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.component.ComponentId;

import java.util.logging.Logger;

/**
 * Execute rewriter search chain specified by the user.
 * It's inteneded to be used for executing rewriter search chains
 * for different markets.
 *
 * @author Karen Sze Wing Lee
 */
@Provides("SearchChainDispatcher")
@After("QLAS")
public class SearchChainDispatcherSearcher extends Searcher {

    protected final Logger logger = Logger.getLogger(SearchChainDispatcherSearcher.class.getName());

    /**
     * Constructor for this searcher
     * @param id Component ID (see vespa's search container doc for more detail)
     */
    public SearchChainDispatcherSearcher(ComponentId id) {
        super(id);
    }

    /**
     * Constructor for unit test
     */
    public SearchChainDispatcherSearcher() {
    }

    /**
     * Execute another search chain specified by the user<br>
     * - Retrieve search chain specified by the user through
     *   param<br>
     * - Execute specified search chain if exist
     */
    @Override
    public Result search(Query query, Execution execution) {
        RewriterUtils.log(logger, query, "Entering SearchChainDispatcherSearcher");

        // Retrieve search chain specified by user through REWRITER_CHAIN
        String rewriterChain = RewriterUtils.getRewriterChain(query);

        // Skipping to next searcher if no rewriter chain is specified
        if(rewriterChain==null || rewriterChain.equals("")) {
            RewriterUtils.log(logger, query, "No rewriter chain is specified, " +
                              "skipping to the next searcher");
            return execution.search(query);
        }

        // Execute rewriter search chain
        RewriterUtils.log(logger, query, "Redirecting to chain " + rewriterChain);
        Chain<Searcher> myChain = execution.searchChainRegistry().getChain(rewriterChain);
        if(myChain==null) {
            RewriterUtils.log(logger, query, "Invalid search chain specified, " +
                              "skipping to the next searcher");
            return execution.search(query);
        }
        new Execution(myChain, execution.context()).search(query);
        RewriterUtils.log(logger, query, "Finish executing search chain " + rewriterChain);

        // Continue down the chain ignoring the result from REWRITER_CHAIN
        // since the rewriters only modify the query but not the result
        return execution.search(query);
    }
}
