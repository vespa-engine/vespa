// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.Limit;
import com.yahoo.prelude.query.PrefixItem;
import com.yahoo.prelude.query.SameElementItem;
import com.yahoo.prelude.query.StringRangeItem;
import com.yahoo.prelude.query.TermItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.schema.Field;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.searchlib.document.FastMapSearch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link FastMapSearcher}
 */
public class FastMapSearcherTest {

    @Test
    public void requireWordItemMadeCorrectly() {
        FastMapSearcher searcher = new FastMapSearcher();
        var word = searcher.makeWord("foo", "bar", "baz");

        var arr = word.getWord().toCharArray();
        assertEquals('f', arr[0]);
        assertEquals('o', arr[1]);
        assertEquals('o', arr[2]);
        assertEquals((char)0x7F, arr[3]);
        assertEquals('b', arr[4]);
        assertEquals('a', arr[5]);
        assertEquals('r', arr[6]);

        assertEquals("baz$keyvalue", word.getIndexName());
    }

    @Test
    public void requireSameElementRewrittenForFastMapField() {
        var execution = execution();

        String expectedWord = "mymap$keyvalue:foo" + FastMapSearch.keyValueSeparator() + "bar";

        // Rewritten at the root
        Query query = queryWith(sameElement("mymap"));
        new FastMapSearcher().search(query, execution);
        assertEquals(expectedWord, query.getModel().getQueryTree().getRoot().toString());

        // Rewritten below a composite
        AndItem and = new AndItem();
        and.addItem(sameElement("mymap"));
        and.addItem(new WordItem("other", "title"));
        query = queryWith(and);
        new FastMapSearcher().search(query, execution);
        assertEquals("AND " + expectedWord + " title:other",
                query.getModel().getQueryTree().getRoot().toString());

        // Untouched when the field does not have fast map search
        query = queryWith(sameElement("othermap"));
        new FastMapSearcher().search(query, execution);
        assertEquals("othermap:{key:foo value:bar}",
                query.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    public void requireIntValueEncodedInExcessHex() {
        String expected = "intvaluemap$keyvalue:" + FastMapSearch.toKeyValue8Term("foo", 10);

        // The value arrives as an IntItem
        assertRewritten(expected, sameElement("intvaluemap", new WordItem("foo", "key"), new IntItem("10", "value")));

        // The value arrives as a WordItem holding an int
        assertRewritten(expected, sameElement("intvaluemap", new WordItem("foo", "key"), new WordItem("10", "value")));

        // Negative values encode across zero
        assertRewritten("intvaluemap$keyvalue:" + FastMapSearch.toKeyValue8Term("foo", -3),
                        sameElement("intvaluemap", new WordItem("foo", "key"), new IntItem("-3", "value")));
    }

    @Test
    public void requireFallbackWhenTermsDoNotMatchOneEntry() {
        // Not parseable as an int for an int-valued map
        assertUntouched(sameElement("intvaluemap", new WordItem("foo", "key"), new WordItem("bar", "value")));

        // A prefix term matches more than one string
        assertUntouched(sameElement("mymap", new PrefixItem("fo", "key"), new WordItem("bar", "value")));

        // An element filter constrains matching beyond a term lookup
        SameElementItem filtered = sameElement("mymap");
        filtered.setElementFilter(List.of(1));
        assertUntouched(filtered);

        // Missing value term
        SameElementItem keyOnly = new SameElementItem("mymap");
        keyOnly.addItem(new WordItem("foo", "key"));
        assertUntouched(keyOnly);
    }

    @Test
    public void requireIntRangeRewrittenToLexicalRange() {
        var searcher = new FastMapSearcher();

        // A closed range becomes a closed lexical range over the encoded endpoints.
        var closed = searcher.makeIntRange("foo", new IntItem("[5;10]", "value"), "intvaluemap");
        assertEquals("intvaluemap$keyvalue", closed.getIndexName());
        assertEquals(FastMapSearch.toKeyValue8Term("foo", 5), closed.getFrom());
        assertEquals(FastMapSearch.toKeyValue8Term("foo", 10), closed.getTo());
        assertTrue(closed.isFromInclusive());
        assertTrue(closed.isToInclusive());

        // Exclusive endpoints stay exclusive.
        var open = searcher.makeIntRange("foo", intRange(new Limit(5, false), new Limit(10, false)), "intvaluemap");
        assertFalse(open.isFromInclusive());
        assertFalse(open.isToInclusive());

        // An unbounded end becomes the extreme int value, so that the range cannot run past
        // this key into the entries of the neighbouring keys.
        var toInfinity = searcher.makeIntRange("foo", intRange(new Limit(5, true), Limit.POSITIVE_INFINITY), "intvaluemap");
        assertEquals(FastMapSearch.toKeyValue8Term("foo", 5), toInfinity.getFrom());
        assertEquals(FastMapSearch.toKeyValue8Term("foo", Integer.MAX_VALUE), toInfinity.getTo());
        assertTrue(toInfinity.isToInclusive());

        var fromInfinity = searcher.makeIntRange("foo", intRange(Limit.NEGATIVE_INFINITY, new Limit(10, true)), "intvaluemap");
        assertEquals(FastMapSearch.toKeyValue8Term("foo", Integer.MIN_VALUE), fromInfinity.getFrom());
        assertEquals(FastMapSearch.toKeyValue8Term("foo", 10), fromInfinity.getTo());
        assertTrue(fromInfinity.isFromInclusive());

        // Negative endpoints encode across zero and stay ordered.
        var negative = searcher.makeIntRange("foo", intRange(new Limit(-10, true), new Limit(-5, true)), "intvaluemap");
        assertTrue(negative.getFrom().compareTo(negative.getTo()) < 0);

        // End to end, through the searcher.
        String expected = "STRING_RANGE intvaluemap$keyvalue:[\"" + FastMapSearch.toKeyValue8Term("foo", 5) + "\";\""
                          + FastMapSearch.toKeyValue8Term("foo", 10) + "\"]";
        assertRewritten(expected, sameElement("intvaluemap", new WordItem("foo", "key"), new IntItem("[5;10]", "value")));
    }

    @Test
    public void requireFallbackForRangesWhichAreNotPlainIntRanges() {
        var searcher = new FastMapSearcher();

        // A fractional endpoint has no exact int encoding.
        assertNull(searcher.makeIntRange("foo", intRange(new Limit(1.5, true), new Limit(10, true)), "intvaluemap"));

        // A hit limit counts entries in the value attribute, not in the synthetic one.
        assertNull(searcher.makeIntRange("foo", new IntItem("[5;10;100]", "value"), "intvaluemap"));

        // A range on a string-valued map is not an IntItem, and is left alone.
        assertUntouched(sameElement("mymap", new WordItem("foo", "key"),
                                    new StringRangeItem("a", true, "z", true, "value", false, null)));
    }

    private static IntItem intRange(Limit from, Limit to) {
        return new IntItem(from, to, "value");
    }

    private static void assertRewritten(String expected, SameElementItem sameElement) {
        Query query = queryWith(sameElement);
        new FastMapSearcher().search(query, execution());
        assertEquals(expected, query.getModel().getQueryTree().getRoot().toString());
    }

    private static void assertUntouched(SameElementItem sameElement) {
        String original = sameElement.toString();
        Query query = queryWith(sameElement);
        new FastMapSearcher().search(query, execution());
        assertEquals(original, query.getModel().getQueryTree().getRoot().toString());
    }

    private static Execution execution() {
        var schema = new Schema.Builder("test")
                .add(new Field.Builder("mymap", "map<string,string>").setFastMapSearch(true).build())
                .add(new Field.Builder("intvaluemap", "map<string,int>").setFastMapSearch(true).build())
                .add(new Field.Builder("intkeymap", "map<int,string>").setFastMapSearch(true).build())
                .add(new Field.Builder("othermap", "map<string,string>").build())
                .build();
        var schemaInfo = new SchemaInfo(List.of(schema), List.of());
        return new Execution(Execution.Context.createContextStub(schemaInfo));
    }

    private static SameElementItem sameElement(String field) {
        return sameElement(field, new WordItem("foo", "key"), new WordItem("bar", "value"));
    }

    private static SameElementItem sameElement(String field, TermItem key, TermItem value) {
        SameElementItem sameElement = new SameElementItem(field);
        sameElement.addItem(key);
        sameElement.addItem(value);
        return sameElement;
    }

    private static Query queryWith(Item root) {
        Query query = new Query();
        query.getModel().getQueryTree().setRoot(root);
        return query;
    }

}
