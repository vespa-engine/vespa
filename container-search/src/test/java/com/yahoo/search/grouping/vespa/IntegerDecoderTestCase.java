// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class IntegerDecoderTestCase {

    @Test
    void requireThatIntDecoderWorksAsExpected() {
        assertDecode("A", 0);
        assertDecode("BC", 1);
        assertDecode("CBI", 12);
        assertDecode("CPG", 123);
        assertDecode("DJKE", 1234);
        assertDecode("EGAHC", 12345);
        assertDecode("FDMEIA", 123456);
        assertDecode("GCFKNAO", 1234567);
        assertDecode("HBHIMCJM", 12345678);
        assertDecode("HOLHJKCK", 123456789);
        assertDecode("IJDCMAFKE", 1234567890);
        assertDecode("IIKKEBPOF", -1163005939);
        assertDecode("IECKEIKID", -559039810);
    }

    @Test
    void requireThatDecoderThrowsExceptionOnBadInput() {
        try {
            new IntegerDecoder("B").next();
            fail();
        } catch (IndexOutOfBoundsException e) {

        }
        try {
            new IntegerDecoder("11X1Y").next();
            fail();
        } catch (NumberFormatException e) {

        }
    }

    private static void assertDecode(String toDecode, int expected) {
        IntegerDecoder decoder = new IntegerDecoder(toDecode);
        assertTrue(decoder.hasNext());
        assertEquals(expected, decoder.next());
        assertFalse(decoder.hasNext());
    }
}
