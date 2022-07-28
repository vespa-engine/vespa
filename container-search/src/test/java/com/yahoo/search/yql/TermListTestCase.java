// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.yahoo.component.chain.Chain;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.searchchain.Execution;
import org.apache.http.client.utils.URIBuilder;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests YQL expressions where a list of terms are supplied by indirection
 *
 * @author bratseth
 */
public class TermListTestCase {

    @Test
    void testTermListInWeightedSet() {
        URIBuilder builder = searchUri();
        builder.setParameter("myTerms", "{'1':1, '2':1, 3:1}");
        builder.setParameter("yql", "select * from sources * where weightedSet(user_id, @myTerms)");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where weightedSet(user_id, {\"1\": 1, \"2\": 1, \"3\": 1})",
                query.yqlRepresentation());
    }

    @Test
    void testTermListInWand() {
        URIBuilder builder = searchUri();
        builder.setParameter("myTerms", "{'1':1, 2:1, '3':1}");
        builder.setParameter("yql", "select * from sources * where wand(user_id, @myTerms)");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where wand(user_id, {\"1\": 1, \"2\": 1, \"3\": 1})",
                query.yqlRepresentation());
    }

    @Test
    void testTermListInDotProduct() {
        URIBuilder builder = searchUri();
        builder.setParameter("myTerms", "{'1':1, '2':1, '3':1}");
        builder.setParameter("yql", "select * from sources * where dotProduct(user_id, @myTerms)");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where dotProduct(user_id, {\"1\": 1, \"2\": 1, \"3\": 1})",
                query.yqlRepresentation());
    }

    private Query searchAndAssertNoErrors(URIBuilder builder) {
        Query query = new Query(builder.toString());
        var searchChain = new Chain<>(new MinimalQueryInserter());
        var context = Execution.Context.createContextStub();
        var execution = new Execution(searchChain, context);
        Result r = execution.search(query);
        var exception = exceptionOf(r);
        assertNull(r.hits().getError(),
                   exception == null ? "No error":
                   exception.getMessage() + "\n" + Arrays.toString(exception.getStackTrace()));
        return query;
    }

    private Throwable exceptionOf(Result r) {
        if (r.hits().getError() == null) return null;
        if (r.hits().getError().getCause() == null) return null;
        return r.hits().getError().getCause();
    }

    private URIBuilder searchUri() {
        URIBuilder builder = new URIBuilder();
        builder.setPath("search/");
        return builder;
    }

}
