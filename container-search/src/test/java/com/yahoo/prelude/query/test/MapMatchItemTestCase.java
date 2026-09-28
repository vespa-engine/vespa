// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.test;

import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.MapMatchItem;
import com.yahoo.prelude.query.WordItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MapMatchItemTestCase {

    @Test
    void testKeyAndValueAreTheChildren() {
        var key = new WordItem("foo", "key");
        var value = new IntItem("42", "value");
        var mapMatch = new MapMatchItem("mymap", key, value);
        assertSame(key, mapMatch.keyItem());
        assertSame(value, mapMatch.valueItem());
        assertSame(key, mapMatch.getItem(0));
        assertSame(value, mapMatch.getItem(1));
        assertEquals("mymap:{key:foo value:42}", mapMatch.toString());
    }

    @Test
    void testCloneHasItsOwnKeyAndValue() {
        var mapMatch = new MapMatchItem("mymap", new WordItem("foo", "key"), new IntItem("42", "value"));
        var copy = (MapMatchItem) mapMatch.clone();

        assertEquals(mapMatch, copy);
        assertNotSame(mapMatch.keyItem(), copy.keyItem());
        assertNotSame(mapMatch.valueItem(), copy.valueItem());
        assertSame(copy.getItem(0), copy.keyItem());
        assertSame(copy.getItem(1), copy.valueItem());
        assertSame(copy, copy.keyItem().getParent());
        assertSame(copy, copy.valueItem().getParent());

        // Changing the copy leaves the original alone
        ((WordItem) copy.keyItem()).setWord("bar");
        assertEquals("mymap:{key:foo value:42}", mapMatch.toString());
        assertEquals("mymap:{key:bar value:42}", copy.toString());
    }

    @Test
    void testCloneCannotGetExtraChildren() {
        var copy = (MapMatchItem) new MapMatchItem("mymap", new WordItem("foo", "key"), new WordItem("bar", "value")).clone();
        assertThrows(IllegalArgumentException.class, () -> copy.addItem(new WordItem("baz", "other")));
    }

    @Test
    void testChildrenCannotBeReplaced() {
        var mapMatch = new MapMatchItem("mymap", new WordItem("foo", "key"), new WordItem("bar", "value"));
        assertThrows(UnsupportedOperationException.class, () -> mapMatch.setItem(0, new WordItem("baz", "key")));
        assertThrows(UnsupportedOperationException.class, () -> mapMatch.setItem(1, new WordItem("baz", "value")));
        assertEquals("mymap:{key:foo value:bar}", mapMatch.toString());
    }

}
