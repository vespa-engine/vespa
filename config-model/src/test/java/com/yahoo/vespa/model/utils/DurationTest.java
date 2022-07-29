// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class DurationTest {
    @Test
    void testDurationUnits() {
        assertEquals(1000, new Duration("1").getMilliSeconds());
        assertEquals(2.0, new Duration("2").getSeconds(), 0.0001);
        assertEquals(1, new Duration("1ms").getMilliSeconds());
        assertEquals(2000, new Duration("2s").getMilliSeconds());
        assertEquals(5 * 60 * 1000, new Duration("5m").getMilliSeconds());
        assertEquals(3 * 60 * 60 * 1000, new Duration("3h").getMilliSeconds());
        assertEquals(24 * 60 * 60 * 1000, new Duration("1d").getMilliSeconds());

        assertEquals(1400, new Duration("1.4s").getMilliSeconds());
        assertEquals(1400, new Duration("1.4 s").getMilliSeconds());
    }

    private void assertException(String str) {
        try {
            new Duration(str);
            fail("Exception not thrown for string: " + str);
        } catch (Exception e) {
        }
    }

    @Test
    void testParseError() {
        assertException("bjarne");
        assertException("");
        assertException("1 foo");
        assertException("1.5 bar");
        assertException("-5");
    }
}
