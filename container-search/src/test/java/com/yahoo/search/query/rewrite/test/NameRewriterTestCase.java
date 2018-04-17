// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.rewrite.test;

import java.util.*;
import java.io.File;

import com.yahoo.search.searchchain.*;
import com.yahoo.search.query.rewrite.*;
import com.yahoo.search.query.rewrite.rewriters.*;
import com.yahoo.search.query.rewrite.RewritesConfig;
import org.junit.Before;
import org.junit.Test;

/**
 * Test Cases for NameRewriter
 *
 * @author Karen Lee
 */
public class NameRewriterTestCase {

    private QueryRewriteSearcherTestUtils utils;
    private final String CONFIG_PATH = "file:src/test/java/com/yahoo/search/query/rewrite/test/" +
                                       "test_name_rewriter.cfg";
    private final String NAME_ENTITY_EXPAND_DICT_PATH = "src/test/java/com/yahoo/search/query/rewrite/test/" +
                                                        "name_rewriter_entity.fsa";
    private final String REWRITER_NAME = NameRewriter.REWRITER_NAME;

    /**
     * Load the NameRewriterSearcher and prepare the
     * execution object
     */
    @Before
    public void setUp() {
        RewritesConfig config = QueryRewriteSearcherTestUtils.createConfigObj(CONFIG_PATH);
        HashMap<String, File> fileList = new HashMap<>();
        fileList.put(NameRewriter.NAME_ENTITY_EXPAND_DICT, new File(NAME_ENTITY_EXPAND_DICT_PATH));
        NameRewriter searcher = new NameRewriter(config, fileList);

        Execution execution = QueryRewriteSearcherTestUtils.createExecutionObj(searcher);
        utils = new QueryRewriteSearcherTestUtils(execution);
    }

    /**
     * RewritesAsEquiv and OriginalAsUnit are on
     */
    @Test
    public void testRewritesAsEquivAndOriginalAsUnit() {
        utils.assertRewrittenQuery("?query=will smith&" +
                                   REWRITER_NAME + "." + RewriterConstants.REWRITES_AS_EQUIV + "=true&" +
                                   REWRITER_NAME + "." + RewriterConstants.ORIGINAL_AS_UNIT + "=true",
                                   "query 'OR \"will smith\" (AND will smith movies) " +
                                   "(AND will smith news) (AND will smith imdb) " +
                                   "(AND will smith lyrics) (AND will smith dead) " +
                                   "(AND will smith nfl) (AND will smith new movie hancock) " +
                                   "(AND will smith biography)'");
    }

    /**
     * RewritesAsEquiv is on
     */
    @Test
    public void testRewritesAsEquiv() {
        utils.assertRewrittenQuery("?query=will smith&" +
                                   REWRITER_NAME + "." + RewriterConstants.REWRITES_AS_EQUIV + "=true&",
                                   "query 'OR (AND will smith) (AND will smith movies) " +
                                   "(AND will smith news) (AND will smith imdb) " +
                                   "(AND will smith lyrics) (AND will smith dead) " +
                                   "(AND will smith nfl) (AND will smith new movie hancock) " +
                                   "(AND will smith biography)'");
    }

    /**
     * Complex query with more than two levels for RewritesAsEquiv is on case
     * Should not rewrite
     */
    @Test
    public void testComplextQueryRewritesAsEquiv() {
        utils.assertRewrittenQuery("?query=((will smith) OR (willl smith)) AND (tom cruise)&type=adv&" +
                                   REWRITER_NAME + "." + RewriterConstants.REWRITES_AS_EQUIV + "=true&",
                                   "query 'AND (OR (AND will smith) (AND willl smith)) (AND tom cruise)'");
    }

    /**
     * Single word query for RewritesAsEquiv and OriginalAsUnit on case
     */
    @Test
    public void testSingleWordForRewritesAsEquivAndOriginalAsUnit() {
        utils.assertRewrittenQuery("?query=obama&" +
                                   REWRITER_NAME + "." + RewriterConstants.REWRITES_AS_EQUIV + "=true&" +
                                   REWRITER_NAME + "." + RewriterConstants.ORIGINAL_AS_UNIT + "=true",
                                   "query 'OR obama (AND obama \"nobel peace prize\") " +
                                   "(AND obama wiki) (AND obama nobel prize) " +
                                   "(AND obama nobel peace prize) (AND obama wears mom jeans) " +
                                   "(AND obama sucks) (AND obama news) (AND malia obama) " +
                                   "(AND obama speech) (AND obama nobel) (AND obama wikipedia) " +
                                   "(AND barack obama biography) (AND obama snl) " +
                                   "(AND obama peace prize) (AND michelle obama) (AND barack obama)'");
    }

    /**
     * RewritesAsUnitEquiv and OriginalAsUnitEquiv are on
     */
    @Test
    public void testRewritesAsUnitEquivAndOriginalAsUnitEquiv() {
        utils.assertRewrittenQuery("?query=will smith&" +
                                   REWRITER_NAME + "." + RewriterConstants.REWRITES_AS_UNIT_EQUIV +
                                   "=true&" +
                                   REWRITER_NAME + "." + RewriterConstants.ORIGINAL_AS_UNIT_EQUIV +
                                   "=true",
                                   "query 'OR (AND will smith) \"will smith\" \"will smith movies\" " +
                                   "\"will smith news\" \"will smith imdb\" " +
                                   "\"will smith lyrics\" \"will smith dead\" " +
                                   "\"will smith nfl\" \"will smith new movie hancock\" " +
                                   "\"will smith biography\"'");
    }

    /**
     * Single word query for RewritesAsUnitEquiv and OriginalAsUnitEquiv on case
     */
    @Test
    public void testSingleWordForRewritesAsUnitEquivAndOriginalAsUnitEquiv() {
        utils.assertRewrittenQuery("?query=obama&" +
                                   REWRITER_NAME + "." + RewriterConstants.REWRITES_AS_UNIT_EQUIV +
                                   "=true&" +
                                   REWRITER_NAME + "." + RewriterConstants.ORIGINAL_AS_UNIT_EQUIV +
                                   "=true",
                                   "query 'OR obama \"obama nobel peace prize\" " +
                                   "\"obama wiki\" \"obama nobel prize\" " +
                                   "\"obama wears mom jeans\" " +
                                   "\"obama sucks\" \"obama news\" \"malia obama\" " +
                                   "\"obama speech\" \"obama nobel\" \"obama wikipedia\" " +
                                   "\"barack obama biography\" \"obama snl\" " +
                                   "\"obama peace prize\" \"michelle obama\" \"barack obama\"'");
    }

    /**
     * Boosting only query (n/a as rewrite in FSA)
     * for RewritesAsEquiv and OriginalAsUnit on case
     */
    @Test
    public void testBoostingQueryForRewritesAsEquivAndOriginalAsUnit() {
        utils.assertRewrittenQuery("?query=angelina jolie&" +
                                   REWRITER_NAME + "." + RewriterConstants.REWRITES_AS_EQUIV + "=true&" +
                                   REWRITER_NAME + "." + RewriterConstants.ORIGINAL_AS_UNIT + "=true",
                                   "query '\"angelina jolie\"'");
    }

    /**
     * No match in FSA for the query
     * RewritesAsEquiv and OriginalAsUnit on case
     */
    @Test
    public void testFSANoMatchForRewritesAsEquivAndOriginalAsUnit() {
        utils.assertRewrittenQuery("?query=tom cruise&" +
                                   REWRITER_NAME + "." + RewriterConstants.REWRITES_AS_EQUIV + "=true&" +
                                   REWRITER_NAME + "." + RewriterConstants.ORIGINAL_AS_UNIT + "=true",
                                   "query 'AND tom cruise'");
    }

    /**
     * RewritesAsUnitEquiv is on
     */
    @Test
    public void testRewritesAsUnitEquiv() {
        utils.assertRewrittenQuery("?query=will smith&" +
                                   REWRITER_NAME + "." + RewriterConstants.REWRITES_AS_UNIT_EQUIV +
                                   "=true",
                                   "query 'OR (AND will smith) \"will smith movies\" " +
                                   "\"will smith news\" \"will smith imdb\" " +
                                   "\"will smith lyrics\" \"will smith dead\" " +
                                   "\"will smith nfl\" \"will smith new movie hancock\" " +
                                   "\"will smith biography\"'");
    }

    /**
     * RewritesAsUnitEquiv is on and MaxRewrites is set to 2
     */
    @Test
    public void testRewritesAsUnitEquivAndMaxRewrites() {
        utils.assertRewrittenQuery("?query=will smith&" +
                                   REWRITER_NAME + "." + RewriterConstants.REWRITES_AS_UNIT_EQUIV +
                                   "=true&" +
                                   REWRITER_NAME + "." + RewriterConstants.MAX_REWRITES + "=2",
                                   "query 'OR (AND will smith) \"will smith movies\" " +
                                   "\"will smith news\"'");
    }

}

