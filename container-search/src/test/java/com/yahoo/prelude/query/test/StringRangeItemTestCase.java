// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.test;

import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.StringRangeItem;
import com.yahoo.prelude.query.Substring;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StringRangeItemTestCase {

    @Test
    void testStringRangeConstruction() {
        StringRangeItem range = new StringRangeItem("aaa", true, "zzz", true, "index", true, null);
        assertEquals("aaa", range.getFrom());
        assertTrue(range.isFromInclusive());
        assertEquals("zzz", range.getTo());
        assertTrue(range.isToInclusive());
        assertEquals("[\"aaa\";\"zzz\"]", range.getIndexedString());
        assertEquals("[\"aaa\";\"zzz\"]", range.getRawWord()); // fallback when no origin provided
        assertEquals(Item.ItemType.STRING_RANGE, range.getItemType());
        assertEquals("STRING_RANGE", range.getName());
        assertEquals(1, range.getNumWords());
        assertTrue(range.isStemmed());
        assertFalse(range.isWords());

        range = new StringRangeItem(null, true, null, false, "index", true, null);
        assertNull(range.getFrom());
        assertFalse(range.isFromInclusive()); // Auto-corrected to false
        assertNull(range.getTo());
        assertFalse(range.isToInclusive());
        assertEquals("<-Infinity;Infinity>", range.getIndexedString());
        assertEquals("<-Infinity;Infinity>", range.getRawWord()); // fallback when no origin provided
        assertEquals(Item.ItemType.STRING_RANGE, range.getItemType());
        assertEquals("STRING_RANGE", range.getName());
        assertEquals(1, range.getNumWords());
        assertTrue(range.isStemmed());
        assertFalse(range.isWords());
    }


    @Test
    void testStringRemembersOrigin() {
        StringRangeItem range = new StringRangeItem("aaa", true, "zzz", true, "index", true, new Substring("range(\"aaa\", \"zzz\")"));
        assertEquals("range(\"aaa\", \"zzz\")", range.getRawWord());
    }
}
