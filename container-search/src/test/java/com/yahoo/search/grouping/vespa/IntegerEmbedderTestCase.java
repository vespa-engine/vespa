// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class IntegerEmbedderTestCase {

    @Test
    void requireThatIntEncoderWorksAsExpected() {
        assertEncode("A", 0);
        assertEncode("BC", 1);
        assertEncode("CBI", 12);
        assertEncode("CPG", 123);
        assertEncode("DJKE", 1234);
        assertEncode("EGAHC", 12345);
        assertEncode("FDMEIA", 123456);
        assertEncode("GCFKNAO", 1234567);
        assertEncode("HBHIMCJM", 12345678);
        assertEncode("HOLHJKCK", 123456789);
        assertEncode("IJDCMAFKE", 1234567890);
        assertEncode("IIKKEBPOF", -1163005939);
        assertEncode("IECKEIKID", -559039810);
    }

    private static void assertEncode(String expected, int toEncode) {
        IntegerEncoder actual = new IntegerEncoder();
        actual.append(toEncode);
        assertEquals(expected, actual.toString());
    }
}
