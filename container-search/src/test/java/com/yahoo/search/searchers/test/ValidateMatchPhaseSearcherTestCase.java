// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchers.ValidateMatchPhaseSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.vespa.config.search.AttributesConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author baldersheim
 */
public class ValidateMatchPhaseSearcherTestCase {

    private final ValidateMatchPhaseSearcher searcher;

    @SuppressWarnings("deprecation")
    public ValidateMatchPhaseSearcherTestCase() {
        searcher = new ValidateMatchPhaseSearcher(
                ConfigGetter.getConfig(AttributesConfig.class,
                                       "raw:" +
                                                     "attribute[4]\n" +
                                                     "attribute[0].name                ok\n" +
                                                     "attribute[0].datatype            INT32\n" +
                                                     "attribute[0].collectiontype      SINGLE\n" +
                                                     "attribute[0].fastsearch          true\n" +
                                                     "attribute[1].name                not_fast\n" +
                                                     "attribute[1].datatype            INT32\n" +
                                                     "attribute[1].collectiontype      SINGLE\n" +
                                                     "attribute[1].fastsearch          false\n" +
                                                     "attribute[2].name                not_numeric\n" +
                                                     "attribute[2].datatype            STRING\n" +
                                                     "attribute[2].collectiontype      SINGLE\n" +
                                                     "attribute[2].fastsearch          true\n" +
                                                     "attribute[3].name                not_single\n" +
                                                     "attribute[3].datatype            INT32\n" +
                                                     "attribute[3].collectiontype      ARRAY\n" +
                                                     "attribute[3].fastsearch          true"
        ));
    }

    private static String getErrorMatch(String attribute) {
        return "4: Invalid query parameter: The attribute '" +
                attribute +
                "' is not available for match-phase. It must be a single value numeric attribute with fast-search.";
    }

    private static String getErrorDiversity(String attribute) {
        return "4: Invalid query parameter: The attribute '" +
                attribute +
                "' is not available for match-phase diversification. It must be a single value numeric or string attribute.";
    }

    @Test
    void testMatchPhaseAttribute() {
        assertEquals("", search(""));
        assertEquals("", match("ok"));
        assertEquals(getErrorMatch("not_numeric"), match("not_numeric"));
        assertEquals(getErrorMatch("not_single"), match("not_single"));
        assertEquals(getErrorMatch("not_fast"), match("not_fast"));
        assertEquals(getErrorMatch("not_found"), match("not_found"));
    }

    @Test
    void testDiversityAttribute() {
        assertEquals("", search(""));
        assertEquals("", diversify("ok"));
        assertEquals("", diversify("not_numeric"));
        assertEquals(getErrorDiversity("not_single"), diversify("not_single"));
        assertEquals("", diversify("not_fast"));
        assertEquals(getErrorDiversity("not_found"), diversify("not_found"));
    }

    private String match(String m) {
        return search("&ranking.matchPhase.attribute=" + m);
    }

    private String diversify(String m) {
        return search("&ranking.matchPhase.attribute=ok&ranking.matchPhase.diversity.attribute=" + m);
    }

    private String search(String m) {
        String q = "/?query=sddocname:test" + m;
        Result r = doSearch(searcher, new Query(q), 0, 10);
        if (r.hits().getError() != null) {
            return r.hits().getError().toString();
        }
        return "";
    }

    private Result doSearch(Searcher searcher, Query query, int offset, int hits) {
        query.setOffset(offset);
        query.setHits(hits);
        return createExecution(searcher).search(query);
    }

    private Execution createExecution(Searcher searcher) {
        return new Execution(chainedAsSearchChain(searcher), Execution.Context.createContextStub());
    }

    private Chain<Searcher> chainedAsSearchChain(Searcher topOfChain) {
        List<Searcher> searchers = new ArrayList<>();
        searchers.add(topOfChain);
        return new Chain<>(searchers);
    }

}
