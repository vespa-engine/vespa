// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.SameElementItem;
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
        var schema = new Schema.Builder("test")
                .add(new Field.Builder("mymap", "map<string,string>").setFastMapSearch(true).build())
                .add(new Field.Builder("othermap", "map<string,string>").build())
                .build();
        var schemaInfo = new SchemaInfo(List.of(schema), List.of());
        var execution = new Execution(Execution.Context.createContextStub(schemaInfo));

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

    private static SameElementItem sameElement(String field) {
        SameElementItem sameElement = new SameElementItem(field);
        sameElement.addItem(new WordItem("foo", "key"));
        sameElement.addItem(new WordItem("bar", "value"));
        return sameElement;
    }

    private static Query queryWith(Item root) {
        Query query = new Query();
        query.getModel().getQueryTree().setRoot(root);
        return query;
    }

}
