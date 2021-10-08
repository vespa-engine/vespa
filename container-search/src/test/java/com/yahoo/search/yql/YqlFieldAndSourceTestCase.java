// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.component.chain.Chain;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig.Documentdb;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig.Documentdb.Summaryclass;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig.Documentdb.Summaryclass.Fields;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.testutil.DocumentSourceSearcher;
import static com.yahoo.search.searchchain.testutil.DocumentSourceSearcher.DEFAULT_SUMMARY_CLASS;;

/**
 * Test translation of fields and sources in YQL+ to the associated concepts in
 * Vespa.
 */
public class YqlFieldAndSourceTestCase {

    private static final String FIELD1 = "field1";
    private static final String FIELD2 = "field2";
    private static final String FIELD3 = "field3";
    private static final String THIRD_OPTION = "THIRD_OPTION";

    private Chain<Searcher> searchChain;
    private Execution.Context context;
    private Execution execution;


    @Before
    public void setUp() throws Exception {
        Query query = new Query("?query=test");

        Result result = new Result(query);
        Hit hit = createHit("lastHit", .1d, FIELD1, FIELD2, FIELD3);
        result.hits().add(hit);

        DocumentSourceSearcher mockBackend = new DocumentSourceSearcher();
        mockBackend.addResult(query, result);

        mockBackend.addSummaryClassByCopy(DEFAULT_SUMMARY_CLASS, Arrays.asList(FIELD1, FIELD2));
        mockBackend.addSummaryClassByCopy(Execution.ATTRIBUTEPREFETCH, Arrays.asList(FIELD2));
        mockBackend.addSummaryClassByCopy(THIRD_OPTION, Arrays.asList(FIELD3));

        DocumentdbInfoConfig config = new DocumentdbInfoConfig(new DocumentdbInfoConfig.Builder()
                                                               .documentdb(buildDocumentdbArray()));

        searchChain = new Chain<>(new FieldFiller(config), mockBackend);
        context = Execution.Context.createContextStub(null);
        execution = new Execution(searchChain, context);
    }

    private Hit createHit(String id, double relevancy, String... fieldNames) {
        Hit h = new Hit(id, relevancy);
        h.setFillable();
        int i = 0;
        for (String field : fieldNames) {
            h.setField(field, ++i);
        }
        return h;
    }

    private List<Documentdb.Builder> buildDocumentdbArray() {
        List<Documentdb.Builder> configArray = new ArrayList<>(1);
        configArray.add(new Documentdb.Builder().summaryclass(
                buildSummaryclassArray()).name("defaultsearchdefinition"));

        return configArray;
    }

    private List<Summaryclass.Builder> buildSummaryclassArray() {
        return Arrays.asList(
                new Summaryclass.Builder()
                        .id(0)
                        .name(DEFAULT_SUMMARY_CLASS)
                        .fields(Arrays.asList(new Fields.Builder().name(FIELD1).type("string"),
                                              new Fields.Builder().name(FIELD2).type("string"))),
                new Summaryclass.Builder()
                        .id(1)
                        .name(Execution.ATTRIBUTEPREFETCH)
                        .fields(Arrays.asList(new Fields.Builder().name(FIELD2).type("string"))),
                new Summaryclass.Builder()
                        .id(2)
                        .name(THIRD_OPTION)
                        .fields(Arrays.asList(new Fields.Builder().name(FIELD3).type("string"))));
    }

    @After
    public void tearDown() throws Exception {
        searchChain = null;
        context = null;
        execution = null;
    }

    @Test
    public final void testTrivial() {
        final Query query = new Query("?query=test&presentation.summaryFields=" + FIELD1);
        Result result = execution.search(query);
        execution.fill(result);
        assertEquals(1, result.getConcreteHitCount());
        assertTrue(result.hits().get(0).isFilled(DEFAULT_SUMMARY_CLASS));
        assertFalse(result.hits().get(0).isFilled(Execution.ATTRIBUTEPREFETCH));
    }

    @Test
    public final void testWithOnlyAttribute() {
        final Query query = new Query("?query=test&presentation.summaryFields=" + FIELD2);
        Result result = execution.search(query);
        execution.fill(result, THIRD_OPTION);
        assertEquals(1, result.getConcreteHitCount());
        assertTrue(result.hits().get(0).isFilled(THIRD_OPTION));
        assertFalse(result.hits().get(0).isFilled(DEFAULT_SUMMARY_CLASS));
        assertTrue(result.hits().get(0).isFilled(Execution.ATTRIBUTEPREFETCH));
    }

    @Test
    public final void testWithOnlyDiskfieldCorrectClassRequested() {
        final Query query = new Query("?query=test&presentation.summaryFields=" + FIELD3);
        Result result = execution.search(query);
        execution.fill(result, THIRD_OPTION);
        assertEquals(1, result.getConcreteHitCount());
        assertTrue(result.hits().get(0).isFilled(THIRD_OPTION));
        assertFalse(result.hits().get(0).isFilled(DEFAULT_SUMMARY_CLASS));
        assertFalse(result.hits().get(0).isFilled(Execution.ATTRIBUTEPREFETCH));
    }
    @Test
    public final void testTrivialCaseWithOnlyDiskfieldWrongClassRequested() {
        final Query query = new Query("?query=test&presentation.summaryFields=" + FIELD1);
        Result result = execution.search(query);
        execution.fill(result, THIRD_OPTION);
        assertEquals(1, result.getConcreteHitCount());
        assertTrue(result.hits().get(0).isFilled(THIRD_OPTION));
        assertTrue(result.hits().get(0).isFilled(DEFAULT_SUMMARY_CLASS));
        assertFalse(result.hits().get(0).isFilled(Execution.ATTRIBUTEPREFETCH));
    }

}
