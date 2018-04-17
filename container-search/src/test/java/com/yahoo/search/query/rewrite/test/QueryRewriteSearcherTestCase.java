// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.rewrite.test;

import java.io.File;
import java.util.*;

import com.yahoo.search.*;
import com.yahoo.search.searchchain.*;
import com.yahoo.search.intent.model.*;
import com.yahoo.search.query.rewrite.RewritesConfig;
import com.yahoo.search.query.rewrite.*;
import com.yahoo.search.query.rewrite.rewriters.*;
import org.junit.Before;
import org.junit.Test;

/**
 * Generic Test Cases for QueryRewriteSearcher
 *
 * @author Karen Lee
 */
public class QueryRewriteSearcherTestCase {

    private QueryRewriteSearcherTestUtils utils;
    private final String NAME_REWRITER_CONFIG_PATH = "file:src/test/java/com/yahoo/search/query/rewrite/test/" +
                                                     "test_name_rewriter.cfg";
    private final String FAKE_FSA_CONFIG_PATH = "file:src/test/java/com/yahoo/search/query/rewrite/test/" +
                                                "test_rewriter_fake_fsa.cfg";
    private final String NAME_ENTITY_EXPAND_DICT_PATH = "src/test/java/com/yahoo/search/query/rewrite/test/" +
                                                        "name_rewriter_entity.fsa";
    private final String FAKE_FSA_PATH = "src/test/java/com/yahoo/search/query/rewrite/test/" +
                                         "test_name_rewriter.cfg";
    private final String NAME_REWRITER_NAME = NameRewriter.REWRITER_NAME;
    private final String MISSPELL_REWRITER_NAME = MisspellRewriter.REWRITER_NAME;

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

        // Create a chain of two rewriters
        ArrayList<Searcher> searchers = new ArrayList<>();
        searchers.add(misspellRewriter);
        searchers.add(nameRewriter);

        Execution execution = QueryRewriteSearcherTestUtils.createExecutionObj(searchers);
        utils = new QueryRewriteSearcherTestUtils(execution);
    }

    /**
     * Invalid FSA config path
     * Query will be passed to next rewriter
     */
    @Test
    public void testInvalidFSAConfigPath() {
        // Instantiate Name Rewriter with fake FSA path
        RewritesConfig config = QueryRewriteSearcherTestUtils.createConfigObj(FAKE_FSA_CONFIG_PATH);
        HashMap<String, File> fileList = new HashMap<>();
        fileList.put(NameRewriter.NAME_ENTITY_EXPAND_DICT, new File(FAKE_FSA_PATH));
        NameRewriter nameRewriterWithFakePath = new NameRewriter(config, fileList);

        // Instantiate Misspell Rewriter
        MisspellRewriter misspellRewriter = new MisspellRewriter();

        // Create a chain of two rewriters
        ArrayList<Searcher> searchers = new ArrayList<>();
        searchers.add(misspellRewriter);
        searchers.add(nameRewriterWithFakePath);

        Execution execution = QueryRewriteSearcherTestUtils.createExecutionObj(searchers);
        QueryRewriteSearcherTestUtils utilsWithFakePath = new QueryRewriteSearcherTestUtils(execution);

        utilsWithFakePath.assertRewrittenQuery("?query=will smith&" +
                                               NAME_REWRITER_NAME + "." +
                                               RewriterConstants.REWRITES_AS_UNIT_EQUIV + "=true",
                                               "query 'AND will smith'");
    }

    /**
     * IntentModel is null and rewriter throws exception
     * It should skip to the next rewriter
     */
    @Test
    public void testExceptionInRewriter() {
        utils.assertRewrittenQuery("?query=will smith&" +
                                   MISSPELL_REWRITER_NAME + "." + RewriterConstants.QSS_RW + "=true&" +
                                   MISSPELL_REWRITER_NAME + "." + RewriterConstants.QSS_SUGG + "=true&" +
                                   NAME_REWRITER_NAME + "." + RewriterConstants.REWRITES_AS_UNIT_EQUIV +
                                   "=true&" +
                                   NAME_REWRITER_NAME + "." + RewriterConstants.ORIGINAL_AS_UNIT_EQUIV + "=true",
                                   "query 'OR (AND will smith) " +
                                   "\"will smith\" \"will smith movies\" " +
                                   "\"will smith news\" \"will smith imdb\" " +
                                   "\"will smith lyrics\" \"will smith dead\" " +
                                   "\"will smith nfl\" \"will smith new movie hancock\" " +
                                   "\"will smith biography\"'");
    }

    /**
     * Two rewrites in chain
     * Query will be rewritten twice
     */
    @Test
    public void testTwoRewritersInChain() {
        IntentModel intentModel = new IntentModel(
                                  utils.createInterpretation("wills smith", 0.9,
                                                             true, false),
                                  utils.createInterpretation("will smith", 1.0,
                                                             false, true));

        utils.assertRewrittenQuery("?query=willl+smith&" +
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

