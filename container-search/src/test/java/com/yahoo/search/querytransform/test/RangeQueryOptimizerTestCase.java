// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.search.Query;
import com.yahoo.search.Searcher;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.Parser;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.query.parser.ParserFactory;
import com.yahoo.search.querytransform.RangeQueryOptimizer;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bratseth
 */
public class RangeQueryOptimizerTestCase {

    private static final Linguistics linguistics = new SimpleLinguistics();
    private static IndexFacts indexFacts = createIndexFacts();

    @Test
    void testRangeOptimizing() {
        assertOptimized("s:<15", "s:<15");
        assertOptimized("AND a s:[1999;2002]", "a AND s:[1999;2002]");
        assertOptimized("AND s:<10;15>", "s:<15 AND s:>10");
        assertOptimized("AND s:give s:5 s:me", "s:give s:5 s:me");
        assertOptimized("AND s:[;15> b:<10;]", "s:<15 AND b:>10");
        assertOptimized("AND s:<10;15> b:[;20>", "s:<15 AND b:<20 AND s:>10");
        assertOptimized("AND c:foo s:<10;15> b:<35;40>", "s:<15 AND s:>10 b:>35 AND c:foo b:<40");
        assertOptimized("AND s:<12;15>", "s:<15 AND s:>10 AND s:>12");
        assertOptimized("Nonoverlapping ranges: Cannot match", "AND s:13 s:4 FALSE", "s:<15 AND s:>10 AND s:>100 AND s:13 AND s:<110 AND s:4");
        assertOptimized("Multivalue ranges are not optimized", "AND m:<15 m:>10", "m:<15 AND m:>10");
        assertOptimized("AND s:[13;15>", "s:<15 AND s:[13;17]");
        assertOptimized("AND s:[13;15>", "s:<15 AND s:[13;15]");
        assertOptimized("AND s:[13;15>", "s:[13;15] AND s:<15");
        assertOptimized("AND s:13 s:4 m:<100 s:[13;15> t:<101;109>", "s:<15 AND s:>10 AND t:>100 AND s:13 AND t:<110 AND s:4 AND t:>101 AND t:<111 AND t:<109 AND m:<100 AND s:[13;17]");
        assertOptimized("AND (AND s:<10;15>) (AND s:<22;27>)", "(s:<15 AND s:>10) AND (s:<27 AND s:>22 AND s:>20");
        assertOptimized("AND (AND s:<10;15.5>) (AND s:<22;27.37>)", "(s:<15.5 AND s:>10) AND (s:<27.37 AND s:>22 AND s:>20");
        assertOptimized("AND FALSE", "s:<2 AND s:>2");
        assertOptimized("AND FALSE", "s:>2 AND s:<2");
        assertOptimized("AND s:2", "s:[;2] AND s:[2;]");
        assertOptimized("AND s:2", "s:[2;] AND s:[;2]");
    }

    @Test
    void testRangeOptimizingCarriesOverItemAttributesWhenNotOptimized() {
        Query query = new Query();
        AndItem root = new AndItem();
        query.getModel().getQueryTree().setRoot(root);
        Item intItem = new IntItem(">" + 15, "s");
        intItem.setWeight(500);
        intItem.setFilter(true);
        intItem.setRanked(false);
        root.addItem(intItem);
        assertOptimized("Not optimized", "AND |s:<15;]!500", query);
        IntItem transformedIntItem = (IntItem) ((AndItem) query.getModel().getQueryTree().getRoot()).getItem(0);
        assertTrue(transformedIntItem.isFilter(), "Filter was carried over");
        assertFalse(transformedIntItem.isRanked(), "Ranked was carried over");
        assertEquals(500, transformedIntItem.getWeight(), "Weight was carried over");
    }

    @Test
    void testRangeOptimizingCarriesOverItemAttributesWhenOptimized() {
        Query query = new Query();
        AndItem root = new AndItem();
        query.getModel().getQueryTree().setRoot(root);

        Item intItem1 = new IntItem(">" + 15, "s");
        intItem1.setFilter(true);
        intItem1.setRanked(false);
        intItem1.setWeight(500);
        root.addItem(intItem1);

        Item intItem2 = new IntItem("<" + 30, "s");
        intItem2.setFilter(true);
        intItem2.setRanked(false);
        intItem2.setWeight(500);
        root.addItem(intItem2);

        assertOptimized("Optimized", "AND |s:<15;30>!500", query);
        IntItem transformedIntItem = (IntItem) ((AndItem) query.getModel().getQueryTree().getRoot()).getItem(0);
        assertTrue(transformedIntItem.isFilter(), "Filter was carried over");
        assertFalse(transformedIntItem.isRanked(), "Ranked was carried over");
        assertEquals(500, transformedIntItem.getWeight(), "Weight was carried over");
    }

    @Test
    void testNoRangeOptimizingWhenAttributesAreIncompatible() {
        Query query = new Query();
        AndItem root = new AndItem();
        query.getModel().getQueryTree().setRoot(root);

        Item intItem1 = new IntItem(">" + 15, "s");
        intItem1.setFilter(true);
        intItem1.setRanked(false);
        intItem1.setWeight(500);
        root.addItem(intItem1);

        Item intItem2 = new IntItem("<" + 30, "s");
        intItem2.setFilter(false); // Disagrees with item1
        intItem2.setRanked(false);
        intItem2.setWeight(500);
        root.addItem(intItem2);

        assertOptimized("Not optimized", "AND |s:<15;]!500 s:[;30>!500", query);

        IntItem transformedIntItem1 = (IntItem) ((AndItem) query.getModel().getQueryTree().getRoot()).getItem(0);
        assertTrue(transformedIntItem1.isFilter(), "Filter was carried over");
        assertFalse(transformedIntItem1.isRanked(), "Ranked was carried over");
        assertEquals(500, transformedIntItem1.getWeight(), "Weight was carried over");

        IntItem transformedIntItem2 = (IntItem) ((AndItem) query.getModel().getQueryTree().getRoot()).getItem(1);
        assertFalse(transformedIntItem2.isFilter(), "Filter was carried over");
        assertFalse(transformedIntItem2.isRanked(), "Ranked was carried over");
        assertEquals(500, transformedIntItem2.getWeight(), "Weight was carried over");
    }

    @Test
    void testDifferentCompatibleRangesPerFieldAreOptimizedSeparately() {
        Query query = new Query();
        AndItem root = new AndItem();
        query.getModel().getQueryTree().setRoot(root);

        // Two internally compatible items
        Item intItem1 = new IntItem(">" + 15, "s");
        intItem1.setRanked(false);
        root.addItem(intItem1);

        Item intItem2 = new IntItem("<" + 30, "s");
        intItem2.setRanked(false);
        root.addItem(intItem2);

        // Two other internally compatible items incompatible with the above
        Item intItem3 = new IntItem(">" + 100, "s");
        root.addItem(intItem3);

        Item intItem4 = new IntItem("<" + 150, "s");
        root.addItem(intItem4);

        assertOptimized("Optimized", "AND s:<15;30> s:<100;150>", query);

        IntItem transformedIntItem1 = (IntItem) ((AndItem) query.getModel().getQueryTree().getRoot()).getItem(0);
        assertFalse(transformedIntItem1.isRanked(), "Ranked was carried over");

        IntItem transformedIntItem2 = (IntItem) ((AndItem) query.getModel().getQueryTree().getRoot()).getItem(1);
        assertTrue(transformedIntItem2.isRanked(), "Ranked was carried over");
    }

    @Test
    void assertOptmimizedYQLQuery() {
        Query query = new Query("/?query=select%20%2A%20from%20sources%20%2A%20where%20%28range%28s%2C%20100000%2C%20100000%29%20OR%20range%28t%2C%20-20000000000L%2C%20-20000000000L%29%20OR%20range%28t%2C%2030%2C%2030%29%29%3B&type=yql");
        assertOptimized("YQL usage of the IntItem API works", "OR s:100000 t:-20000000000 t:30", query);
    }

    @Test
    void testTracing() {
        Query notOptimized = new Query("/?tracelevel=2");
        notOptimized.getModel().getQueryTree().setRoot(parseQuery("s:<15"));
        assertOptimized("", "s:<15", notOptimized);
        assertFalse(contains("Optimized query ranges", notOptimized.getContext(true).getTrace().traceNode().descendants(String.class)));

        Query optimized = new Query("/?tracelevel=2");
        optimized.getModel().getQueryTree().setRoot(parseQuery("s:<15 AND s:>10"));
        assertOptimized("", "AND s:<10;15>", optimized);
        assertTrue(contains("Optimized query ranges", optimized.getContext(true).getTrace().traceNode().descendants(String.class)));
    }

    private boolean contains(String prefix, Iterable<String> traceEntries) {
        for (String traceEntry : traceEntries)
            if (traceEntry.startsWith(prefix)) return true;
        return false;
    }

    private Query assertOptimized(String expected, String queryString) {
        return assertOptimized(null, expected, queryString);
    }

    private Query assertOptimized(String explanation, String expected, String queryString) {
        Query query = new Query();
        query.getModel().getQueryTree().setRoot(parseQuery(queryString));
        return assertOptimized(explanation, expected, query);
    }

    private Query assertOptimized(String explanation, String expected, Query query) {
        Chain<Searcher> chain = new Chain<>("test", new RangeQueryOptimizer());
        new Execution(chain, Execution.Context.createContextStub(indexFacts)).search(query);
        assertEquals(expected, query.getModel().getQueryTree().getRoot().toString(), explanation);
        return query;
    }

    private Item parseQuery(String query) {
        IndexFacts indexFacts = new IndexFacts();
        Parser parser = ParserFactory.newInstance(Query.Type.ADVANCED, new ParserEnvironment()
                .setIndexFacts(indexFacts)
                .setLinguistics(linguistics));
        return parser.parse(new Parsable().setQuery(query)).getRoot();
    }

    private static IndexFacts createIndexFacts() {
        SearchDefinition sd = new SearchDefinition("test");
        Index singleValue1 = new Index("s");
        Index singleValue2 = new Index("t");
        Index multiValue = new Index("m");
        multiValue.setMultivalue(true);
        sd.addIndex(singleValue1);
        sd.addIndex(singleValue2);
        sd.addIndex(multiValue);
        return new IndexFacts(new IndexModel(sd));
    }

}
