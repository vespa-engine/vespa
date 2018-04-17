// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.rewrite.test;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.intent.model.IntentModel;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.test.QueryTestCase;
import com.yahoo.search.query.rewrite.RewritesConfig;
import com.yahoo.text.interpretation.Modification;
import com.yahoo.text.interpretation.Interpretation;
import com.yahoo.text.interpretation.Annotations;
import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.component.chain.Chain;
import org.junit.Assert;

import java.util.List;

/**
 * Test utilities for QueryRewriteSearcher
 *
 * @author Karen Lee
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
        return new ConfigGetter<>(RewritesConfig.class).getConfig(configPath);
    }

    /**
     * Create execution object based on searcher
     *
     * @param searcher searcher to be added to the search chain
     */
    public static Execution createExecutionObj(Searcher searcher) {
        return new Execution(new Chain<>(searcher), Execution.Context.createContextStub());
    }

    /**
     * Create execution object based on searchers
     *
     * @param searchers list of searchers to be added to the search chain
     */
    public static Execution createExecutionObj(List<Searcher> searchers) {
        return new Execution(new Chain<>(searchers), Execution.Context.createContextStub());
    }

    /**
     * Compare the rewritten query returned after executing
     * the origQuery against the provided finalQuery
     *
     * @param origQuery query to be passed to Query object e.g. "?query=will%20smith"
     * @param finalQuery expected final query from result.getQuery() e.g. "query 'AND will smith'"
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
     *
     * @param origQuery query to be passed to Query object e.g. "?query=will%20smith"
     * @param finalQuery expected final query from result.getQuery() e.g. "query 'AND will smith'"
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
     *
     * @param spellRewrite query to be used as modification
     * @param score score to be used as modification score
     * @param isQSSRW whether the modification is qss_rw
     * @param isQSSSugg whether the modification is qss_sugg
     * @return newly created interpretation with modification
     */
    public Interpretation createInterpretation(String spellRewrite, double score, boolean isQSSRW, boolean isQSSSugg) {
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

