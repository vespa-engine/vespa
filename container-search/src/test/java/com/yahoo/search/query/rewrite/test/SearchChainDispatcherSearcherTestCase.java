// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.rewrite.test;

import java.util.*;
import java.io.File;

import com.yahoo.search.*;
import com.yahoo.search.searchchain.*;
import com.yahoo.search.query.rewrite.*;
import com.yahoo.search.query.rewrite.rewriters.*;
import com.yahoo.search.searchchain.SearchChainRegistry;
import com.yahoo.search.query.rewrite.RewritesConfig;
import com.yahoo.search.intent.model.*;
import com.yahoo.component.chain.Chain;
import org.junit.Before;
import org.junit.Test;

/**
 * Test Cases for SearchChainDispatcherSearcher
 *
 * @author Karen Lee
 */
public class SearchChainDispatcherSearcherTestCase {

    private QueryRewriteSearcherTestUtils utils;
    private final String NAME_REWRITER_CONFIG_PATH = "file:src/test/java/com/yahoo/search/query/rewrite/test/" +
                                                     "test_name_rewriter.cfg";
    private final String NAME_ENTITY_EXPAND_DICT_PATH = "src/test/java/com/yahoo/search/query/rewrite/test/" +
                                                        "name_rewriter_entity.fsa";
    private final String NAME_REWRITER_NAME = NameRewriter.REWRITER_NAME;
    private final String MISSPELL_REWRITER_NAME = MisspellRewriter.REWRITER_NAME;
    private final String US_MARKET_SEARCH_CHAIN = "us_qrw";

    /**
     * Load the QueryRewriteSearcher and prepare the
     * execution object
     */
    @Before
    public void setUp() {
        // Instantiate Name Rewriter
        RewritesConfig config = QueryRewriteSearcherTestUtils.createConfigObj(NAME_REWRITER_CONFIG_PATH);
        HashMap<String, File> fileList = new HashMap<>();
        fileList.put(NameRewriter.NAME_ENTITY_EXPAND_DICT, new File(NAME_ENTITY_EXPAND_DICT_PATH));
        NameRewriter nameRewriter = new NameRewriter(config, fileList);

        // Instantiate Misspell Rewriter
        MisspellRewriter misspellRewriter = new MisspellRewriter();

        // Create market search chain of two rewriters
        ArrayList<Searcher> searchers = new ArrayList<>();
        searchers.add(misspellRewriter);
        searchers.add(nameRewriter);
        Chain<Searcher> marketSearchChain = new Chain<>(US_MARKET_SEARCH_CHAIN, searchers);

        // Add market search chain to the registry
        SearchChainRegistry registry = new SearchChainRegistry();
        registry.register(marketSearchChain);

        // Instantiate Search Chain Dispatcher Searcher
        SearchChainDispatcherSearcher searchChainDispatcher = new SearchChainDispatcherSearcher();

        // Create a chain containing only the dispatcher
        Chain<Searcher> mainSearchChain = new Chain<>(searchChainDispatcher);
        Execution execution = new Execution(mainSearchChain, Execution.Context.createContextStub(registry, null));
        utils = new QueryRewriteSearcherTestUtils(execution);
    }

    /**
     * Execute the market chain
     * Query will be rewritten twice
     */
    @Test
    public void testMarketChain() {
        IntentModel intentModel = new IntentModel(
                                  utils.createInterpretation("wills smith", 0.9,
                                                             true, false),
                                  utils.createInterpretation("will smith", 1.0,
                                                             false, true));

        utils.assertRewrittenQuery("?query=willl+smith&QRWChain=" + US_MARKET_SEARCH_CHAIN + "&" +
                                   MISSPELL_REWRITER_NAME + "." + RewriterConstants.QSS_RW + "=true&" +
                                   MISSPELL_REWRITER_NAME + "." + RewriterConstants.QSS_SUGG + "=true&" +
                                   NAME_REWRITER_NAME + "." + RewriterConstants.REWRITES_AS_UNIT_EQUIV +
                                   "=true&" +
                                   NAME_REWRITER_NAME + "." + RewriterConstants.ORIGINAL_AS_UNIT_EQUIV + "=true",
                                   "query 'OR (AND willl smith) (AND will smith) " +
                                   "\"will smith\" \"will smith movies\" " +
                                   "\"will smith news\" \"will smith imdb\" " +
                                   "\"will smith lyrics\" \"will smith dead\" " +
                                   "\"will smith nfl\" \"will smith new movie hancock\" " +
                                   "\"will smith biography\"'",
                                   intentModel);
    }

    /**
     * Market chain is not valid
     * Query will be passed to next rewriter
     */
    @Test
    public void testInvalidMarketChain() {
        utils.assertRewrittenQuery("?query=will smith&QRWChain=abc&" +
                                   MISSPELL_REWRITER_NAME + "." + RewriterConstants.QSS_RW + "=true&" +
                                   MISSPELL_REWRITER_NAME + "." + RewriterConstants.QSS_SUGG + "=true&" +
                                   NAME_REWRITER_NAME + "." + RewriterConstants.REWRITES_AS_UNIT_EQUIV +
                                   "=true",
                                   "query 'AND will smith'");
    }

    /**
     * Empty market chain value
     * Query will be passed to next rewriter
     */
    @Test
    public void testEmptyMarketChain() {
        utils.assertRewrittenQuery("?query=will smith&QRWChain=&" +
                                   MISSPELL_REWRITER_NAME + "." + RewriterConstants.QSS_RW + "=true&" +
                                   MISSPELL_REWRITER_NAME + "." + RewriterConstants.QSS_SUGG + "=true&" +
                                   NAME_REWRITER_NAME + "." + RewriterConstants.REWRITES_AS_UNIT_EQUIV +
                                   "=true",
                                   "query 'AND will smith'");
    }

    /**
     * Searchers down the chain after SearchChainDispatcher
     * should be executed
     */
    @Test
    public void testChainContinuation() {
        // Instantiate Name Rewriter
        RewritesConfig config = QueryRewriteSearcherTestUtils.createConfigObj(NAME_REWRITER_CONFIG_PATH);
        HashMap<String, File> fileList = new HashMap<>();
        fileList.put(NameRewriter.NAME_ENTITY_EXPAND_DICT, new File(NAME_ENTITY_EXPAND_DICT_PATH));
        NameRewriter nameRewriter = new NameRewriter(config, fileList);

        // Instantiate Misspell Rewriter
        MisspellRewriter misspellRewriter = new MisspellRewriter();

        // Create market search chain of only misspell rewriter
        Chain<Searcher> marketSearchChain = new Chain<>(US_MARKET_SEARCH_CHAIN, misspellRewriter);

        // Add market search chain to the registry
        SearchChainRegistry registry = new SearchChainRegistry();
        registry.register(marketSearchChain);

        // Instantiate Search Chain Dispatcher Searcher
        SearchChainDispatcherSearcher searchChainDispatcher = new SearchChainDispatcherSearcher();

        // Create a chain containing the dispatcher and the name rewriter
        ArrayList<Searcher> searchers = new ArrayList<>();
        searchers.add(searchChainDispatcher);
        searchers.add(nameRewriter);

        // Create a chain containing only the dispatcher
        Chain<Searcher> mainSearchChain = new Chain<>(searchers);
        Execution execution = new Execution(mainSearchChain, Execution.Context.createContextStub(registry, null));
        new QueryRewriteSearcherTestUtils(execution);

        IntentModel intentModel = new IntentModel(
                                  utils.createInterpretation("wills smith", 0.9,
                                                             true, false),
                                  utils.createInterpretation("will smith", 1.0,
                                                             false, true));

        utils.assertRewrittenQuery("?query=willl+smith&QRWChain=" + US_MARKET_SEARCH_CHAIN + "&" +
                                   MISSPELL_REWRITER_NAME + "." + RewriterConstants.QSS_RW + "=true&" +
                                   MISSPELL_REWRITER_NAME + "." + RewriterConstants.QSS_SUGG + "=true&" +
                                   NAME_REWRITER_NAME + "." + RewriterConstants.REWRITES_AS_UNIT_EQUIV +
                                   "=true&" +
                                   NAME_REWRITER_NAME + "." + RewriterConstants.ORIGINAL_AS_UNIT_EQUIV + "=true",
                                   "query 'OR (AND willl smith) (AND will smith) " +
                                   "\"will smith\" \"will smith movies\" " +
                                   "\"will smith news\" \"will smith imdb\" " +
                                   "\"will smith lyrics\" \"will smith dead\" " +
                                   "\"will smith nfl\" \"will smith new movie hancock\" " +
                                   "\"will smith biography\"'",
                                   intentModel);
    }

}

