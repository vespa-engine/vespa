// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.yahoo.search.schema.DocumentSummary;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.yahoo.component.chain.Chain;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.testutil.DocumentSourceSearcher;
import static com.yahoo.search.searchchain.testutil.DocumentSourceSearcher.DEFAULT_SUMMARY_CLASS;
import static com.yahoo.prelude.fastsearch.VespaBackEndSearcher.SORTABLE_ATTRIBUTES_SUMMARY_CLASS;


/**
 * Test translation of fields and sources in YQL to the associated concepts in Vespa.
 */
public class YqlFieldAndSourceTestCase {

    private static final String FIELD1 = "field1";
    private static final String FIELD2 = "field2";
    private static final String FIELD3 = "field3";
    private static final String THIRD_OPTION = "THIRD_OPTION";

    private Chain<Searcher> searchChain;
    private Execution.Context context;
    private Execution execution;

    @BeforeEach
    public void setUp() throws Exception {
        Query query = new Query("?query=test");

        Result result = new Result(query);
        Hit hit = createHit("lastHit", .1d, FIELD1, FIELD2, FIELD3);
        result.hits().add(hit);

        DocumentSourceSearcher mockBackend = new DocumentSourceSearcher();
        mockBackend.addResult(query, result);

        mockBackend.addSummaryClassByCopy(DEFAULT_SUMMARY_CLASS, Arrays.asList(FIELD1, FIELD2));
        mockBackend.addSummaryClassByCopy(SORTABLE_ATTRIBUTES_SUMMARY_CLASS, Arrays.asList(FIELD2));
        mockBackend.addSummaryClassByCopy(THIRD_OPTION, Arrays.asList(FIELD3));

        searchChain = new Chain<>(new FieldFiller(schemaInfo()), mockBackend);
        context = Execution.Context.createContextStub();
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

    private SchemaInfo schemaInfo() {
        var schema = new Schema.Builder("defaultsearchdefinition");
        schema.add(new DocumentSummary.Builder(DEFAULT_SUMMARY_CLASS).addField(FIELD1, "string")
                                                                     .addField(FIELD2, "string").build())
              .add((new DocumentSummary.Builder(SORTABLE_ATTRIBUTES_SUMMARY_CLASS).addField(FIELD2, "string").build()))
              .add((new DocumentSummary.Builder(THIRD_OPTION).addField(FIELD3, "string").build()));
        return new SchemaInfo(List.of(schema.build()), List.of());
    }

    @AfterEach
    public void tearDown() throws Exception {
        searchChain = null;
        context = null;
        execution = null;
    }

    @Test
    final void testTrivial() {
        final Query query = new Query("?query=test&presentation.summaryFields=" + FIELD1);
        Result result = execution.search(query);
        execution.fill(result);
        assertEquals(1, result.getConcreteHitCount());
        assertTrue(result.hits().get(0).isFilled(DEFAULT_SUMMARY_CLASS));
        assertFalse(result.hits().get(0).isFilled(SORTABLE_ATTRIBUTES_SUMMARY_CLASS));
    }

    @Test
    final void testWithOnlyAttribute() {
        final Query query = new Query("?query=test&presentation.summaryFields=" + FIELD2);
        Result result = execution.search(query);
        execution.fill(result, THIRD_OPTION);
        assertEquals(1, result.getConcreteHitCount());
        assertTrue(result.hits().get(0).isFilled(THIRD_OPTION));
        assertFalse(result.hits().get(0).isFilled(DEFAULT_SUMMARY_CLASS));
        assertTrue(result.hits().get(0).isFilled(SORTABLE_ATTRIBUTES_SUMMARY_CLASS));
    }

    @Test
    final void testWithOnlyAttributeNoClassRequested() {
        final Query query = new Query("?query=test&presentation.summaryFields=" + FIELD2);
        Result result = execution.search(query);
        execution.fill(result, null);
        assertEquals(1, result.getConcreteHitCount());
        assertFalse(result.hits().get(0).isFilled(THIRD_OPTION));
        assertFalse(result.hits().get(0).isFilled(DEFAULT_SUMMARY_CLASS));
        assertTrue(result.hits().get(0).isFilled(SORTABLE_ATTRIBUTES_SUMMARY_CLASS));
    }

    @Test
    final void testWithOnlyDiskfieldNoClassRequested() {
        final Query query = new Query("?query=test&presentation.summaryFields=" + FIELD3);
        Result result = execution.search(query);
        execution.fill(result, null);
        assertEquals(1, result.getConcreteHitCount());
        assertFalse(result.hits().get(0).isFilled(THIRD_OPTION));
        assertTrue(result.hits().get(0).isFilled(DEFAULT_SUMMARY_CLASS));
        assertFalse(result.hits().get(0).isFilled(SORTABLE_ATTRIBUTES_SUMMARY_CLASS));
    }

    @Test
    final void testWithOnlyDiskfieldCorrectClassRequested() {
        final Query query = new Query("?query=test&presentation.summaryFields=" + FIELD3);
        Result result = execution.search(query);
        execution.fill(result, THIRD_OPTION);
        assertEquals(1, result.getConcreteHitCount());
        assertTrue(result.hits().get(0).isFilled(THIRD_OPTION));
        assertFalse(result.hits().get(0).isFilled(DEFAULT_SUMMARY_CLASS));
        assertFalse(result.hits().get(0).isFilled(SORTABLE_ATTRIBUTES_SUMMARY_CLASS));
    }

    @Test
    final void testTrivialCaseWithOnlyDiskfieldWrongClassRequested() {
        final Query query = new Query("?query=test&presentation.summaryFields=" + FIELD1);
        Result result = execution.search(query);
        execution.fill(result, THIRD_OPTION);
        assertEquals(1, result.getConcreteHitCount());
        assertTrue(result.hits().get(0).isFilled(THIRD_OPTION));
        assertTrue(result.hits().get(0).isFilled(DEFAULT_SUMMARY_CLASS));
        assertFalse(result.hits().get(0).isFilled(SORTABLE_ATTRIBUTES_SUMMARY_CLASS));
    }

}
