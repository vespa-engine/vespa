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
    public void requireSameElementRewrittenForFastArrayOfStructField() {
        // The key and value are the struct fields given in the schema
        assertRewritten("myarray$keyvalue:foo" + FastMapSearch.keyValueSeparator() + "bar",
                        sameElement("myarray", new WordItem("foo", "mykey"), new WordItem("bar", "myvalue")));
        assertRewritten("myarray$keyvalue:foo" + FastMapSearch.keyValueSeparator() + "bar",
                        sameElement("myarray", new WordItem("bar", "myvalue"), new WordItem("foo", "mykey")));
        assertRewritten("intvaluearray$keyvalue:" + FastMapSearch.toKeyValue8Term("foo", 10),
                        sameElement("intvaluearray", new WordItem("foo", "mykey"), new IntItem("10", "myvalue")));
        assertRewritten("STRING_RANGE longvaluearray$keyvalue:[\"" + FastMapSearch.toKeyValue16Term("foo", 5L) + "\";\""
                        + FastMapSearch.toKeyValue16Term("foo", 10L) + "\"]",
                        sameElement("longvaluearray", new WordItem("foo", "mykey"), new IntItem("[5;10]", "myvalue")));

        // Terms on the map key and value names, or on other struct fields, are not rewritten
        assertUntouched(sameElement("myarray", new WordItem("foo", "key"), new WordItem("bar", "value")));
        assertUntouched(sameElement("myarray", new WordItem("foo", "mykey"), new WordItem("bar", "other")));

        // Untouched when the field does not have fast map search
        assertUntouched(sameElement("otherarray", new WordItem("foo", "mykey"), new WordItem("bar", "myvalue")));
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
    public void requireLongValueEncodedInExcessHex() {
        long beyondInt = 1L << 32;
        String expected = "longvaluemap$keyvalue:" + FastMapSearch.toKeyValue16Term("foo", beyondInt);

        // The value arrives as an IntItem
        assertRewritten(expected, sameElement("longvaluemap", new WordItem("foo", "key"), new IntItem(Long.toString(beyondInt), "value")));

        // The value arrives as a WordItem holding a long
        assertRewritten(expected, sameElement("longvaluemap", new WordItem("foo", "key"), new WordItem(Long.toString(beyondInt), "value")));

        // A value which fits in an int is still encoded with 16 digits
        assertRewritten("longvaluemap$keyvalue:" + FastMapSearch.toKeyValue16Term("foo", 10L),
                        sameElement("longvaluemap", new WordItem("foo", "key"), new IntItem("10", "value")));

        // Negative values encode across zero
        assertRewritten("longvaluemap$keyvalue:" + FastMapSearch.toKeyValue16Term("foo", -beyondInt),
                        sameElement("longvaluemap", new WordItem("foo", "key"), new IntItem(Long.toString(-beyondInt), "value")));
    }

    @Test
    public void requireFallbackWhenTermsDoNotMatchOneEntry() {
        // Not parseable as an int for an int-valued map
        assertUntouched(sameElement("intvaluemap", new WordItem("foo", "key"), new WordItem("bar", "value")));

        // Beyond the int range for an int-valued map
        assertUntouched(sameElement("intvaluemap", new WordItem("foo", "key"), new IntItem(Long.toString(1L << 32), "value")));

        // Not parseable as a long for a long-valued map
        assertUntouched(sameElement("longvaluemap", new WordItem("foo", "key"), new WordItem("bar", "value")));
        assertUntouched(sameElement("longvaluemap", new WordItem("foo", "key"), new IntItem("99999999999999999999", "value")));

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
        assertEquals(0, closed.getHitLimit());
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
    public void requireIntRangeWithHitLimitRewrittenToLexicalRange() {
        var searcher = new FastMapSearcher();

        var closed = searcher.makeIntRange("foo", new IntItem("[5;10;42]", "value"), "intvaluemap");
        assertEquals("intvaluemap$keyvalue", closed.getIndexName());
        assertEquals(FastMapSearch.toKeyValue8Term("foo", 5), closed.getFrom());
        assertEquals(FastMapSearch.toKeyValue8Term("foo", 10), closed.getTo());
        assertEquals(42, closed.getHitLimit());
        assertTrue(closed.isFromInclusive());
        assertTrue(closed.isToInclusive());
    }

    @Test
    public void requireFallbackForRangesWhichAreNotPlainIntRanges() {
        var searcher = new FastMapSearcher();

        // A fractional endpoint has no exact int encoding.
        assertNull(searcher.makeIntRange("foo", intRange(new Limit(1.5, true), new Limit(10, true)), "intvaluemap"));

        // A range on a string-valued map is not an IntItem, and is left alone.
        assertUntouched(sameElement("mymap", new WordItem("foo", "key"),
                                    new StringRangeItem("a", true, "z", true, "value", false, null)));
    }

    @Test
    public void requireLongRangeRewrittenToLexicalRange() {
        var searcher = new FastMapSearcher();
        long low = 1L << 32;
        long high = (1L << 32) + 100;

        // A closed range becomes a closed lexical range over the encoded endpoints.
        var closed = searcher.makeLongRange("foo", new IntItem("[" + low + ";" + high + "]", "value"), "longvaluemap");
        assertEquals("longvaluemap$keyvalue", closed.getIndexName());
        assertEquals(FastMapSearch.toKeyValue16Term("foo", low), closed.getFrom());
        assertEquals(FastMapSearch.toKeyValue16Term("foo", high), closed.getTo());
        assertTrue(closed.isFromInclusive());
        assertEquals(0, closed.getHitLimit());
        assertTrue(closed.isToInclusive());

        // Exclusive endpoints stay exclusive.
        var open = searcher.makeLongRange("foo", intRange(new Limit(low, false), new Limit(high, false)), "longvaluemap");
        assertFalse(open.isFromInclusive());
        assertFalse(open.isToInclusive());

        // An unbounded end becomes the extreme long value, so that the range cannot run past
        // this key into the entries of the neighbouring keys.
        var toInfinity = searcher.makeLongRange("foo", intRange(new Limit(low, true), Limit.POSITIVE_INFINITY), "longvaluemap");
        assertEquals(FastMapSearch.toKeyValue16Term("foo", low), toInfinity.getFrom());
        assertEquals(FastMapSearch.toKeyValue16Term("foo", Long.MAX_VALUE), toInfinity.getTo());
        assertTrue(toInfinity.isToInclusive());

        var fromInfinity = searcher.makeLongRange("foo", intRange(Limit.NEGATIVE_INFINITY, new Limit(high, true)), "longvaluemap");
        assertEquals(FastMapSearch.toKeyValue16Term("foo", Long.MIN_VALUE), fromInfinity.getFrom());
        assertEquals(FastMapSearch.toKeyValue16Term("foo", high), fromInfinity.getTo());
        assertTrue(fromInfinity.isFromInclusive());

        // Int endpoints, and integral double endpoints, are exact longs.
        var small = searcher.makeLongRange("foo", intRange(new Limit(5, true), new Limit(10.0, true)), "longvaluemap");
        assertEquals(FastMapSearch.toKeyValue16Term("foo", 5L), small.getFrom());
        assertEquals(FastMapSearch.toKeyValue16Term("foo", 10L), small.getTo());

        // Negative endpoints encode across zero and stay ordered.
        var negative = searcher.makeLongRange("foo", intRange(new Limit(-high, true), new Limit(-low, true)), "longvaluemap");
        assertTrue(negative.getFrom().compareTo(negative.getTo()) < 0);

        // End to end, through the searcher.
        String expected = "STRING_RANGE longvaluemap$keyvalue:[\"" + FastMapSearch.toKeyValue16Term("foo", low) + "\";\""
                          + FastMapSearch.toKeyValue16Term("foo", high) + "\"]";
        assertRewritten(expected, sameElement("longvaluemap", new WordItem("foo", "key"),
                                              new IntItem("[" + low + ";" + high + "]", "value")));
    }

    @Test
    public void requireLongRangeWithHitLimitRewrittenToLexicalRange() {
        var searcher = new FastMapSearcher();

        var closed = searcher.makeLongRange("foo", new IntItem("[5;10;42]", "value"), "longvaluemap");
        assertEquals("longvaluemap$keyvalue", closed.getIndexName());
        assertEquals(FastMapSearch.toKeyValue16Term("foo", 5), closed.getFrom());
        assertEquals(FastMapSearch.toKeyValue16Term("foo", 10), closed.getTo());
        assertTrue(closed.isFromInclusive());
        assertEquals(42, closed.getHitLimit());
        assertTrue(closed.isToInclusive());
    }

    @Test
    public void requireFallbackForRangesWhichAreNotPlainLongRanges() {
        var searcher = new FastMapSearcher();

        // A fractional endpoint has no exact long encoding.
        assertNull(searcher.makeLongRange("foo", intRange(new Limit(1.5, true), new Limit(10, true)), "longvaluemap"));

        // An endpoint beyond the long range has no encoding.
        assertNull(searcher.makeLongRange("foo", new IntItem("[0;99999999999999999999]", "value"), "longvaluemap"));
        assertNull(searcher.makeLongRange("foo", intRange(new Limit(0, true), new Limit(1e19, true)), "longvaluemap"));
    }

    @Test
    public void requireFloatValueEncodedInExcessHex() {
        String expected = "floatvaluemap$keyvalue:" + FastMapSearch.toKeyValueFloatTerm("foo", 1.5f);

        // The value arrives as an IntItem
        assertRewritten(expected, sameElement("floatvaluemap", new WordItem("foo", "key"), new IntItem("1.5", "value")));

        // The value arrives as a WordItem holding a number
        assertRewritten(expected, sameElement("floatvaluemap", new WordItem("foo", "key"), new WordItem("1.5", "value")));

        // An integral value is a float too
        assertRewritten("floatvaluemap$keyvalue:" + FastMapSearch.toKeyValueFloatTerm("foo", 10.0f),
                        sameElement("floatvaluemap", new WordItem("foo", "key"), new IntItem("10", "value")));

        // A value which is not exactly a float is rounded to the nearest one, like the backend does
        assertRewritten("floatvaluemap$keyvalue:" + FastMapSearch.toKeyValueFloatTerm("foo", 0.1f),
                        sameElement("floatvaluemap", new WordItem("foo", "key"), new IntItem("0.1", "value")));

        // Negative values encode across zero
        assertRewritten("floatvaluemap$keyvalue:" + FastMapSearch.toKeyValueFloatTerm("foo", -3.25f),
                        sameElement("floatvaluemap", new WordItem("foo", "key"), new WordItem("-3.25", "value")));
    }

    @Test
    public void requireDoubleValueEncodedInExcessHex() {
        String expected = "doublevaluemap$keyvalue:" + FastMapSearch.toKeyValueDoubleTerm("foo", 0.1);

        // The value arrives as an IntItem
        assertRewritten(expected, sameElement("doublevaluemap", new WordItem("foo", "key"), new IntItem("0.1", "value")));

        // The value arrives as a WordItem holding a number
        assertRewritten(expected, sameElement("doublevaluemap", new WordItem("foo", "key"), new WordItem("0.1", "value")));
        assertRewritten("doublevaluemap$keyvalue:" + FastMapSearch.toKeyValueDoubleTerm("foo", 1.5e300),
                        sameElement("doublevaluemap", new WordItem("foo", "key"), new WordItem("1.5e300", "value")));

        // Negative values encode across zero
        assertRewritten("doublevaluemap$keyvalue:" + FastMapSearch.toKeyValueDoubleTerm("foo", -3.0),
                        sameElement("doublevaluemap", new WordItem("foo", "key"), new IntItem("-3", "value")));
    }

    /** -0.0 and 0.0 are equal as numbers, but not as encoded strings, so a zero must match both. */
    @Test
    public void requireFloatingPointZeroMatchesBothZeros() {
        var searcher = new FastMapSearcher();
        for (boolean isFloat : new boolean[] { true, false }) {
            for (String zero : new String[] { "0", "0.0", "-0.0" }) {
                var range = (StringRangeItem) searcher.makeFloatingPointItem("foo", new IntItem(zero, "value"), "map", isFloat);
                assertEquals(floatingPointTerm("foo", -0.0, isFloat), range.getFrom());
                assertEquals(floatingPointTerm("foo", 0.0, isFloat), range.getTo());
                assertTrue(range.isFromInclusive());
                assertTrue(range.isToInclusive());
            }

            // An inclusive zero endpoint includes both zeros
            var closed = (StringRangeItem) searcher.makeFloatingPointItem("foo", new IntItem("[-1;0]", "value"), "map", isFloat);
            assertEquals(floatingPointTerm("foo", 0.0, isFloat), closed.getTo());
            closed = (StringRangeItem) searcher.makeFloatingPointItem("foo", new IntItem("[0;1]", "value"), "map", isFloat);
            assertEquals(floatingPointTerm("foo", -0.0, isFloat), closed.getFrom());

            // An exclusive zero endpoint excludes both zeros
            var open = (StringRangeItem) searcher.makeFloatingPointItem("foo", intRange(new Limit(-1.0, true), new Limit(0.0, false)), "map", isFloat);
            assertEquals(floatingPointTerm("foo", -0.0, isFloat), open.getTo());
            assertFalse(open.isToInclusive());
            open = (StringRangeItem) searcher.makeFloatingPointItem("foo", new IntItem(">0", "value"), "map", isFloat);
            assertEquals(floatingPointTerm("foo", 0.0, isFloat), open.getFrom());
            assertFalse(open.isFromInclusive());
        }
    }

    @Test
    public void requireFloatingPointRangeRewrittenToLexicalRange() {
        var searcher = new FastMapSearcher();

        // A closed range becomes a closed lexical range over the encoded endpoints.
        var closed = (StringRangeItem) searcher.makeFloatingPointItem("foo", new IntItem("[-1.5;2.5]", "value"), "floatvaluemap", true);
        assertEquals("floatvaluemap$keyvalue", closed.getIndexName());
        assertEquals(FastMapSearch.toKeyValueFloatTerm("foo", -1.5f), closed.getFrom());
        assertEquals(FastMapSearch.toKeyValueFloatTerm("foo", 2.5f), closed.getTo());
        assertEquals(0, closed.getHitLimit());
        assertTrue(closed.isFromInclusive());
        assertTrue(closed.isToInclusive());

        // Exclusive endpoints stay exclusive.
        var open = (StringRangeItem) searcher.makeFloatingPointItem("foo", intRange(new Limit(1.0, false), new Limit(2.0, false)), "floatvaluemap", true);
        assertEquals(FastMapSearch.toKeyValueFloatTerm("foo", 1.0f), open.getFrom());
        assertEquals(FastMapSearch.toKeyValueFloatTerm("foo", 2.0f), open.getTo());
        assertFalse(open.isFromInclusive());
        assertFalse(open.isToInclusive());

        // An unbounded end becomes the infinity in its direction, which the backend includes.
        var toInfinity = (StringRangeItem) searcher.makeFloatingPointItem("foo", new IntItem(">5", "value"), "doublevaluemap", false);
        assertEquals(FastMapSearch.toKeyValueDoubleTerm("foo", 5.0), toInfinity.getFrom());
        assertFalse(toInfinity.isFromInclusive());
        assertEquals(FastMapSearch.toKeyValueDoubleTerm("foo", Double.POSITIVE_INFINITY), toInfinity.getTo());
        assertTrue(toInfinity.isToInclusive());
        var fromInfinity = (StringRangeItem) searcher.makeFloatingPointItem("foo", new IntItem("<5", "value"), "floatvaluemap", true);
        assertEquals(FastMapSearch.toKeyValueFloatTerm("foo", Float.NEGATIVE_INFINITY), fromInfinity.getFrom());
        assertTrue(fromInfinity.isFromInclusive());
        assertEquals(FastMapSearch.toKeyValueFloatTerm("foo", 5.0f), fromInfinity.getTo());
        assertFalse(fromInfinity.isToInclusive());

        // End to end, through the searcher.
        String expected = "STRING_RANGE doublevaluemap$keyvalue:[\"" + FastMapSearch.toKeyValueDoubleTerm("foo", 0.5) + "\";\""
                          + FastMapSearch.toKeyValueDoubleTerm("foo", 10.0) + "\"]";
        assertRewritten(expected, sameElement("doublevaluemap", new WordItem("foo", "key"), new IntItem("[0.5;10]", "value")));
    }

    @Test
    public void requireFloatingPointRangeWithHitLimitRewrittenToLexicalRange() {
        var searcher = new FastMapSearcher();

        var closed = (StringRangeItem) searcher.makeFloatingPointItem("foo", new IntItem("[-1.5;2.5;42]", "value"), "floatvaluemap", true);
        assertEquals("floatvaluemap$keyvalue", closed.getIndexName());
        assertEquals(FastMapSearch.toKeyValueFloatTerm("foo", -1.5f), closed.getFrom());
        assertEquals(FastMapSearch.toKeyValueFloatTerm("foo", 2.5f), closed.getTo());
        assertEquals(42, closed.getHitLimit());
        assertTrue(closed.isFromInclusive());
        assertTrue(closed.isToInclusive());
    }

        @Test
    public void requireFallbackForUnsupportedFloatingPointTerms() {
        var searcher = new FastMapSearcher();

        // Not a number
        assertUntouched(sameElement("floatvaluemap", new WordItem("foo", "key"), new WordItem("bar", "value")));
        assertUntouched(sameElement("doublevaluemap", new WordItem("foo", "key"), new WordItem("NaN", "value")));
        assertNull(searcher.makeFloatingPointItem("foo", intRange(new Limit(Double.NaN, true), new Limit(1.0, true)), "doublevaluemap", false));

        // A prefix term matches more than one string
        assertUntouched(sameElement("doublevaluemap", new WordItem("foo", "key"), new PrefixItem("1", "value")));
    }

    private static String floatingPointTerm(String key, double value, boolean isFloat) {
        return isFloat ? FastMapSearch.toKeyValueFloatTerm(key, (float) value) : FastMapSearch.toKeyValueDoubleTerm(key, value);
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
                .add(new Field.Builder("longvaluemap", "map<string,long>").setFastMapSearch(true).build())
                .add(new Field.Builder("floatvaluemap", "map<string,float>").setFastMapSearch(true).build())
                .add(new Field.Builder("doublevaluemap", "map<string,double>").setFastMapSearch(true).build())
                .add(new Field.Builder("othermap", "map<string,string>").build())
                .add(new Field.Builder("myarray", "array<entry>").setFastMapSearch(arrayFields("string")).build())
                .add(new Field.Builder("intvaluearray", "array<intentry>").setFastMapSearch(arrayFields("int")).build())
                .add(new Field.Builder("longvaluearray", "array<longentry>").setFastMapSearch(arrayFields("long")).build())
                .add(new Field.Builder("otherarray", "array<entry>").build())
                .build();
        var schemaInfo = new SchemaInfo(List.of(schema), List.of());
        return new Execution(Execution.Context.createContextStub(schemaInfo));
    }

    private static Field.FastMapSearchFields arrayFields(String valueType) {
        return new Field.FastMapSearchFields("mykey", Field.Type.from("string"), "myvalue", Field.Type.from(valueType));
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
