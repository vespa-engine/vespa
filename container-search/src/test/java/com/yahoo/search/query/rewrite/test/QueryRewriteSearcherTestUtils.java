// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.rewrite.test;

import com.yahoo.search.test.QueryTestCase;
import junit.framework.Assert;
import java.util.*;

import com.yahoo.search.*;
import com.yahoo.search.searchchain.*;
import com.yahoo.search.query.rewrite.RewritesConfig;
import com.yahoo.search.intent.model.*;
import com.yahoo.text.interpretation.Modification;
import com.yahoo.text.interpretation.Interpretation;
import com.yahoo.text.interpretation.Annotations;
import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.component.chain.Chain;

/**
 * Test utilities for QueryRewriteSearcher
 *
 * @author karenlee@yahoo-inc.com
 */
public class QueryRewriteSearcherTestUtils {

    private Execution execution;

    /**
     * Constructor for this class
     * Load the QueryRewriteSearcher and prepare the
     * execution object
     */
    public QueryRewriteSearcherTestUtils(Execution execution) {
        this.execution = execution;
    }


    /**
     * Create config object based on config path
     *
     * @param configPath path for the searcher config
     */
    public static RewritesConfig createConfigObj(String configPath) {
        ConfigGetter<RewritesConfig> getter = new ConfigGetter<>(RewritesConfig.class);
        RewritesConfig config = getter.getConfig(configPath);
        return config;
    }

    /**
     * Create execution object based on searcher
     *
     * @param searcher searcher to be added to the search chain
     */
    public static Execution createExecutionObj(Searcher searcher) {
        @SuppressWarnings("deprecation")
        Chain<Searcher> searchChain = new Chain<>(searcher);
        Execution myExecution = new Execution(searchChain, Execution.Context.createContextStub());
        return myExecution;
    }

    /**
     * Create execution object based on searchers
     *
     * @param searchers list of searchers to be added to the search chain
     */
    public static Execution createExecutionObj(List<Searcher> searchers) {
        @SuppressWarnings("deprecation")
        Chain<Searcher> searchChain = new Chain<>(searchers);
        Execution myExecution = new Execution(searchChain, Execution.Context.createContextStub());
        return myExecution;
    }

    /**
     * Compare the rewritten query returned after executing
     * the origQuery against the provided finalQuery
     * @param origQuery query to be passed to Query object
     *                  e.g. "?query=will%20smith"
     * @param finalQuery expected final query from result.getQuery()
     *                   e.g. "query 'AND will smith'"
     */
    public void assertRewrittenQuery(String origQuery, String finalQuery) {
        Query query = new Query(QueryTestCase.httpEncode(origQuery));
        Result result = execution.search(query);
        Assert.assertEquals(finalQuery, result.getQuery().toString());
    }

    /**
     * Set the provided intent model
     * Compare the rewritten query returned after executing
     * the origQuery against the provided finalQuery
     * @param origQuery query to be passed to Query object
     *                  e.g. "?query=will%20smith"
     * @param finalQuery expected final query from result.getQuery()
     *                   e.g. "query 'AND will smith'"
     * @param intentModel IntentModel to be added to the Query
     */
    public void assertRewrittenQuery(String origQuery, String finalQuery, IntentModel intentModel) {
        Query query = new Query(origQuery);
        intentModel.setTo(query);
        Result result = execution.search(query);
        Assert.assertEquals(finalQuery, result.getQuery().toString());
    }

    /**
     * Create a new interpretation with modification that
     * contains the passed in query and score
     * @param spellRewrite query to be used as modification
     * @param score score to be used as modification score
     * @param isQSSRW whether the modification is qss_rw
     * @param isQSSSugg whether the modification is qss_sugg
     * @return newly created interpretation with modification
     */
    public Interpretation createInterpretation(String spellRewrite, double score,
                                               boolean isQSSRW, boolean isQSSSugg) {
        Modification modification = new Modification(spellRewrite);
        Annotations annotation = modification.getAnnotation();
        annotation.put("score", score);
        if(isQSSRW)
            annotation.put("qss_rw", true);
        if(isQSSSugg)
            annotation.put("qss_sugg", true);
        Interpretation interpretation = new Interpretation(modification);
        return interpretation;
    }
}

