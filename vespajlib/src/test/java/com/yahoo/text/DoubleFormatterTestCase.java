// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * @author arnej27959
 */
public class DoubleFormatterTestCase {

    @Test
    public void testZero() {
        String zero = DoubleFormatter.stringValue(0.0);
        //assertEquals("0.0", zero);
    }

    @Test
    public void testOne() {
        String one = DoubleFormatter.stringValue(1.0);
        assertEquals("1.0", one);
    }

    @Test
    public void testMinusOne() {
        String one = DoubleFormatter.stringValue(-1.0);
        assertEquals("-1.0", one);
    }

    @Test
    public void testNanInf() {
        String plusInf = DoubleFormatter.stringValue(Double.POSITIVE_INFINITY);
        assertEquals("Infinity", plusInf);

        String notAnum = DoubleFormatter.stringValue(Double.NaN);
        assertEquals("NaN", notAnum);

        String negInf = DoubleFormatter.stringValue(Double.NEGATIVE_INFINITY);
        assertEquals("-Infinity", negInf);
    }

    @Test
    public void testSeven() {
        String seven = DoubleFormatter.stringValue(7.0);
        assertEquals("7.0", seven);

        seven = DoubleFormatter.stringValue(77.0);
        assertEquals("77.0", seven);

        seven = DoubleFormatter.stringValue(7777.0);
        assertEquals("7777.0", seven);

        seven = DoubleFormatter.stringValue(7777007777.0);
        assertEquals("7.777007777E9", seven);
    }


    @Test
    public void testSomeChosenNumbers() {
        String s = DoubleFormatter.stringValue(4097.0);
        assertEquals("4097.0", s);

        s = DoubleFormatter.stringValue(4097.5);
        assertEquals("4097.5", s);

        s = DoubleFormatter.stringValue(1073741823.0);
        assertEquals("1.073741823E9", s);

        s = DoubleFormatter.stringValue(1073741823.5);
        assertEquals("1.0737418235E9", s);

        s = DoubleFormatter.stringValue(1073741825.5);
        assertEquals("1.0737418255E9", s);

        s = DoubleFormatter.stringValue(1.23456789012345669);
        assertEquals("1.234567890123457", s);
        s = DoubleFormatter.stringValue(12.3456789012345673);
        assertEquals("12.34567890123457", s);
        s = DoubleFormatter.stringValue(123.456789012345666);
        assertEquals("123.4567890123457", s);
        s = DoubleFormatter.stringValue(1234.56789012345666);
        assertEquals("1234.567890123457", s);
        s = DoubleFormatter.stringValue(12345.6789012345670);
        assertEquals("12345.67890123457", s);
        s = DoubleFormatter.stringValue(123456.789012345674);
        assertEquals("123456.7890123457", s);
        s = DoubleFormatter.stringValue(1234567.89012345671);
        assertEquals("1234567.890123457", s);

        s = DoubleFormatter.stringValue(0.99);
        // assertEquals("0.99", s);

        s = DoubleFormatter.stringValue(0.5);
        assertEquals("0.5", s);

        s = DoubleFormatter.stringValue(0.1);
        // assertEquals("0.1", s);

        s = DoubleFormatter.stringValue(0.00123456789);
        // assertEquals("0.00123456789", s);

        s = DoubleFormatter.stringValue(0.0000000000001);
        // assertEquals("0.0000000000001", s);
    }

    @Test
    public void testPowersOfTwo() {
        String twos = DoubleFormatter.stringValue(2.0);
        assertEquals("2.0", twos);

        twos = DoubleFormatter.stringValue(128.0);
        assertEquals("128.0", twos);

        twos = DoubleFormatter.stringValue(1048576.0);
        assertEquals("1048576.0", twos);

        twos = DoubleFormatter.stringValue(1073741824.0);
        assertEquals("1.073741824E9", twos);
    }

    @Test
    public void testSmallNumbers() {
        for (double d = 1.0; d > 1.0e-200; d *= 0.75) {
            String fs = DoubleFormatter.stringValue(d);
            String vs = String.valueOf(d);
            double rp = Double.valueOf(fs);
            if (d != rp) {
                // System.err.println("differs: "+d+" became "+fs+" then instead: "+rp+" diff: "+(d-rp));
            } else if (! fs.equals(vs)) {
                // System.err.println("string rep differs: "+vs+" became "+fs);
            }
            assertEquals(d, rp, 1.0e-7*d);
        }
    }

    @Test
    public void testVerySmallNumbers() {
        for (double d = 1.0; d > 1.0e-200; d *= 0.5) {
            String fs = DoubleFormatter.stringValue(d);
            String vs = String.valueOf(d);
            double rp = Double.valueOf(fs);
            if (d != rp) {
                // System.err.println("differs: "+d+" became "+fs+" then instead: "+rp+" diff: "+(d-rp));
            } else if (! fs.equals(vs)) {
                // System.err.println("string rep differs: "+vs+" became "+fs);
            }
            assertEquals(d, rp, 1.0e-13*d);
        }
    }

    @Test
    public void testVeryVerySmallNumbers() {
        for (double d = 1.0e-200; d > 0; d *= 0.5) {
            String fs = DoubleFormatter.stringValue(d);
            String vs = String.valueOf(d);
            double rp = Double.valueOf(fs);
            if (d != rp) {
                // System.err.println("differs: "+d+" became "+fs+" then instead: "+rp+" diff: "+(d-rp));
            } else if (! fs.equals(vs)) {
                // System.err.println("string rep differs: "+vs+" became "+fs);
            }
            assertEquals(d, rp, 1.0e-13*d);
        }
    }

    @Test
    public void testVeryBigNumbers() {
        for (double d = 1.0; d < Double.POSITIVE_INFINITY; d *= 2.0) {
            String fs = DoubleFormatter.stringValue(d);
            String vs = String.valueOf(d);
            double rp = Double.valueOf(fs);
            if (d != rp) {
                // System.err.println("differs: "+d+" became "+fs+" then instead: "+rp);
            } else if (! fs.equals(vs)) {
                // System.err.println("string rep differs: "+vs+" became "+fs);
            }
            assertEquals(d, rp, 1.0e-13*d);
        }

        assertEquals("1.0E200", String.valueOf(1.0e+200));

        String big = DoubleFormatter.stringValue(1.0e+200);
        assertEquals("1.0E200", big);

        big = DoubleFormatter.stringValue(1.0e+298);
        assertEquals("1.0E298", big);

        big = DoubleFormatter.stringValue(1.0e+299);
        assertEquals("1.0E299", big);

        big = DoubleFormatter.stringValue(1.0e+300);
        assertEquals("1.0E300", big);

    }

    @Test
    public void testRandomNumbers() {
        java.util.Random rgen = new java.util.Random(0xCafeBabe);
        for (int i = 0; i < 123456; i++) {
            double d = rgen.nextDouble();
        }
    }

}
