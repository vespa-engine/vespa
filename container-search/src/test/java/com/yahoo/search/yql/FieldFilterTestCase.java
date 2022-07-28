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

/**
 * Smoketest that we remove fields in a sane manner.
 *
 * @author Steinar Knutsen
 */
public class FieldFilterTestCase {

    private static final String FIELD_C = "c";
    private static final String FIELD_B = "b";
    private static final String FIELD_A = "a";
    private Chain<Searcher> searchChain;
    private Execution.Context context;
    private Execution execution;

    @BeforeEach
    public void setUp() throws Exception {
        Query query = new Query("?query=test");

        Result result = new Result(query);
        Hit hit = createHit("lastHit", .1d, FIELD_A, FIELD_B, FIELD_C);
        result.hits().add(hit);

        DocumentSourceSearcher mockBackend = new DocumentSourceSearcher();
        mockBackend.addResult(query, result);

        searchChain = new Chain<>(new FieldFilter(), mockBackend);
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

    @AfterEach
    public void tearDown() throws Exception {
        searchChain = null;
        context = null;
        execution = null;
    }

    @Test
    final void testBasic() {
        final Query query = new Query("?query=test&presentation.summaryFields=" + FIELD_B);
        Result result = execution.search(query);
        execution.fill(result);
        assertEquals(1, result.getConcreteHitCount());
        assertFalse(result.hits().get(0).fieldKeys().contains(FIELD_A));
        assertTrue(result.hits().get(0).fieldKeys().contains(FIELD_B));
        assertFalse(result.hits().get(0).fieldKeys().contains(FIELD_C));
    }

    @Test
    final void testNoFiltering() {
        final Query query = new Query("?query=test");
        Result result = execution.search(query);
        execution.fill(result);
        assertEquals(1, result.getConcreteHitCount());
        assertTrue(result.hits().get(0).fieldKeys().contains(FIELD_A));
        assertTrue(result.hits().get(0).fieldKeys().contains(FIELD_B));
        assertTrue(result.hits().get(0).fieldKeys().contains(FIELD_C));
    }

}
