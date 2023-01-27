// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.yahoo.component.chain.Chain;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.testutil.DocumentSourceSearcher;

import java.util.Set;

/**
 * Smoketest that we remove fields in a sane manner.
 *
 * @author Steinar Knutsen
 */
public class FieldFilterTestCase {

    private static final String FIELD_C = "c";
    private static final String FIELD_B = "b";
    private static final String FIELD_A = "a";
    private static final Set<String> syntheticFields = Set.of("matchfeatures", "rankfeatures", "summaryfeatures");
    private Chain<Searcher> searchChain;
    private Execution.Context context;
    private Execution execution;

    @BeforeEach
    public void setUp() throws Exception {
        Query query = new Query("?query=test");

        Result result = new Result(query);
        result.hits().add(createHit("hit1", .1d, false, FIELD_A, FIELD_B, FIELD_C));
        result.hits().add(createHit("hit2", .1d, true, FIELD_A, FIELD_B, FIELD_C));

        DocumentSourceSearcher mockBackend = new DocumentSourceSearcher();
        mockBackend.addResult(query, result);

        searchChain = new Chain<>(new FieldFilter(), mockBackend);
        context = Execution.Context.createContextStub();
        execution = new Execution(searchChain, context);

    }

    private Hit createHit(String id, double relevancy, boolean addSyntheticFields, String... fieldNames) {
        Hit h = new Hit(id, relevancy);
        h.setFillable();
        int i = 0;
        for (String field : fieldNames)
            h.setField(field, ++i);
        if (addSyntheticFields) {
            for (String field : syntheticFields)
                h.setField(field, ++i);
        }
        return h;
    }

    @AfterEach
    public void tearDown() throws Exception {
        searchChain = null;
        context = null;
        execution = null;
    }

    @Test
    void testBasic() {
        Query query = new Query("?query=test&presentation.summaryFields=" + FIELD_B);
        Result result = execution.search(query);
        execution.fill(result);
        assertEquals(2, result.getConcreteHitCount());
        assertEquals(Set.of(FIELD_B), result.hits().get(0).fieldKeys());
        assertEquals(Set.of(FIELD_B, "matchfeatures", "rankfeatures", "summaryfeatures"),
                     result.hits().get(1).fieldKeys());
    }

    @Test
    void testNoFiltering() {
        Query query = new Query("?query=test");
        Result result = execution.search(query);
        execution.fill(result);
        assertEquals(2, result.getConcreteHitCount());
        assertEquals(Set.of(FIELD_A, FIELD_B, FIELD_C), result.hits().get(0).fieldKeys());
        assertEquals(Set.of(FIELD_A, FIELD_B, FIELD_C, "matchfeatures", "rankfeatures", "summaryfeatures"),
                     result.hits().get(1).fieldKeys());
    }

}
