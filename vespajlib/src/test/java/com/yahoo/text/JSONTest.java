// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class JSONTest {

    @Test
    public void testMapToString() {
        Map<String,Object> map = new LinkedHashMap<>();
        map.put("a \"key\"", 3);
        map.put("key2", "value");
        map.put("key3", 3.3);

        assertEquals("{\"a \\\"key\\\"\":3,\"key2\":\"value\",\"key3\":3.3}", JSON.encode(map));
    }

    @Test
    public void testEquals() {
        assertTrue(JSON.equals("{}", "{}"));

        // Whitespace is irrelevant
        assertTrue(JSON.equals("{}", "\n{ }"));

        // Order of fields in object is irrelevant
        assertTrue(JSON.equals("{\"a\":0, \"c\":1}", "{\"c\":1, \"a\":0}"));

        // Order of elements of array is significant
        assertFalse(JSON.equals("[\"a\",\"b\"]", "[\"b\",\"a\"]"));

        // Verify null-valued fields are not ignored
        assertFalse(JSON.equals("{\"a\":null}", "{}"));

        // Current impl uses BigInteger if integer doesn't fit in a long.
        assertEquals(9223372036854775807L, Long.MAX_VALUE);
        assertTrue(JSON.equals("{\"a\": 9223372036854775807}", "{\"a\": 9223372036854775807}"));

        // double 1.0 and int 1 are different
        assertTrue(JSON.equals( "{\"a\": 1}",  "{\"a\": 1}"));
        assertTrue(JSON.equals( "{\"a\": 1.0}",  "{\"a\": 1.0}"));
        assertFalse(JSON.equals( "{\"a\": 1.0}",  "{\"a\": 1}"));

        // Double-precision on numbers. Constant from Math.E.
        assertTrue(JSON.equals( "{\"e\": 2.71828182845904}",  "{\"e\": 2.71828182845904}"));

        // Justification of above float values
        double e1 = 2.7182818284590452354;
        double e2 = 2.718281828459045;
        double e3 = 2.71828182845904;
        assertEquals(e1, Math.E, -1);
        assertEquals(e1, e2, -1);
        assertNotEquals(e1, e3, -1);

        // Impl uses BigInteger
        assertTrue(JSON.equals( "{\"a\": 92233720368547758070}",
                                "{\"a\": 92233720368547758070}"));
        assertFalse(JSON.equals("{\"a\": 92233720368547758070}",
                                "{\"a\": 92233720368547758071}"));

        // Impl converts to double and ignores extraneous digits
        assertTrue(JSON.equals( "{\"e\": 2.7182818284590452354}",
                                "{\"e\": 2.7182818284590452354}"));
        assertTrue(JSON.equals( "{\"e\": 2.7182818284590452354}",
                                "{\"e\": 2.7182818284590452355}"));
        assertFalse(JSON.equals("{\"e\": 2.7182818284590452354}",
                                "{\"e\": 2.71828182845904}"));

        // Misc impl defined results
        assertFalse(JSON.equals("{\"a\": 1.0}", "{\"a\":1}"));
        assertTrue(JSON.equals("{\"a\": 1.0}", "{\"a\":1.00}"));
        assertTrue(JSON.equals("{\"a\": 1.0}", "{\"a\":1.0000000000000000000000000000}"));
        assertTrue(JSON.equals("{\"a\": 10.0}", "{\"a\":1e1}"));
        assertTrue(JSON.equals("{\"a\": 1.2}", "{\"a\":12e-1}"));
    }

}
