package com.yahoo.config.provision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bratseth
 */
public class IntRangeTestCase {

    @Test
    public void testStringAndEquals() {
        assertEquals(IntRange.empty(), IntRange.from(IntRange.from("[]").toString()));
        assertEquals(IntRange.from(1), IntRange.from(IntRange.from("[1,]").toString()));
        assertEquals(IntRange.to(3), IntRange.from(IntRange.from("[,3]").toString()));
        assertEquals(IntRange.of(1, 3), IntRange.from(IntRange.from("[1,3]").toString()));
        assertEquals(IntRange.of(1, 3), IntRange.from(IntRange.from("[1, 3]").toString()));
    }

    @Test
    public void testInclusion() {
        assertFalse(IntRange.of(3, 5).includes(2));
        assertTrue(IntRange.of(3, 5).includes(3));
        assertTrue(IntRange.of(3, 5).includes(4));
        assertTrue(IntRange.of(3, 5).includes(5));
        assertFalse(IntRange.of(3, 5).includes(6));

        assertTrue(IntRange.from(3).includes(1000));
        assertFalse(IntRange.from(3).includes(2));

        assertTrue(IntRange.to(5).includes(-1000));
        assertFalse(IntRange.to(3).includes(4));
    }

}
