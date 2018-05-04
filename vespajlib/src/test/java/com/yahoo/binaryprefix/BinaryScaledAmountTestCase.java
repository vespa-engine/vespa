// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.binaryprefix;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author tonytv
 */
public class BinaryScaledAmountTestCase {

    @Test
    public void testConversion() {
        BinaryScaledAmount oneMeg = new BinaryScaledAmount(1024, BinaryPrefix.kilo);

        assertEquals(1, oneMeg.as(BinaryPrefix.mega));
        assertEquals(1024, oneMeg.as(BinaryPrefix.kilo));
        assertEquals(1024*1024, oneMeg.as(BinaryPrefix.unit));
        assertEquals(1 << 20, oneMeg.hashCode());

        Object v = this;
        assertEquals(false, oneMeg.equals(v));
        v = new BinaryScaledAmount(1, BinaryPrefix.mega);
        assertEquals(true, oneMeg.equals(v));
    }

    @Test
    public void testSymbols() {
        BinaryScaledAmount oneMeg = new BinaryScaledAmount(1024, BinaryPrefix.kilo);

        assertEquals(1, oneMeg.as(BinaryPrefix.fromSymbol('M')));
        assertEquals(1024, oneMeg.as(BinaryPrefix.fromSymbol('K')));

        boolean ex = false;
        try {
            BinaryPrefix invalid = BinaryPrefix.fromSymbol('q');
        } catch (RuntimeException e) {
            ex = true;
        }
        assertEquals(true, ex);
    }

}
