// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.test;

import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.SerializationContext;
import com.yahoo.prelude.query.StringRangeItem;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author boeker
 */
public class StringRangeItemTestCase {

    @Test
    void testConstruction() {
        StringRangeItem range = new StringRangeItem("apple", "pear", "fruit");
        assertEquals("apple", range.getFrom());
        assertEquals("pear", range.getTo());
        assertTrue(range.isFromInclusive());
        assertTrue(range.isToInclusive());
        assertEquals("fruit", range.getIndexName());
        assertEquals(Item.ItemType.STRING_RANGE, range.getItemType());
        assertEquals(34, range.getCode());
    }

    @Test
    void testUnboundedEnds() {
        StringRangeItem upwards = new StringRangeItem("apple", null, "fruit");
        assertEquals("apple", upwards.getFrom());
        assertNull(upwards.getTo());

        StringRangeItem downwards = new StringRangeItem(null, "pear", "fruit");
        assertNull(downwards.getFrom());
        assertEquals("pear", downwards.getTo());
    }

    @Test
    void testToString() {
        assertEquals("fruit:[\"apple\";\"pear\"]", new StringRangeItem("apple", "pear", "fruit").toString());
        assertEquals("fruit:<\"apple\";\"pear\">",
                     new StringRangeItem("apple", false, "pear", false, "fruit").toString());
        assertEquals("fruit:<\"apple\";\"pear\"]",
                     new StringRangeItem("apple", false, "pear", true, "fruit").toString());
        assertEquals("fruit:[\"apple\";]", new StringRangeItem("apple", null, "fruit").toString());
        assertEquals("fruit:[;\"pear\"]", new StringRangeItem(null, "pear", "fruit").toString());
        assertEquals("fruit:[;]", new StringRangeItem(null, null, "fruit").toString());
    }

    /** Quotes and backslashes in a bound must not be confusable with the syntax of the expression. */
    @Test
    void testBoundsAreEscaped() {
        assertEquals("fruit:[\"a\\\"b\";\"c\\\\d\"]",
                     new StringRangeItem("a\"b", "c\\d", "fruit").toString());
        assertEquals("fruit:[\"a\\\";\\\"b\";]",
                     new StringRangeItem("a\";\"b", null, "fruit").toString());
    }

    @Test
    void testEquality() {
        assertEquals(new StringRangeItem("apple", "pear", "fruit"), new StringRangeItem("apple", "pear", "fruit"));
        assertEquals(new StringRangeItem("apple", "pear", "fruit").hashCode(),
                     new StringRangeItem("apple", "pear", "fruit").hashCode());
        assertNotEquals(new StringRangeItem("apple", "pear", "fruit"),
                        new StringRangeItem("apple", "pear", "vegetable"));
        assertNotEquals(new StringRangeItem("apple", "pear", "fruit"),
                        new StringRangeItem("apple", false, "pear", true, "fruit"));
        assertNotEquals(new StringRangeItem("apple", null, "fruit"),
                        new StringRangeItem("apple", "", "fruit"));
    }

    @Test
    void testCloning() {
        StringRangeItem range = new StringRangeItem("apple", false, "pear", true, "fruit");
        Item clone = range.clone();
        assertEquals(range, clone);
        assertFalse(range == clone);
    }

    /** The legacy query stack format has no representation of string ranges, so the expression is sent as a term. */
    @Test
    void testEncodingToTheQueryStackWritesTheExpressionAsATerm() {
        StringRangeItem range = new StringRangeItem("apple", "pear", "fruit");
        ByteBuffer buffer = ByteBuffer.allocate(128);
        assertEquals(1, range.encode(buffer, new SerializationContext(1.0)));
        String encoded = new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8);
        assertTrue(encoded.contains("fruit"), encoded);
        assertTrue(encoded.endsWith("[\"apple\";\"pear\"]"), encoded);
    }

    /** A range has no single value which can be set from a string. */
    @Test
    void testSetValueIsRejected() {
        StringRangeItem range = new StringRangeItem("apple", "pear", "fruit");
        assertThrows(UnsupportedOperationException.class, () -> range.setValue("apple"));
    }

    /** No linguistic processing applies to the bounds of a range. */
    @Test
    void testIsNotSubjectToLinguisticProcessing() {
        StringRangeItem range = new StringRangeItem("apple", "pear", "fruit");
        assertFalse(range.isWords());
        assertFalse(range.isNormalizable());
        assertTrue(range.isStemmed());
    }

}
