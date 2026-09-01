// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.PrefixItem;
import com.yahoo.prelude.query.SameElementItem;
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

/**
 * Tests for {@link FastMapSearcher}
 */
public class FastMapSearcherTest {

    @Test
    public void requireWordItemMadeCorrectly() {
        FastMapSearcher searcher = new FastMapSearcher();
        var word = searcher.makeFastMapSearchWordItem("foo", "bar", "baz");

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
        String expected = "intvaluemap$keyvalue:foo" + FastMapSearch.keyValueSeparator() + FastMapSearch.encodeInt(10);

        // The value arrives as an IntItem
        assertRewritten(expected, sameElement("intvaluemap", new WordItem("foo", "key"), new IntItem("10", "value")));

        // The value arrives as a WordItem holding an int
        assertRewritten(expected, sameElement("intvaluemap", new WordItem("foo", "key"), new WordItem("10", "value")));

        // Negative values encode across zero
        assertRewritten("intvaluemap$keyvalue:foo" + FastMapSearch.keyValueSeparator() + FastMapSearch.encodeInt(-3),
                        sameElement("intvaluemap", new WordItem("foo", "key"), new IntItem("-3", "value")));
    }

    @Test
    public void requireIntKeyEncodedInExcessHex() {
        assertRewritten("intkeymap$keyvalue:" + FastMapSearch.encodeInt(7) + FastMapSearch.keyValueSeparator() + "bar",
                        sameElement("intkeymap", new IntItem("7", "key"), new WordItem("bar", "value")));
    }

    @Test
    public void requireFallbackWhenTermsDoNotMatchOneEntry() {
        // A range matches more than one value
        assertUntouched(sameElement("intvaluemap", new WordItem("foo", "key"), new IntItem("[5;10]", "value")));

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
