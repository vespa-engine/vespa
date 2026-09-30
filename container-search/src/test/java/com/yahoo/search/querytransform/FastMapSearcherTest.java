// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.component.chain.Chain;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.ExactStringItem;
import com.yahoo.prelude.query.FuzzyItem;
import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.Limit;
import com.yahoo.prelude.query.MapMatchItem;
import com.yahoo.prelude.query.PrefixItem;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.RegExpItem;
import com.yahoo.prelude.query.SameElementItem;
import com.yahoo.prelude.query.StringRangeItem;
import com.yahoo.prelude.query.TermItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.schema.Field;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.yql.MinimalQueryInserter;
import com.yahoo.searchlib.document.FastMapSearch;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link FastMapSearcher}
 */
public class FastMapSearcherTest {

    @Test
    public void requireWordItemMadeCorrectly() {
        FastMapSearcher searcher = new FastMapSearcher();
        var word = searcher.makeWord("foo", "bar", "baz$lookup");

        var arr = word.getWord().toCharArray();
        assertEquals('f', arr[0]);
        assertEquals('o', arr[1]);
        assertEquals('o', arr[2]);
        assertEquals((char)0x7F, arr[3]);
        assertEquals('b', arr[4]);
        assertEquals('a', arr[5]);
        assertEquals('r', arr[6]);

        assertEquals("baz$lookup", word.getIndexName());
    }

    @Test
    public void requireSearcherInactiveWhenNoSchemaHasFastMapLookups() {
        var withoutLookups = new Schema.Builder("plain")
                .add(new Field.Builder("mymap", "map<string,string>").build())
                .build();
        var searcher = new FastMapSearcher(new SchemaInfo(List.of(withoutLookups), List.of()));

        // Neither rewritten nor rejected, even if the query is executed with schemas which have lookups
        var mapMatch = mapMatch("mymap");
        String original = mapMatch.toString();
        Query query = queryWith(mapMatch);
        searcher.search(query, execution());
        assertEquals(original, query.getModel().getQueryTree().getRoot().toString());

        query = queryWith(new WordItem("foo", "mymap.lookup"));
        searcher.search(query, execution());
        assertEquals("mymap.lookup:foo", query.getModel().getQueryTree().getRoot().toString());

        // Also inactive with no schemas at all
        query = queryWith(mapMatch("mymap"));
        new FastMapSearcher(SchemaInfo.empty()).search(query, execution());
        assertEquals(original, query.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    public void requireSearcherActiveWhenAnySchemaHasFastMapLookups() {
        var withoutLookups = new Schema.Builder("plain")
                .add(new Field.Builder("othermap", "map<string,string>").build())
                .build();
        var searcher = new FastMapSearcher(new SchemaInfo(List.of(withoutLookups, schemaInfo().schemas().get("test")), List.of()));

        Query query = queryWith(mapMatch("mymap"));
        searcher.search(query, execution());
        assertEquals("mymap$lookup:foo" + FastMapSearch.keyValueSeparator() + "bar",
                     query.getModel().getQueryTree().getRoot().toString());

        assertThrows(IllegalInputException.class,
                     () -> searcher.search(queryWith(new WordItem("foo", "mymap.lookup")), execution()));
    }

    @Test
    public void requireMapLookupRewrittenForFastMapField() {
        var execution = execution();

        String expectedWord = "mymap$lookup:foo" + FastMapSearch.keyValueSeparator() + "bar";

        // Rewritten at the root
        Query query = queryWith(mapMatch("mymap"));
        new FastMapSearcher().search(query, execution);
        assertEquals(expectedWord, query.getModel().getQueryTree().getRoot().toString());

        // Rewritten below a composite
        AndItem and = new AndItem();
        and.addItem(mapMatch("mymap"));
        and.addItem(new WordItem("other", "title"));
        query = queryWith(and);
        new FastMapSearcher().search(query, execution);
        assertEquals("AND " + expectedWord + " title:other",
                query.getModel().getQueryTree().getRoot().toString());

        // Untouched when the field does not have fast map search
        assertUntouched(mapMatch("othermap"));

        // Untouched when the lookup field is not named, or is not the lookup field of the map
        assertUntouched(new MapMatchItem("mymap", new WordItem("foo", "key"), new WordItem("bar", "value")));
        assertUntouched(new MapMatchItem("mymap.other", new WordItem("foo", "key"), new WordItem("bar", "value")));
        assertUntouched(new MapMatchItem("nosuchmap.lookup", new WordItem("foo", "key"), new WordItem("bar", "value")));

        // Untouched when not a map lookup
        assertUntouched(sameElement("mymap"));
    }

    @Test
    public void requireYqlMapLookupOnLookupFieldRewritten() {
        assertEquals("mymap$lookup:foo" + FastMapSearch.keyValueSeparator() + "bar",
                     rewrittenYql("mymap.lookup{\"foo\"} contains \"bar\""));
        assertEquals("intvaluemap$lookup:" + FastMapSearch.toKeyValue8Term("foo", 42),
                     rewrittenYql("intvaluemap.lookup{\"foo\"} = 42"));
        assertEquals("STRING_RANGE intvaluemap$lookup:[\"" + FastMapSearch.toKeyValue8Term("foo", 5) + "\";\""
                     + FastMapSearch.toKeyValue8Term("foo", 10) + "\"]",
                     rewrittenYql("range(intvaluemap.lookup{\"foo\"}, 5, 10)"));
        assertEquals("myarray$lookup:foo" + FastMapSearch.keyValueSeparator() + "bar",
                     rewrittenYql("myarray.lookup{\"foo\"} contains \"bar\""));

        // Without the lookup field name, the map lookup is a regular sameElement on the map
        assertEquals("mymap:{key:foo value:bar}", rewrittenYql("mymap{\"foo\"} contains \"bar\""));
    }

    /**
     * With index facts, as in a deployment, the parser requires the lookup field and its key and value
     * to be known indexes. They are added to the index info by the config model.
     */
    @Test
    public void requireYqlMapLookupOnLookupFieldParsedWithIndexFacts() {
        var sd = new SearchDefinition("test");
        for (String index : List.of("mymap", "mymap.key", "mymap.value"))
            sd.addCommand(index, "attribute");
        var withoutLookup = new IndexFacts(new IndexModel(sd));
        var result = searchYql("mymap.lookup{\"foo\"} contains \"bar\"", withoutLookup);
        assertEquals("Could not create query from YQL: Field 'mymap.lookup' does not exist.",
                     result.hits().getError().getDetailedMessage());

        for (String index : List.of("mymap.lookup", "mymap.lookup.key", "mymap.lookup.value"))
            sd.addCommand(index, "attribute");
        var withLookup = new IndexFacts(new IndexModel(sd));
        result = searchYql("mymap.lookup{\"foo\"} contains \"bar\"", withLookup);
        assertNull(result.hits().getError());
        assertEquals("mymap$lookup:foo" + FastMapSearch.keyValueSeparator() + "bar",
                     result.getQuery().getModel().getQueryTree().getRoot().toString());
    }

    /**
     * A string key or value has the matching settings of the lookup attribute in the index info,
     * so that the parser keeps it as one term, as it is matched as a whole.
     */
    @Test
    public void requireMultiwordAndPunctuatedKeysAndValuesKeptAsOneTerm() {
        var sd = new SearchDefinition("test");
        for (String map : List.of("mymap", "intvaluemap")) {
            sd.addCommand(map, "multivalue");
            sd.addCommand(map + ".lookup", "multivalue");
            for (String command : List.of("lowercase", "multivalue", "attribute", "fast-search", "string", "word", "type string"))
                sd.addCommand(map + ".lookup.key", command);
        }
        for (String command : List.of("lowercase", "multivalue", "attribute", "fast-search", "string", "word", "type string"))
            sd.addCommand("mymap.lookup.value", command);
        for (String command : List.of("multivalue", "numerical", "integer", "type int"))
            sd.addCommand("intvaluemap.lookup.value", command);
        var indexFacts = new IndexFacts(new IndexModel(sd));

        assertEquals("mymap$lookup:new york" + FastMapSearch.keyValueSeparator() + "big apple",
                     searchYqlTree("mymap.lookup{\"new york\"} contains \"big apple\"", indexFacts));
        assertEquals("mymap$lookup:foo!" + FastMapSearch.keyValueSeparator() + "bar!",
                     searchYqlTree("mymap.lookup{\"foo!\"} contains \"bar!\"", indexFacts));
        assertEquals("intvaluemap$lookup:" + FastMapSearch.toKeyValue8Term("new york", 42),
                     searchYqlTree("intvaluemap.lookup{\"new york\"} = 42", indexFacts));
        assertEquals("intvaluemap$lookup:" + FastMapSearch.toKeyValue8Term("foo!", 42),
                     searchYqlTree("intvaluemap.lookup{\"foo!\"} = 42", indexFacts));
    }

    @Test
    public void requireOtherUseOfLookupFieldRejected() {
        assertLookupFieldUseRejected(new WordItem("x", "mymap.lookup"));
        assertLookupFieldUseRejected(new WordItem("x", "mymap.lookup.key"));
        assertLookupFieldUseRejected(new WordItem("x", "mymap.lookup.value"));
        assertLookupFieldUseRejected(new PrefixItem("x", "myarray.lookup.key"));
        assertLookupFieldUseRejected(sameElement("mymap.lookup"));
        assertLookupFieldUseRejected(sameElement("mymap", new WordItem("foo", "lookup.key"), new WordItem("bar", "value")));
        AndItem and = new AndItem();
        and.addItem(new WordItem("other", "title"));
        and.addItem(new WordItem("x", "mymap.lookup"));
        assertLookupFieldUseRejected(and);

        var exception = assertThrows(IllegalInputException.class, () -> rewrittenYql("mymap.lookup contains \"x\""));
        assertEquals("'mymap.lookup' can only be searched by a map lookup, as in mymap.lookup{\"key\"} = value, but got mymap.lookup:x",
                     exception.getMessage());
        assertThrows(IllegalInputException.class, () -> rewrittenYql("mymap.lookup.key contains \"x\""));
        assertThrows(IllegalInputException.class,
                     () -> rewrittenYql("mymap.lookup contains sameElement(key contains \"foo\", value contains \"bar\")"));

        // Other names are not lookup fields
        assertUntouched(new WordItem("x", "mymap.key"));
        assertUntouched(new WordItem("x", "mymap.lookupx"));
        assertUntouched(new WordItem("x", "othermap.lookup"));
        assertUntouched(new WordItem("x", "mymap.lookup.other"));
    }

    /** Each lookup of a field is rewritten to its own attribute, with its own key and value types. */
    @Test
    public void requireEachLookupOfAFieldIsRewritten() {
        assertEquals("twolookups$lookup:foo" + FastMapSearch.keyValueSeparator() + "bar",
                     rewrittenYql("twolookups.lookup{\"foo\"} = \"bar\""));
        assertEquals("twolookups$reversed:" + FastMapSearch.toKeyValue8Term("bar", 42),
                     rewrittenYql("twolookups.reversed{\"bar\"} = 42"));
        assertRewritten("twolookups$reversed:" + FastMapSearch.toKeyValue8Term("bar", 42),
                        new MapMatchItem("twolookups.reversed", new WordItem("bar", "key"), new IntItem(42, "value")));
        assertRejected(new MapMatchItem("twolookups.reversed", new WordItem("bar", "key"), new WordItem("x", "value")));
        assertLookupFieldUseRejected(new WordItem("x", "twolookups.reversed"));
        assertLookupFieldUseRejected(new WordItem("x", "twolookups.reversed.key"));
        assertUntouched(new WordItem("x", "twolookups.other"));
    }

    @Test
    public void requireLookupResolvedAcrossSchemas() {
        var withLookup = new Schema.Builder("withlookup")
                .add(new Field.Builder("mymap", "map<string,string>").addFastMapSearch("lookup").build())
                .build();
        var sameLookup = new Schema.Builder("samelookup")
                .add(new Field.Builder("mymap", "map<string,string>").addFastMapSearch("lookup").build())
                .build();
        var withoutLookup = new Schema.Builder("withoutlookup")
                .add(new Field.Builder("mymap", "map<string,string>").build())
                .build();
        var otherLookup = new Schema.Builder("otherlookup")
                .add(new Field.Builder("mymap", "map<string,string>").addFastMapSearch("other").build())
                .build();
        var otherType = new Schema.Builder("othertype")
                .add(new Field.Builder("mymap", "map<string,int>").addFastMapSearch("lookup").build())
                .build();
        var withoutField = new Schema.Builder("withoutfield")
                .add(new Field.Builder("title", "string").build())
                .build();
        String expected = "mymap$lookup:foo" + FastMapSearch.keyValueSeparator() + "bar";

        assertEquals(expected, rewritten(mapMatch("mymap"), withLookup, sameLookup).toString());
        assertEquals(expected, rewritten(mapMatch("mymap"), withoutField, withLookup).toString());

        var exception = assertThrows(IllegalInputException.class,
                                     () -> rewritten(mapMatch("mymap"), withoutLookup, withLookup));
        assertEquals("Lookup 'mymap.lookup' is not defined for field 'mymap' in schema 'withoutlookup', " +
                     "but is in schema 'withlookup'. Restrict the query to the schemas which have it",
                     exception.getMessage());
        exception = assertThrows(IllegalInputException.class,
                                 () -> rewritten(mapMatch("mymap"), otherLookup, withLookup, sameLookup));
        assertEquals("Lookup 'mymap.lookup' is not defined for field 'mymap' in schema 'otherlookup', " +
                     "but is in schemas 'samelookup', 'withlookup'. Restrict the query to the schemas which have it",
                     exception.getMessage());
        exception = assertThrows(IllegalInputException.class,
                                 () -> rewritten(mapMatch("mymap"), withLookup, otherType));
        assertEquals("Lookup 'mymap.lookup' has different key or value types in schemas 'othertype', 'withlookup'. " +
                     "Restrict the query to schemas where it is the same",
                     exception.getMessage());

        // A lookup field in one schema is not searchable directly in any
        assertThrows(IllegalInputException.class,
                     () -> rewritten(new WordItem("x", "mymap.lookup"), withoutLookup, withLookup));

        // Without any lookup fields, nothing is changed
        var plain = new MapMatchItem("mymap.lookup", new WordItem("foo", "key"), new WordItem("bar", "value"));
        assertEquals(plain.toString(), rewritten(plain, withoutLookup, withoutField).toString());
    }

    @Test
    public void requireMapLookupRewrittenForFastArrayOfStructField() {
        // A map lookup has a key and a value, whatever the struct fields holding them are named
        assertRewritten("myarray$lookup:foo" + FastMapSearch.keyValueSeparator() + "bar",
                        mapMatch("myarray"));
        assertRewritten("intvaluearray$lookup:" + FastMapSearch.toKeyValue8Term("foo", 10),
                        mapMatch("intvaluearray", new WordItem("foo", "key"), new IntItem("10", "value")));
        assertRewritten("STRING_RANGE longvaluearray$lookup:[\"" + FastMapSearch.toKeyValue16Term("foo", 5L) + "\";\""
                        + FastMapSearch.toKeyValue16Term("foo", 10L) + "\"]",
                        mapMatch("longvaluearray", new WordItem("foo", "key"), new IntItem("[5;10]", "value")));

        // A sameElement on the struct fields is not a map lookup, and is not rewritten
        assertUntouched(sameElement("myarray", new WordItem("foo", "mykey"), new WordItem("bar", "myvalue")));

        // Untouched when the field does not have fast map search
        assertUntouched(mapMatch("otherarray"));

        // A lookup which is not a single term is rejected
        assertRejected(mapMatch("myarray", new PrefixItem("fo", "key"), new WordItem("bar", "value")));
    }

    @Test
    public void requireIntegerKeyRewrittenToItsDecimalForm() {
        var exact = new ExactStringItem("42");
        exact.setIndexName("key");
        for (TermItem key : List.of(new IntItem("42", "key"), new IntItem(42, "key"), new WordItem("42", "key"), exact,
                                    new WordItem("042", "key"), new IntItem("0042", "key")))
            assertRewritten(intKeyTerm("42"), intKeyLookup(key));
        assertRewritten(intKeyTerm("-7"), intKeyLookup(new IntItem("-7", "key")));
        assertRewritten(intKeyTerm("0"), intKeyLookup(new WordItem("-0", "key")));

        // The int range limits the key of an int map, but not of a long map
        assertRewritten(intKeyTerm(Integer.toString(Integer.MIN_VALUE)), intKeyLookup(new IntItem(Integer.MIN_VALUE, "key")));
        assertRewritten(intKeyTerm(Integer.toString(Integer.MAX_VALUE)), intKeyLookup(new IntItem(Integer.MAX_VALUE, "key")));
        assertRejected(intKeyLookup(new IntItem(1L << 31, "key")));
        assertRejected(intKeyLookup(new IntItem((long) Integer.MIN_VALUE - 1, "key")));
        for (long key : new long[] { 1L << 31, Long.MAX_VALUE, Long.MIN_VALUE })
            assertRewritten("longkeymap$lookup:" + FastMapSearch.toKeyValue8Term(Long.toString(key), 3),
                            mapMatch("longkeymap", new IntItem(key, "key"), new IntItem("3", "value")));
        assertRejected(mapMatch("longkeymap", new IntItem("9223372036854775808", "key"), new IntItem("3", "value")));

        // Array of struct with an int key
        assertRewritten("intkeyarray$lookup:" + FastMapSearch.toKeyValueTerm("42", "bar"),
                        mapMatch("intkeyarray", new IntItem("42", "key"), new WordItem("bar", "value")));
    }

    /** An integer key must be a plain integer: ranges, even of a single integer, and other number forms are rejected. */
    @Test
    public void requireIntegerKeyWhichIsNotAPlainIntegerRejected() {
        for (TermItem key : List.of(new WordItem("foo", "key"), new WordItem("1.5", "key"),
                                    new WordItem("42.0", "key"), new WordItem(" 42", "key"), new WordItem("+42", "key"),
                                    new WordItem("\u0664\u0662", "key"), // Arabic-Indic digits 42
                                    new WordItem("0x2A", "key"), new WordItem("1e2", "key"), new WordItem("[42;42]", "key"),
                                    new IntItem("1.5", "key"), new IntItem("42.0", "key"),
                                    new IntItem("[42;42]", "key"), new IntItem("<41;43>", "key"), new IntItem("[41.5;42.5]", "key"),
                                    new IntItem("[1;2]", "key"), new IntItem(">5", "key"), new IntItem("[;5]", "key"),
                                    new IntItem("[42;42;10]", "key"),
                                    new IntItem(new Limit(Double.NaN, true), new Limit(Double.NaN, true), "key"),
                                    new PrefixItem("4", "key")))
            assertRejected(intKeyLookup(key));

        var exception = assertThrows(IllegalInputException.class, () -> rewritten(intKeyLookup(new WordItem("foo", "key"))));
        assertEquals("Lookup 'intkeymap.lookup' requires a single int as key, and a single word " +
                     "as value, but got intkeymap.lookup:{key:foo value:bar}",
                     exception.getMessage());
        exception = assertThrows(IllegalInputException.class,
                                 () -> rewritten(mapMatch("longkeymap", new WordItem("foo", "key"), new IntItem("3", "value"))));
        assertEquals("Lookup 'longkeymap.lookup' requires a single long as key, and a single integer or an integer range " +
                     "as value, but got longkeymap.lookup:{key:foo value:3}",
                     exception.getMessage());
    }

    @Test
    public void requireIntegerKeyWithFloatingPointValueRewritten() {
        assertRewritten("intkeyfloatmap$lookup:" + FastMapSearch.toKeyValueFloatTerm("42", 1.5f),
                        mapMatch("intkeyfloatmap", new IntItem("42", "key"), new WordItem("1.5", "value")));
        assertRewritten("longkeydoublemap$lookup:" + FastMapSearch.toKeyValueDoubleTerm("5000000000", 1.5),
                        mapMatch("longkeydoublemap", new IntItem(5000000000L, "key"), new WordItem("1.5", "value")));
    }

    @Test
    public void requireYqlMapLookupWithIntegerKeyRewritten() {
        assertEquals(intKeyTerm("42"), rewrittenYql("intkeymap.lookup{42} contains \"bar\""));
        assertEquals(intKeyTerm("42"), rewrittenYql("intkeymap.lookup{\"42\"} contains \"bar\""));
        assertEquals("longkeymap$lookup:" + FastMapSearch.toKeyValue8Term("5000000000", 3),
                     rewrittenYql("longkeymap.lookup{5000000000} = 3"));
        assertEquals("STRING_RANGE longkeymap$lookup:[\"" + FastMapSearch.toKeyValue8Term("1", 5) + "\";\""
                     + FastMapSearch.toKeyValue8Term("1", 10) + "\"]",
                     rewrittenYql("range(longkeymap.lookup{1}, 5, 10)"));
    }

    @Test
    public void requireIntValueEncodedInExcessHex() {
        String expected = "intvaluemap$lookup:" + FastMapSearch.toKeyValue8Term("foo", 10);

        // The value arrives as an IntItem
        assertRewritten(expected, mapMatch("intvaluemap", new WordItem("foo", "key"), new IntItem("10", "value")));

        // The value arrives as a WordItem holding an int
        assertRewritten(expected, mapMatch("intvaluemap", new WordItem("foo", "key"), new WordItem("10", "value")));

        // Negative values encode across zero
        assertRewritten("intvaluemap$lookup:" + FastMapSearch.toKeyValue8Term("foo", -3),
                        mapMatch("intvaluemap", new WordItem("foo", "key"), new IntItem("-3", "value")));
    }

    @Test
    public void requireLongValueEncodedInExcessHex() {
        long beyondInt = 1L << 32;
        String expected = "longvaluemap$lookup:" + FastMapSearch.toKeyValue16Term("foo", beyondInt);

        // The value arrives as an IntItem
        assertRewritten(expected, mapMatch("longvaluemap", new WordItem("foo", "key"), new IntItem(Long.toString(beyondInt), "value")));

        // The value arrives as a WordItem holding a long
        assertRewritten(expected, mapMatch("longvaluemap", new WordItem("foo", "key"), new WordItem(Long.toString(beyondInt), "value")));

        // A value which fits in an int is still encoded with 16 digits
        assertRewritten("longvaluemap$lookup:" + FastMapSearch.toKeyValue16Term("foo", 10L),
                        mapMatch("longvaluemap", new WordItem("foo", "key"), new IntItem("10", "value")));

        // Negative values encode across zero
        assertRewritten("longvaluemap$lookup:" + FastMapSearch.toKeyValue16Term("foo", -beyondInt),
                        mapMatch("longvaluemap", new WordItem("foo", "key"), new IntItem(Long.toString(-beyondInt), "value")));
    }

    @Test
    public void requireRejectedWhenTermsDoNotMatchOneEntry() {
        // Not parseable as an int for an int-valued map
        assertRejected(mapMatch("intvaluemap", new WordItem("foo", "key"), new WordItem("bar", "value")));

        // Beyond the int range for an int-valued map
        assertRejected(mapMatch("intvaluemap", new WordItem("foo", "key"), new IntItem(Long.toString(1L << 32), "value")));

        // Not parseable as a long for a long-valued map
        assertRejected(mapMatch("longvaluemap", new WordItem("foo", "key"), new WordItem("bar", "value")));
        assertRejected(mapMatch("longvaluemap", new WordItem("foo", "key"), new IntItem("99999999999999999999", "value")));

        // A prefix, fuzzy or regex term matches more than one string
        assertRejected(mapMatch("mymap", new PrefixItem("fo", "key"), new WordItem("bar", "value")));
        assertRejected(mapMatch("mymap", new WordItem("foo", "key"), new PrefixItem("ba", "value")));
        assertRejected(mapMatch("mymap", new WordItem("foo", "key"), new FuzzyItem("value", true, "bar", 2, 0, false)));
        assertRejected(mapMatch("mymap", new WordItem("foo", "key"), new RegExpItem("value", true, "b.*")));

        // A key or value which is not a term
        var phrase = new PhraseItem();
        phrase.setIndexName("value");
        phrase.addItem(new WordItem("big", "value"));
        phrase.addItem(new WordItem("apple", "value"));
        assertRejected(new MapMatchItem("mymap.lookup", new WordItem("foo", "key"), phrase));

        // A fractional bound on an int value
        assertRejected(mapMatch("intvaluemap", new WordItem("foo", "key"), intRange(new Limit(1.5, true), new Limit(10, true))));

        // The error says what is supported
        var exception = assertThrows(IllegalInputException.class,
                                     () -> rewritten(mapMatch("intvaluemap", new WordItem("foo", "key"), new WordItem("bar", "value"))));
        assertEquals("Lookup 'intvaluemap.lookup' requires a single word as key, and a single integer or an integer range " +
                     "as value, but got intvaluemap.lookup:{key:foo value:bar}",
                     exception.getMessage());
    }

    @Test
    public void requireIntRangeRewrittenToLexicalRange() {
        var searcher = new FastMapSearcher();

        // A closed range becomes a closed lexical range over the encoded endpoints.
        var closed = searcher.makeIntRange("foo", new IntItem("[5;10]", "value"), "intvaluemap$lookup");
        assertEquals("intvaluemap$lookup", closed.getIndexName());
        assertEquals(FastMapSearch.toKeyValue8Term("foo", 5), closed.getFrom());
        assertEquals(FastMapSearch.toKeyValue8Term("foo", 10), closed.getTo());
        assertEquals(0, closed.getHitLimit());
        assertTrue(closed.isFromInclusive());
        assertTrue(closed.isToInclusive());

        // Exclusive endpoints stay exclusive.
        var open = searcher.makeIntRange("foo", intRange(new Limit(5, false), new Limit(10, false)), "intvaluemap$lookup");
        assertFalse(open.isFromInclusive());
        assertFalse(open.isToInclusive());

        // An unbounded end becomes the extreme int value, so that the range cannot run past
        // this key into the entries of the neighbouring keys.
        var toInfinity = searcher.makeIntRange("foo", intRange(new Limit(5, true), Limit.POSITIVE_INFINITY), "intvaluemap$lookup");
        assertEquals(FastMapSearch.toKeyValue8Term("foo", 5), toInfinity.getFrom());
        assertEquals(FastMapSearch.toKeyValue8Term("foo", Integer.MAX_VALUE), toInfinity.getTo());
        assertTrue(toInfinity.isToInclusive());

        var fromInfinity = searcher.makeIntRange("foo", intRange(Limit.NEGATIVE_INFINITY, new Limit(10, true)), "intvaluemap$lookup");
        assertEquals(FastMapSearch.toKeyValue8Term("foo", Integer.MIN_VALUE), fromInfinity.getFrom());
        assertEquals(FastMapSearch.toKeyValue8Term("foo", 10), fromInfinity.getTo());
        assertTrue(fromInfinity.isFromInclusive());

        // Negative endpoints encode across zero and stay ordered.
        var negative = searcher.makeIntRange("foo", intRange(new Limit(-10, true), new Limit(-5, true)), "intvaluemap$lookup");
        assertTrue(negative.getFrom().compareTo(negative.getTo()) < 0);

        // End to end, through the searcher.
        String expected = "STRING_RANGE intvaluemap$lookup:[\"" + FastMapSearch.toKeyValue8Term("foo", 5) + "\";\""
                          + FastMapSearch.toKeyValue8Term("foo", 10) + "\"]";
        assertRewritten(expected, mapMatch("intvaluemap", new WordItem("foo", "key"), new IntItem("[5;10]", "value")));
    }

    @Test
    public void requireIntRangeWithHitLimitRewrittenToLexicalRange() {
        var searcher = new FastMapSearcher();

        var closed = searcher.makeIntRange("foo", new IntItem("[5;10;42]", "value"), "intvaluemap$lookup");
        assertEquals("intvaluemap$lookup", closed.getIndexName());
        assertEquals(FastMapSearch.toKeyValue8Term("foo", 5), closed.getFrom());
        assertEquals(FastMapSearch.toKeyValue8Term("foo", 10), closed.getTo());
        assertEquals(42, closed.getHitLimit());
        assertTrue(closed.isFromInclusive());
        assertTrue(closed.isToInclusive());
    }

    @Test
    public void requireRejectedForRangesWhichAreNotPlainIntRanges() {
        var searcher = new FastMapSearcher();

        // A fractional endpoint has no exact int encoding.
        assertNull(searcher.makeIntRange("foo", intRange(new Limit(1.5, true), new Limit(10, true)), "intvaluemap$lookup"));

        // A range on a string-valued map is not an IntItem, and is rejected.
        assertRejected(mapMatch("mymap", new WordItem("foo", "key"),
                                new StringRangeItem("a", true, "z", true, "value", false, null)));
    }

    @Test
    public void requireLongRangeRewrittenToLexicalRange() {
        var searcher = new FastMapSearcher();
        long low = 1L << 32;
        long high = (1L << 32) + 100;

        // A closed range becomes a closed lexical range over the encoded endpoints.
        var closed = searcher.makeLongRange("foo", new IntItem("[" + low + ";" + high + "]", "value"), "longvaluemap$lookup");
        assertEquals("longvaluemap$lookup", closed.getIndexName());
        assertEquals(FastMapSearch.toKeyValue16Term("foo", low), closed.getFrom());
        assertEquals(FastMapSearch.toKeyValue16Term("foo", high), closed.getTo());
        assertTrue(closed.isFromInclusive());
        assertEquals(0, closed.getHitLimit());
        assertTrue(closed.isToInclusive());

        // Exclusive endpoints stay exclusive.
        var open = searcher.makeLongRange("foo", intRange(new Limit(low, false), new Limit(high, false)), "longvaluemap$lookup");
        assertFalse(open.isFromInclusive());
        assertFalse(open.isToInclusive());

        // An unbounded end becomes the extreme long value, so that the range cannot run past
        // this key into the entries of the neighbouring keys.
        var toInfinity = searcher.makeLongRange("foo", intRange(new Limit(low, true), Limit.POSITIVE_INFINITY), "longvaluemap$lookup");
        assertEquals(FastMapSearch.toKeyValue16Term("foo", low), toInfinity.getFrom());
        assertEquals(FastMapSearch.toKeyValue16Term("foo", Long.MAX_VALUE), toInfinity.getTo());
        assertTrue(toInfinity.isToInclusive());

        var fromInfinity = searcher.makeLongRange("foo", intRange(Limit.NEGATIVE_INFINITY, new Limit(high, true)), "longvaluemap$lookup");
        assertEquals(FastMapSearch.toKeyValue16Term("foo", Long.MIN_VALUE), fromInfinity.getFrom());
        assertEquals(FastMapSearch.toKeyValue16Term("foo", high), fromInfinity.getTo());
        assertTrue(fromInfinity.isFromInclusive());

        // Int endpoints, and integral double endpoints, are exact longs.
        var small = searcher.makeLongRange("foo", intRange(new Limit(5, true), new Limit(10.0, true)), "longvaluemap$lookup");
        assertEquals(FastMapSearch.toKeyValue16Term("foo", 5L), small.getFrom());
        assertEquals(FastMapSearch.toKeyValue16Term("foo", 10L), small.getTo());

        // Negative endpoints encode across zero and stay ordered.
        var negative = searcher.makeLongRange("foo", intRange(new Limit(-high, true), new Limit(-low, true)), "longvaluemap$lookup");
        assertTrue(negative.getFrom().compareTo(negative.getTo()) < 0);

        // End to end, through the searcher.
        String expected = "STRING_RANGE longvaluemap$lookup:[\"" + FastMapSearch.toKeyValue16Term("foo", low) + "\";\""
                          + FastMapSearch.toKeyValue16Term("foo", high) + "\"]";
        assertRewritten(expected, mapMatch("longvaluemap", new WordItem("foo", "key"),
                                              new IntItem("[" + low + ";" + high + "]", "value")));
    }

    @Test
    public void requireLongRangeWithHitLimitRewrittenToLexicalRange() {
        var searcher = new FastMapSearcher();

        var closed = searcher.makeLongRange("foo", new IntItem("[5;10;42]", "value"), "longvaluemap$lookup");
        assertEquals("longvaluemap$lookup", closed.getIndexName());
        assertEquals(FastMapSearch.toKeyValue16Term("foo", 5), closed.getFrom());
        assertEquals(FastMapSearch.toKeyValue16Term("foo", 10), closed.getTo());
        assertTrue(closed.isFromInclusive());
        assertEquals(42, closed.getHitLimit());
        assertTrue(closed.isToInclusive());
    }

    @Test
    public void requireRejectedForRangesWhichAreNotPlainLongRanges() {
        var searcher = new FastMapSearcher();

        // A fractional endpoint has no exact long encoding.
        assertNull(searcher.makeLongRange("foo", intRange(new Limit(1.5, true), new Limit(10, true)), "longvaluemap$lookup"));

        // An endpoint beyond the long range has no encoding.
        assertNull(searcher.makeLongRange("foo", new IntItem("[0;99999999999999999999]", "value"), "longvaluemap$lookup"));
        assertNull(searcher.makeLongRange("foo", intRange(new Limit(0, true), new Limit(1e19, true)), "longvaluemap$lookup"));
    }

    @Test
    public void requireFloatValueEncodedInExcessHex() {
        String expected = "floatvaluemap$lookup:" + FastMapSearch.toKeyValueFloatTerm("foo", 1.5f);

        // The value arrives as an IntItem
        assertRewritten(expected, mapMatch("floatvaluemap", new WordItem("foo", "key"), new IntItem("1.5", "value")));

        // The value arrives as a WordItem holding a number
        assertRewritten(expected, mapMatch("floatvaluemap", new WordItem("foo", "key"), new WordItem("1.5", "value")));

        // An integral value is a float too
        assertRewritten("floatvaluemap$lookup:" + FastMapSearch.toKeyValueFloatTerm("foo", 10.0f),
                        mapMatch("floatvaluemap", new WordItem("foo", "key"), new IntItem("10", "value")));

        // A value which is not exactly a float is rounded to the nearest one, like the backend does
        assertRewritten("floatvaluemap$lookup:" + FastMapSearch.toKeyValueFloatTerm("foo", 0.1f),
                        mapMatch("floatvaluemap", new WordItem("foo", "key"), new IntItem("0.1", "value")));

        // Negative values encode across zero
        assertRewritten("floatvaluemap$lookup:" + FastMapSearch.toKeyValueFloatTerm("foo", -3.25f),
                        mapMatch("floatvaluemap", new WordItem("foo", "key"), new WordItem("-3.25", "value")));
    }

    @Test
    public void requireDoubleValueEncodedInExcessHex() {
        String expected = "doublevaluemap$lookup:" + FastMapSearch.toKeyValueDoubleTerm("foo", 0.1);

        // The value arrives as an IntItem
        assertRewritten(expected, mapMatch("doublevaluemap", new WordItem("foo", "key"), new IntItem("0.1", "value")));

        // The value arrives as a WordItem holding a number
        assertRewritten(expected, mapMatch("doublevaluemap", new WordItem("foo", "key"), new WordItem("0.1", "value")));
        assertRewritten("doublevaluemap$lookup:" + FastMapSearch.toKeyValueDoubleTerm("foo", 1.5e300),
                        mapMatch("doublevaluemap", new WordItem("foo", "key"), new WordItem("1.5e300", "value")));

        // Negative values encode across zero
        assertRewritten("doublevaluemap$lookup:" + FastMapSearch.toKeyValueDoubleTerm("foo", -3.0),
                        mapMatch("doublevaluemap", new WordItem("foo", "key"), new IntItem("-3", "value")));
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
        var closed = (StringRangeItem) searcher.makeFloatingPointItem("foo", new IntItem("[-1.5;2.5]", "value"), "floatvaluemap$lookup", true);
        assertEquals("floatvaluemap$lookup", closed.getIndexName());
        assertEquals(FastMapSearch.toKeyValueFloatTerm("foo", -1.5f), closed.getFrom());
        assertEquals(FastMapSearch.toKeyValueFloatTerm("foo", 2.5f), closed.getTo());
        assertEquals(0, closed.getHitLimit());
        assertTrue(closed.isFromInclusive());
        assertTrue(closed.isToInclusive());

        // Exclusive endpoints stay exclusive.
        var open = (StringRangeItem) searcher.makeFloatingPointItem("foo", intRange(new Limit(1.0, false), new Limit(2.0, false)), "floatvaluemap$lookup", true);
        assertEquals(FastMapSearch.toKeyValueFloatTerm("foo", 1.0f), open.getFrom());
        assertEquals(FastMapSearch.toKeyValueFloatTerm("foo", 2.0f), open.getTo());
        assertFalse(open.isFromInclusive());
        assertFalse(open.isToInclusive());

        // An unbounded end becomes the infinity in its direction, which the backend includes.
        var toInfinity = (StringRangeItem) searcher.makeFloatingPointItem("foo", new IntItem(">5", "value"), "doublevaluemap$lookup", false);
        assertEquals(FastMapSearch.toKeyValueDoubleTerm("foo", 5.0), toInfinity.getFrom());
        assertFalse(toInfinity.isFromInclusive());
        assertEquals(FastMapSearch.toKeyValueDoubleTerm("foo", Double.POSITIVE_INFINITY), toInfinity.getTo());
        assertTrue(toInfinity.isToInclusive());
        var fromInfinity = (StringRangeItem) searcher.makeFloatingPointItem("foo", new IntItem("<5", "value"), "floatvaluemap$lookup", true);
        assertEquals(FastMapSearch.toKeyValueFloatTerm("foo", Float.NEGATIVE_INFINITY), fromInfinity.getFrom());
        assertTrue(fromInfinity.isFromInclusive());
        assertEquals(FastMapSearch.toKeyValueFloatTerm("foo", 5.0f), fromInfinity.getTo());
        assertFalse(fromInfinity.isToInclusive());

        // End to end, through the searcher.
        String expected = "STRING_RANGE doublevaluemap$lookup:[\"" + FastMapSearch.toKeyValueDoubleTerm("foo", 0.5) + "\";\""
                          + FastMapSearch.toKeyValueDoubleTerm("foo", 10.0) + "\"]";
        assertRewritten(expected, mapMatch("doublevaluemap", new WordItem("foo", "key"), new IntItem("[0.5;10]", "value")));
    }

    @Test
    public void requireFloatingPointRangeWithHitLimitRewrittenToLexicalRange() {
        var searcher = new FastMapSearcher();

        var closed = (StringRangeItem) searcher.makeFloatingPointItem("foo", new IntItem("[-1.5;2.5;42]", "value"), "floatvaluemap$lookup", true);
        assertEquals("floatvaluemap$lookup", closed.getIndexName());
        assertEquals(FastMapSearch.toKeyValueFloatTerm("foo", -1.5f), closed.getFrom());
        assertEquals(FastMapSearch.toKeyValueFloatTerm("foo", 2.5f), closed.getTo());
        assertEquals(42, closed.getHitLimit());
        assertTrue(closed.isFromInclusive());
        assertTrue(closed.isToInclusive());
    }

    @Test
    public void requireRejectedForUnsupportedFloatingPointTerms() {
        var searcher = new FastMapSearcher();

        // Not a number
        assertRejected(mapMatch("floatvaluemap", new WordItem("foo", "key"), new WordItem("bar", "value")));
        assertRejected(mapMatch("doublevaluemap", new WordItem("foo", "key"), new WordItem("NaN", "value")));
        assertNull(searcher.makeFloatingPointItem("foo", intRange(new Limit(Double.NaN, true), new Limit(1.0, true)), "doublevaluemap$lookup", false));

        // A prefix term matches more than one string
        assertRejected(mapMatch("doublevaluemap", new WordItem("foo", "key"), new PrefixItem("1", "value")));
    }

    private static String floatingPointTerm(String key, double value, boolean isFloat) {
        return isFloat ? FastMapSearch.toKeyValueFloatTerm(key, (float) value) : FastMapSearch.toKeyValueDoubleTerm(key, value);
    }

    private static IntItem intRange(Limit from, Limit to) {
        return new IntItem(from, to, "value");
    }

    /** Asserts that the given map lookup on the lookup field is rejected, as it cannot become a single term. */
    private static void assertRejected(MapMatchItem mapMatch) {
        var exception = assertThrows(IllegalInputException.class, () -> rewritten(mapMatch), mapMatch.toString());
        assertTrue(exception.getMessage().startsWith("Lookup '" + mapMatch.getFieldName() + "' requires "), exception.getMessage());
    }

    private static void assertLookupFieldUseRejected(Item item) {
        var exception = assertThrows(IllegalInputException.class, () -> rewritten(item), item.toString());
        assertTrue(exception.getMessage().contains("can only be searched by a map lookup"), exception.getMessage());
    }

    private static Item rewritten(Item item, Schema ... schemas) {
        Query query = queryWith(item.clone());
        var schemaInfo = new SchemaInfo(List.of(schemas), List.of());
        new FastMapSearcher().search(query, new Execution(Execution.Context.createContextStub(schemaInfo)));
        return query.getModel().getQueryTree().getRoot();
    }

    private static Item rewritten(Item item) {
        Query query = queryWith(item.clone());
        new FastMapSearcher().search(query, execution());
        return query.getModel().getQueryTree().getRoot();
    }

    private static void assertRewritten(String expected, Item item) {
        Query query = queryWith(item);
        new FastMapSearcher().search(query, execution());
        assertEquals(expected, query.getModel().getQueryTree().getRoot().toString());
    }

    private static void assertUntouched(Item item) {
        String original = item.toString();
        Query query = queryWith(item);
        new FastMapSearcher().search(query, execution());
        assertEquals(original, query.getModel().getQueryTree().getRoot().toString());
    }

    private static Execution execution() {
        return new Execution(Execution.Context.createContextStub(schemaInfo()));
    }

    private static SchemaInfo schemaInfo() {
        var schema = new Schema.Builder("test")
                .add(new Field.Builder("mymap", "map<string,string>").addFastMapSearch("lookup").build())
                .add(new Field.Builder("intvaluemap", "map<string,int>").addFastMapSearch("lookup").build())
                .add(new Field.Builder("longvaluemap", "map<string,long>").addFastMapSearch("lookup").build())
                .add(new Field.Builder("floatvaluemap", "map<string,float>").addFastMapSearch("lookup").build())
                .add(new Field.Builder("doublevaluemap", "map<string,double>").addFastMapSearch("lookup").build())
                .add(new Field.Builder("intkeymap", "map<int,string>").addFastMapSearch("lookup").build())
                .add(new Field.Builder("longkeymap", "map<long,int>").addFastMapSearch("lookup").build())
                .add(new Field.Builder("intkeyfloatmap", "map<int,float>").addFastMapSearch("lookup").build())
                .add(new Field.Builder("longkeydoublemap", "map<long,double>").addFastMapSearch("lookup").build())
                .add(new Field.Builder("othermap", "map<string,string>").build())
                .add(new Field.Builder("myarray", "array<entry>").addFastMapSearch(arrayFields("string")).build())
                .add(new Field.Builder("intvaluearray", "array<intentry>").addFastMapSearch(arrayFields("int")).build())
                .add(new Field.Builder("longvaluearray", "array<longentry>").addFastMapSearch(arrayFields("long")).build())
                .add(new Field.Builder("intkeyarray", "array<intkeyentry>").addFastMapSearch(arrayFields("int", "string")).build())
                .add(new Field.Builder("twolookups", "array<entry>")
                             .addFastMapSearch(arrayFields("string"))
                             .addFastMapSearch(new Field.FastMapSearchFields("reversed", "myvalue", Field.Type.from("string"), "mykey", Field.Type.from("int")))
                             .build())
                .add(new Field.Builder("otherarray", "array<entry>").build())
                .build();
        return new SchemaInfo(List.of(schema), List.of());
    }

    private static Field.FastMapSearchFields arrayFields(String valueType) {
        return arrayFields("string", valueType);
    }

    private static Field.FastMapSearchFields arrayFields(String keyType, String valueType) {
        return new Field.FastMapSearchFields("lookup", "mykey", Field.Type.from(keyType), "myvalue", Field.Type.from(valueType));
    }

    /** Returns a lookup of the given key with value "bar" in the map<int,string> intkeymap. */
    private static MapMatchItem intKeyLookup(TermItem key) {
        return mapMatch("intkeymap", key, new WordItem("bar", "value"));
    }

    /** Returns the term which a lookup of the given key with value "bar" in intkeymap is rewritten to. */
    private static String intKeyTerm(String key) {
        return "intkeymap$lookup:" + FastMapSearch.toKeyValueTerm(key, "bar");
    }

    private static MapMatchItem mapMatch(String field) {
        return mapMatch(field, new WordItem("foo", "key"), new WordItem("bar", "value"));
    }

    /** Returns a map lookup on the lookup field of the given field, as in field.lookup{"key"} = value */
    private static MapMatchItem mapMatch(String field, TermItem key, TermItem value) {
        return new MapMatchItem(field + ".lookup", key, value);
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

    /** Returns the result of parsing the given YQL where clause with the given index facts and running the searcher. */
    private static Result searchYql(String where, IndexFacts indexFacts) {
        Query query = new Query("?yql=" + URLEncoder.encode("select * from sources * where " + where, StandardCharsets.UTF_8));
        var context = Execution.Context.createContextStub(null, indexFacts, execution().context().schemaInfo(), null);
        return new Execution(new Chain<>(new MinimalQueryInserter(), new FastMapSearcher()), context).search(query);
    }

    private static String searchYqlTree(String where, IndexFacts indexFacts) {
        var result = searchYql(where, indexFacts);
        assertNull(result.hits().getError(), where);
        return result.getQuery().getModel().getQueryTree().getRoot().toString();
    }

    /** Returns the query tree after parsing the given YQL where clause and running the searcher. */
    private static String rewrittenYql(String where) {
        Query query = new Query("?yql=" + URLEncoder.encode("select * from sources * where " + where, StandardCharsets.UTF_8));
        new Execution(new Chain<>(new MinimalQueryInserter(), new FastMapSearcher()), execution().context()).search(query);
        return query.getModel().getQueryTree().getRoot().toString();
    }

    private static Query queryWith(Item root) {
        Query query = new Query();
        query.getModel().getQueryTree().setRoot(root);
        return query;
    }

}
