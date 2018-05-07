// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Basic consistency check of BobHash implementation
 *
 * @author Steinar Knutsen
 */
public class BobHashTestCase {

    @Test
    public void testit() {
        // Teststring: minprice
        // Basic ASCII string
        byte[] minprice = { 109, 105, 110, 112, 114, 105, 99, 101 };

        assertEquals(BobHash.hash(minprice, 0), 0x90188543);
        // Teststring: a\u00FFa\u00FF
        // String with non-ASCII characters
        byte[] ayay = { 97, -1, 97, -1 };

        assertEquals(BobHash.hash(ayay, 0), 0x1C798331);
        // lots of a's to ensure testing unsigned type emulation
        byte[] aa = {
            97, 97, 97, 97, 97, 97, 97, 97, 97, 97, 97, 97, 97, 97, 97,
            97, 97, 97, 97, 97, 97, 97, 97, 97, 97, 97, 97, 97 };

        assertEquals(BobHash.hash(aa, 0), 0xE09ED5E9);
        // A string which caused problems during developmen of another
        // feature
        byte[] lastnamefirstinitial = {
            0x6c, 0x61, 0x73, 0x74, 0x6e, 0x61, 0x6d,
            0x65, 0x66, 0x69, 0x72, 0x73, 0x74, 0x69, 0x6e, 0x69, 0x74, 0x69,
            0x61, 0x6c };

        assertEquals(BobHash.hash(lastnamefirstinitial, 0), 0xF36B4BD3);
    }

}
