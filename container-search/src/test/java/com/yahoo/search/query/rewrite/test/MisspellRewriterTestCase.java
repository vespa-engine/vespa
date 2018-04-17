// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.rewrite.test;

import com.yahoo.search.*;
import com.yahoo.search.searchchain.*;
import com.yahoo.search.intent.model.*;
import com.yahoo.search.query.rewrite.*;
import com.yahoo.search.query.rewrite.rewriters.*;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Test Cases for MisspellRewriter
 *
 * @author Karen Lee
 */
public class MisspellRewriterTestCase {

    private QueryRewriteSearcherTestUtils utils;
    public final String REWRITER_NAME = MisspellRewriter.REWRITER_NAME;

    /**
     * Load the QueryRewriteSearcher and prepare the
     * execution object
     */
    @Before
    public void setUp() {
        MisspellRewriter searcher = new MisspellRewriter();
        Execution execution = QueryRewriteSearcherTestUtils.createExecutionObj(searcher);
        utils = new QueryRewriteSearcherTestUtils(execution);
    }

    /**
     * QSSRewrite and QSSSuggest are on
     * QLAS returns spell correction: qss_rw=0.9 qss_sugg=1.0
     */
    @Test
    public void testQSSRewriteQSSSuggestWithRewrite() {
        IntentModel intentModel = new IntentModel(
                                  utils.createInterpretation("will smith rw", 0.9,
                                                             true, false),
                                  utils.createInterpretation("will smith sugg", 1.0,
                                                             false, true));

        utils.assertRewrittenQuery("?query=willl+smith&" +
                                   REWRITER_NAME + "." + RewriterConstants.QSS_RW + "=true&" +
                                   REWRITER_NAME + "." + RewriterConstants.QSS_SUGG + "=true",
                                   "query 'OR (AND willl smith) (AND will smith sugg)'",
                                   intentModel);
    }

    /**
     * QSSRewrite is on
     * QLAS returns spell correction: qss_rw=0.9 qss_rw=0.9 qss_sugg=1.0
     */
    @Test
    public void testQSSRewriteWithRewrite() {
        IntentModel intentModel = new IntentModel(
                                  utils.createInterpretation("will smith rw1", 0.9,
                                                             true, false),
                                  utils.createInterpretation("will smith rw2", 0.9,
                                                             true, false),
                                  utils.createInterpretation("will smith sugg", 1.0,
                                                             false, true));

        utils.assertRewrittenQuery("?query=willl+smith&" +
                                   REWRITER_NAME + "." + RewriterConstants.QSS_RW + "=true",
                                   "query 'OR (AND willl smith) (AND will smith rw1)'",
                                   intentModel);
    }

    /**
     * QSSSuggest is on
     * QLAS returns spell correction: qss_rw=1.0 qss_sugg=0.9 qss_sugg=0.8
     */
    @Test
    public void testQSSSuggWithRewrite() {
        IntentModel intentModel = new IntentModel(
                                  utils.createInterpretation("will smith rw", 1.0,
                                                             true, false),
                                  utils.createInterpretation("will smith sugg1", 0.9,
                                                             false, true),
                                  utils.createInterpretation("will smith sugg2", 0.8,
                                                             false, true));

        utils.assertRewrittenQuery("?query=willl+smith&" +
                                   REWRITER_NAME + "." + RewriterConstants.QSS_SUGG + "=true",
                                   "query 'OR (AND willl smith) (AND will smith sugg1)'",
                                   intentModel);
    }

    /**
     * QSSRewrite and QSSSuggest are off
     * QLAS returns spell correction: qss_rw=1.0 qss_sugg=1.0
     */
    @Test
    public void testFeautureOffWithRewrite() {
        IntentModel intentModel = new IntentModel(
                                  utils.createInterpretation("will smith rw", 1.0,
                                                             true, false),
                                  utils.createInterpretation("will smith sugg", 1.0,
                                                             false, true));

        utils.assertRewrittenQuery("?query=willl+smith",
                                   "query 'AND willl smith'",
                                   intentModel);
    }

    /**
     * QSSRewrite and QSSSuggest are on
     * QLAS returns no spell correction
     */
    @Test
    public void testQSSRewriteQSSSuggWithoutRewrite() {
        IntentModel intentModel = new IntentModel(
                                  utils.createInterpretation("use diff query for testing", 1.0,
                                                             false, false),
                                  utils.createInterpretation("use diff query for testing", 1.0,
                                                             false, false));

        utils.assertRewrittenQuery("?query=will+smith&" +
                                   REWRITER_NAME + "." + RewriterConstants.QSS_RW + "=true&" +
                                   REWRITER_NAME + "." + RewriterConstants.QSS_SUGG + "=true",
                                   "query 'AND will smith'",
                                   intentModel);
    }

    /**
     * IntentModel is null
     * It should throw exception
     */
    @Test
    public void testNullIntentModelException() {
        try {
            RewriterUtils.getSpellCorrected(new Query("willl smith"), true, true);
            fail();
        } catch (RuntimeException e) {
        }
    }

}

