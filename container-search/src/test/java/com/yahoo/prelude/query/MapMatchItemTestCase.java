// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.search.Query;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertSame(mapMatch, key.getParent());
        assertSame(mapMatch, value.getParent());
        assertEquals("mymap:{key:foo value:42}", mapMatch.toString());
    }

    @Test
    void testKeyAndValueMayBeCompositeItems() {
        var phrase = new PhraseItem();
        phrase.setIndexName("value");
        phrase.addItem(new WordItem("new", "value"));
        phrase.addItem(new WordItem("york", "value"));
        var mapMatch = new MapMatchItem("mymap", new WordItem("foo", "key"), phrase);
        assertSame(phrase, mapMatch.valueItem());
        assertEquals("mymap.value", phrase.getFieldName());
        assertThrows(NullPointerException.class, () -> new MapMatchItem("mymap", null, new IntItem("42", "value")));
        assertThrows(NullPointerException.class, () -> new MapMatchItem("mymap", new WordItem("foo", "key"), null));
    }

    @Test
    void testChildrenSearchFieldsInsideTheMap() {
        var mapMatch = mapMatch();
        assertEquals("mymap", mapMatch.getFieldName());
        assertEquals("mymap.key", ((HasIndexItem) mapMatch.keyItem()).getFieldName());
        assertEquals("mymap.value", ((HasIndexItem) mapMatch.valueItem()).getFieldName());

        // Setting the index name renames the map field, not the children
        mapMatch.setIndexName("othermap");
        assertEquals("othermap:{key:foo value:42}", mapMatch.toString());
        assertEquals("othermap.key", ((HasIndexItem) mapMatch.keyItem()).getFieldName());
    }

    @Test
    void testCloneHasItsOwnKeyAndValue() {
        var mapMatch = new MapMatchItem("mymap", new WordItem("foo", "key"), new IntItem("42", "value"));
        var copy = mapMatch.clone();

        assertEquals(mapMatch, copy);
        assertNotSame(mapMatch.keyItem(), copy.keyItem());
        assertNotSame(mapMatch.valueItem(), copy.valueItem());
        assertSame(copy, copy.keyItem().getParent());
        assertSame(copy, copy.valueItem().getParent());

        // Changing the copy leaves the original alone
        ((WordItem) copy.keyItem()).setWord("bar");
        assertEquals("mymap:{key:foo value:42}", mapMatch.toString());
        assertEquals("mymap:{key:bar value:42}", copy.toString());
    }

    @Test
    void testCloneCannotGetExtraChildren() {
        var copy = mapMatch().clone();
        assertThrows(UnsupportedOperationException.class, () -> copy.addItem(new WordItem("baz", "other")));
        assertThrows(UnsupportedOperationException.class, () -> copy.getItemIterator().add(new WordItem("baz", "other")));
    }

    @Test
    void testChildrenCannotBeAdded() {
        var mapMatch = mapMatch();
        assertThrows(UnsupportedOperationException.class, () -> mapMatch.addItem(new WordItem("baz", "other")));
        assertThrows(UnsupportedOperationException.class, () -> mapMatch.addItem(0, new WordItem("baz", "other")));
        assertThrows(UnsupportedOperationException.class, () -> mapMatch.getItemIterator().add(new WordItem("baz", "other")));
        assertEquals(mapMatch(), mapMatch);
    }

    @Test
    void testChildrenCannotBeRemoved() {
        var mapMatch = mapMatch();
        assertThrows(UnsupportedOperationException.class, () -> mapMatch.removeItem(0));
        assertThrows(UnsupportedOperationException.class, () -> mapMatch.removeItem(mapMatch.valueItem()));
        var iterator = mapMatch.getItemIterator();
        iterator.next();
        assertThrows(UnsupportedOperationException.class, iterator::remove);
        assertEquals(mapMatch(), mapMatch);
    }

    /** Query rewriters, such as normalizing, may replace the key and value in place. */
    @Test
    void testChildrenCanBeReplaced() {
        var mapMatch = mapMatch();
        var key = new WordItem("bar", "key");
        mapMatch.setItem(0, key);
        assertSame(key, mapMatch.keyItem());
        assertSame(mapMatch, key.getParent());

        var value = new IntItem("43", "value");
        var iterator = mapMatch.getItemIterator();
        iterator.next();
        iterator.next();
        iterator.set(value);
        assertSame(value, mapMatch.valueItem());
        assertSame(mapMatch, value.getParent());
        assertEquals("mymap:{key:bar value:43}", mapMatch.toString());
    }

    @Test
    void testIsNotASameElement() {
        Item mapMatch = mapMatch();
        assertFalse(mapMatch instanceof SameElementItem);
    }

    /** The key and value are ranked as terms, so they get unique ids and their labels reach the backend. */
    @Test
    void testChildrenAreTagged() {
        var mapMatch = mapMatch();
        mapMatch.keyItem().setLabel("my_key");
        var query = new Query();
        query.getModel().getQueryTree().setRoot(mapMatch);
        query.prepare();

        var key = (TaggableItem) mapMatch.keyItem();
        var value = (TaggableItem) mapMatch.valueItem();
        assertTrue(key.getUniqueID() > 0);
        assertTrue(value.getUniqueID() > 0);
        assertNotEquals(key.getUniqueID(), value.getUniqueID());
        assertEquals(String.valueOf(key.getUniqueID()),
                     query.getRanking().getProperties().get("vespa.label.my_key.id").get(0));
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

        // Including its settings
        var sameElement = sameElement();
        sameElement.setWeight(150);
        sameElement.setRanked(false);
        var mapMatch = mapMatch();
        mapMatch.setWeight(150);
        mapMatch.setRanked(false);
        assertArrayEquals(encode(sameElement), encode(mapMatch));
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

    private static byte[] encode(Item item) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        item.encode(buffer, SerializationContext.ignored());
        return Arrays.copyOf(buffer.array(), buffer.position());
    }

}
