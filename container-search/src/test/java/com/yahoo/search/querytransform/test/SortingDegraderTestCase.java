// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.grouping.GroupingQueryParser;
import com.yahoo.search.query.properties.DefaultProperties;
import com.yahoo.search.querytransform.SortingDegrader;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bratseth
 */
public class SortingDegraderTestCase {

    @Test
    void testDegradingAscending() {
        Query query = new Query("?ranking.sorting=%2ba1%20-a2");
        execute(query);
        assertEquals("a1", query.getRanking().getMatchPhase().getAttribute());
        assertTrue(query.getRanking().getMatchPhase().getAscending());
        assertEquals(1400l, query.getRanking().getMatchPhase().getMaxHits().longValue());
        assertEquals(0.2, query.getRanking().getMatchPhase().getMaxFilterCoverage().doubleValue(), 1e-16);
    }

    @Test
    void testDegradingDescending() {
        Query query = new Query("?ranking.sorting=-a1%20-a2");
        execute(query);
        assertEquals("a1", query.getRanking().getMatchPhase().getAttribute());
        assertFalse(query.getRanking().getMatchPhase().getAscending());
        assertEquals(1400l, query.getRanking().getMatchPhase().getMaxHits().longValue());
    }

    @Test
    void testDegradingNonDefaultMaxHits() {
        Query query = new Query("?ranking.sorting=-a1%20-a2&ranking.matchPhase.maxHits=37");
        execute(query);
        assertEquals("a1", query.getRanking().getMatchPhase().getAttribute());
        assertFalse(query.getRanking().getMatchPhase().getAscending());
        assertEquals(37l, query.getRanking().getMatchPhase().getMaxHits().longValue());
    }

    @Test
    void testDegradingNonDefaultMaxFilterCoverage() {
        Query query = new Query("?ranking.sorting=-a1%20-a2&ranking.matchPhase.maxFilterCoverage=0.37");
        execute(query);
        assertEquals("a1", query.getRanking().getMatchPhase().getAttribute());
        assertFalse(query.getRanking().getMatchPhase().getAscending());
        assertEquals(0.37d, query.getRanking().getMatchPhase().getMaxFilterCoverage().doubleValue(), 1e-16);
    }

    @Test
    void testDegradingNonDefaultIllegalMaxFilterCoverage() {
        try {
            Query query = new Query("?ranking.sorting=-a1%20-a2&ranking.matchPhase.maxFilterCoverage=37");
            assertTrue(false);
        } catch (IllegalArgumentException qe) {
            assertTrue(qe instanceof IllegalArgumentException);
            assertEquals("Could not set 'ranking.matchPhase.maxFilterCoverage' to '37'", qe.getMessage());
            Throwable rootE = qe.getCause();
            assertTrue(rootE instanceof IllegalArgumentException);
            assertEquals("maxFilterCoverage must be in the range [0.0, 1.0]. It is 37.0", rootE.getMessage());
        }

    }

    @Test
    void testNoDegradingWhenGrouping() {
        Query query = new Query("?ranking.sorting=%2ba1%20-a2&select=all(group(a1)%20each(output(a1)))");
        execute(query);
        assertNull(query.getRanking().getMatchPhase().getAttribute());

    }

    @Test
    void testNoDegradingWhenNonFastSearchAttribute() {
        Query query = new Query("?ranking.sorting=%2bnonFastSearchAttribute%20-a2");
        execute(query);
        assertNull(query.getRanking().getMatchPhase().getAttribute());
    }

    @Test
    void testNoDegradingWhenNonNumericalAttribute() {
        Query query = new Query("?ranking.sorting=%2bstringAttribute%20-a2");
        execute(query);
        assertNull(query.getRanking().getMatchPhase().getAttribute());
    }

    @Test
    void testNoDegradingWhenTurnedOff() {
        Query query = new Query("?ranking.sorting=-a1%20-a2&sorting.degrading=false");
        execute(query);
        assertNull(query.getRanking().getMatchPhase().getAttribute());
    }

    @Test
    void testAccessAllDegradingParametersInQuery() {
        Query query = new Query("?ranking.matchPhase.maxHits=555&ranking.matchPhase.attribute=foo&ranking.matchPhase.ascending=true");
        execute(query);

        assertEquals("foo", query.getRanking().getMatchPhase().getAttribute());
        assertTrue(query.getRanking().getMatchPhase().getAscending());
        assertEquals(555l, query.getRanking().getMatchPhase().getMaxHits().longValue());

        assertEquals("foo", query.properties().get("ranking.matchPhase.attribute"));
        assertTrue(query.properties().getBoolean("ranking.matchPhase.ascending"));
        assertEquals(555l, query.properties().getLong("ranking.matchPhase.maxHits").longValue());
    }

    @Test
    void testDegradingWithLargeMaxHits() {
        Query query = new Query("?ranking.sorting=%2ba1%20-a2");
        query.properties().set(DefaultProperties.MAX_HITS, 13 * 1000);
        query.properties().set(DefaultProperties.MAX_OFFSET, 8 * 1000);
        execute(query);
        assertEquals("a1", query.getRanking().getMatchPhase().getAttribute());
        assertTrue(query.getRanking().getMatchPhase().getAscending());
        assertEquals(21000l, query.getRanking().getMatchPhase().getMaxHits().longValue());
    }

    @Test
    void testDegradingWithoutPaginationSupport() {
        Query query = new Query("?ranking.sorting=%2ba1%20-a2&hits=7&offset=1");
        query.properties().set(DefaultProperties.MAX_HITS, 13 * 1000);
        query.properties().set(DefaultProperties.MAX_OFFSET, 8 * 1000);
        query.properties().set(SortingDegrader.PAGINATION, "false");
        execute(query);
        assertEquals("a1", query.getRanking().getMatchPhase().getAttribute());
        assertTrue(query.getRanking().getMatchPhase().getAscending());
        assertEquals(8l, query.getRanking().getMatchPhase().getMaxHits().longValue());
    }

    private Result execute(Query query) {
        // Add the grouping parser to transfer the select parameter to a grouping expression
        Chain<Searcher> chain = new Chain<Searcher>(new GroupingQueryParser(), new SortingDegrader());
        return new Execution(chain, Execution.Context.createContextStub(createIndexFacts())).search(query);
    }

    private IndexFacts createIndexFacts() {
        SearchDefinition test = new SearchDefinition("test");

        Index fastSearchAttribute1 = new Index("a1");
        fastSearchAttribute1.setFastSearch(true);
        fastSearchAttribute1.setNumerical(true);

        Index fastSearchAttribute2 = new Index("a2");
        fastSearchAttribute2.setFastSearch(true);
        fastSearchAttribute2.setNumerical(true);

        Index nonFastSearchAttribute = new Index("nonFastSearchAttribute");
        nonFastSearchAttribute.setNumerical(true);

        Index stringAttribute = new Index("stringAttribute");
        stringAttribute.setFastSearch(true);

        test.addIndex(fastSearchAttribute1);
        test.addIndex(fastSearchAttribute2);
        test.addIndex(nonFastSearchAttribute);
        test.addIndex(stringAttribute);
        return new IndexFacts(new IndexModel(test));
    }

}
