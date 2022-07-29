// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.test;

import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.RangeItem;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RangeItemTestCase {

    @Test
    void testRangeConstruction() {
        verifyRange(new RangeItem(5, 7, 9, "a", true), 9, true);
        verifyRange(new RangeItem(5, 7, "a", true), 0, true);
        verifyRange(new RangeItem(5, 7, "a"), 0, false);
    }

    private void verifyRange(RangeItem range, int limit, boolean isFromQuery) {
        assertEquals(5, range.getFrom());
        assertEquals(7, range.getTo());
        assertEquals(limit, range.getHitLimit());
        assertEquals("a", range.getIndexName());
        if (range.getHitLimit() != 0) {
            assertEquals("[5;7;9]", range.getNumber());
        } else {
            assertEquals("[5;7]", range.getNumber());
        }
        assertEquals(isFromQuery, range.isFromQuery());
        ByteBuffer buffer = ByteBuffer.allocate(128);
        int count = range.encode(buffer);
        ByteBuffer buffer2 = ByteBuffer.allocate(128);
        int count2 = new IntItem(range.getNumber(), range.getIndexName(), range.isFromQuery()).encode(buffer2);
        assertEquals(buffer, buffer2);
    }

}
