// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.component.chain.Chain;
import com.yahoo.prelude.query.PredicateQueryItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.assertEquals;

/**
 * Test BooleanSearcher
 */
public class BooleanSearcherTestCase {

    private Execution exec;

    private Execution buildExec() {
        return new Execution(new Chain<Searcher>(new BooleanSearcher()),
                Execution.Context.createContextStub());
    }

    @Before
    public void setUp() throws Exception {
        exec = buildExec();
    }

    @Test
    public void requireThatAttributeMapToSingleFeature() {
        PredicateQueryItem item = buildPredicateQueryItem("{gender:female}", null);
        assertEquals(1, item.getFeatures().size());
        assertEquals(0, item.getRangeFeatures().size());
        assertEquals("gender", item.getFeatures().iterator().next().getKey());
        assertEquals("female", item.getFeatures().iterator().next().getValue());
        assertEquals("PREDICATE_QUERY_ITEM gender=female", item.toString());
    }

    @Test
    public void requireThatAttributeListMapToMultipleFeatures() {
        PredicateQueryItem item = buildPredicateQueryItem("{gender:[female,male]}", null);
        assertEquals(2, item.getFeatures().size());
        assertEquals(0, item.getRangeFeatures().size());
        assertEquals("PREDICATE_QUERY_ITEM gender=female, gender=male", item.toString());
    }

    @Test
    public void requireThatRangeAttributesMapToRangeTerm() {
        PredicateQueryItem item = buildPredicateQueryItem(null, "{age:25}");
        assertEquals(0, item.getFeatures().size());
        assertEquals(1, item.getRangeFeatures().size());
        assertEquals("PREDICATE_QUERY_ITEM age:25", item.toString());

        item = buildPredicateQueryItem(null, "{age:25:0x43, height:170:[2,3,4]}");
        assertEquals(0, item.getFeatures().size());
        assertEquals(2, item.getRangeFeatures().size());
    }

    @Test
    public void requireThatQueryWithoutBooleanPropertiesIsUnchanged() {
        Query q = new Query("");
        q.getModel().getQueryTree().setRoot(new WordItem("foo", "otherfield"));
        Result r = exec.search(q);

        WordItem root = (WordItem)r.getQuery().getModel().getQueryTree().getRoot();
        assertEquals("foo", root.getWord());
    }

    @Test
    public void requireThatBooleanSearcherCanBuildPredicateQueryItem() {
        PredicateQueryItem root = buildPredicateQueryItem("{gender:female}", "{age:23:[2, 3, 5]}");

        Collection<PredicateQueryItem.Entry> features = root.getFeatures();
        assertEquals(1, features.size());
        PredicateQueryItem.Entry entry = (PredicateQueryItem.Entry) features.toArray()[0];
        assertEquals("gender", entry.getKey());
        assertEquals("female", entry.getValue());
        assertEquals(-1L, entry.getSubQueryBitmap());

        Collection<PredicateQueryItem.RangeEntry> rangeFeatures = root.getRangeFeatures();
        assertEquals(1, rangeFeatures.size());
        PredicateQueryItem.RangeEntry rangeEntry = (PredicateQueryItem.RangeEntry) rangeFeatures.toArray()[0];
        assertEquals("age", rangeEntry.getKey());
        assertEquals(23L, rangeEntry.getValue());
        assertEquals(44L, rangeEntry.getSubQueryBitmap());
    }

    @Test
    public void requireThatKeysAndValuesCanContainSpaces() {
        PredicateQueryItem item = buildPredicateQueryItem("{'My Key':'My Value'}", null);
        assertEquals(1, item.getFeatures().size());
        assertEquals(0, item.getRangeFeatures().size());
        assertEquals("My Key", item.getFeatures().iterator().next().getKey());
        assertEquals("'My Value'", item.getFeatures().iterator().next().getValue());
        assertEquals("PREDICATE_QUERY_ITEM My Key='My Value'", item.toString());
    }

    private PredicateQueryItem buildPredicateQueryItem(String attributes, String rangeAttributes) {
        Query q = buildQuery("predicate", attributes, rangeAttributes);
        Result r = exec.search(q);
        return (PredicateQueryItem)r.getQuery().getModel().getQueryTree().getRoot();
    }

    private Query buildQuery(String field, String attributes, String rangeAttributes) {
        Query q = new Query("");
        q.properties().set("boolean.field", field);
        if (attributes != null) {
            q.properties().set("boolean.attributes", attributes);
        }
        if (rangeAttributes != null) {
            q.properties().set("boolean.rangeAttributes", rangeAttributes);
        }
        return q;
    }

}
