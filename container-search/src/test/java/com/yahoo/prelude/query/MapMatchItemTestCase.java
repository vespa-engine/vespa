// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    void testChildrenCannotBeAdded() {
        var mapMatch = mapMatch();
        assertThrows(IllegalArgumentException.class, () -> mapMatch.addItem(new WordItem("baz", "other")));
        assertThrows(IllegalArgumentException.class, () -> mapMatch.addItem(0, new WordItem("baz", "other")));
        assertThrows(UnsupportedOperationException.class, () -> mapMatch.getItemIterator().add(new WordItem("baz", "other")));
        assertUnchanged(mapMatch);
    }

    @Test
    void testChildrenCannotBeReplaced() {
        var mapMatch = mapMatch();
        assertThrows(UnsupportedOperationException.class, () -> mapMatch.setItem(0, new WordItem("baz", "key")));
        assertThrows(UnsupportedOperationException.class, () -> mapMatch.setItem(1, new WordItem("baz", "value")));
        var iterator = mapMatch.getItemIterator();
        iterator.next();
        assertThrows(UnsupportedOperationException.class, () -> iterator.set(new WordItem("baz", "key")));
        assertUnchanged(mapMatch);
    }

    @Test
    void testChildrenCannotBeRemoved() {
        var mapMatch = mapMatch();
        assertThrows(UnsupportedOperationException.class, () -> mapMatch.removeItem(0));
        assertThrows(UnsupportedOperationException.class, () -> mapMatch.removeItem(mapMatch.valueItem()));
        var iterator = mapMatch.getItemIterator();
        iterator.next();
        assertThrows(UnsupportedOperationException.class, iterator::remove);
        assertUnchanged(mapMatch);
    }

    @Test
    void testIsNotEqualToTheEquivalentSameElement() {
        assertNotEquals(sameElement(), mapMatch());
        assertNotEquals(mapMatch(), sameElement());
    }

    @Test
    void testEqualsAndHashCode() {
        assertEquals(mapMatch(), mapMatch());
        assertEquals(mapMatch().hashCode(), mapMatch().hashCode());

        assertNotEquals(mapMatch(), new MapMatchItem("othermap", new WordItem("foo", "key"), new IntItem("42", "value")));
        assertNotEquals(mapMatch(), new MapMatchItem("mymap", new WordItem("bar", "key"), new IntItem("42", "value")));
        assertNotEquals(mapMatch(), new MapMatchItem("mymap", new WordItem("foo", "key"), new IntItem("43", "value")));
    }

    /** A MapMatchItem is sent to the backend as the equivalent sameElement. */
    @Test
    void testEncodedAsTheEquivalentSameElement() {
        assertEquals(sameElement().getItemType(), mapMatch().getItemType());
        assertArrayEquals(encode(sameElement()), encode(mapMatch()));
        assertEquals(ToProtobuf.convertFromQuery(sameElement()), ToProtobuf.convertFromQuery(mapMatch()));
    }

    private static MapMatchItem mapMatch() {
        return new MapMatchItem("mymap", new WordItem("foo", "key"), new IntItem("42", "value"));
    }

    private static SameElementItem sameElement() {
        var sameElement = new SameElementItem("mymap");
        sameElement.addItem(new WordItem("foo", "key"));
        sameElement.addItem(new IntItem("42", "value"));
        return sameElement;
    }

    private static void assertUnchanged(MapMatchItem mapMatch) {
        assertEquals(2, mapMatch.getItemCount());
        assertEquals("mymap:{key:foo value:42}", mapMatch.toString());
        assertSame(mapMatch, mapMatch.keyItem().getParent());
        assertSame(mapMatch, mapMatch.valueItem().getParent());
    }

    private static byte[] encode(Item item) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        item.encode(buffer, SerializationContext.ignored());
        return Arrays.copyOf(buffer.array(), buffer.position());
    }

}
