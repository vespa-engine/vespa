// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("deprecation")
/**
 * @author arnej27959
 */
public class DoubleParserTestCase {

    @Test
    public void testZero() {
        String[] zeros = {
            "0",
            "0.",
            ".0",
            "0.0",
            "0.0e0",
            "0.0e99",
            "0.0e+300",
            "0.0e-42"
        };
        for (String s : zeros) {
            double d = DoubleParser.parse(s);
            assertEquals(0.0, d, 0);
        }
    }

    @Test
    public void testOne() {
        String[] ones = {
            "1",
            "1.",
            "1.0",
            "+1",
            "10.0e-1",
            "0.1e1",
            "1000.0e-3",
            ".001e+3",
        };
        for (String s : ones) {
            System.out.println("parsing: '"+s+"' now");
            double d = DoubleParser.parse(s);
            System.out.println("expected: 1.0");
            System.out.println("actual: "+d);
            assertEquals(1.0, d, 0);
        }
    }

    @Test
    public void testMinusOne() {
        String[] numbers = {
            "-1",
            "-1.0",
            "-1.",
            "-1e0",
            "-10e-1",
        };
        for (String s : numbers) {
            System.out.println("parsing: '"+s+"' now");
            double d = DoubleParser.parse(s);
            System.out.println("expected: -1.0");
            System.out.println("actual: "+d);
            assertEquals(-1.0, d, 0);
        }
    }

    @Test
    public void testNanInf() {
        String[] numbers = {
            "NaN",
            "Infinity",
            "-Infinity",
            "+Infinity",
            "+NaN",
            "-NaN"
        };
        for (String s : numbers) {
            System.out.println("parsing: '"+s+"' now");
            double d1 = Double.parseDouble(s);
            double d2 = DoubleParser.parse(s);
            long lb1 = Double.doubleToRawLongBits(d1);
            long lb2 = Double.doubleToRawLongBits(d2);
            assertEquals(lb1, lb2, 0);
        }
    }

    @Test
    public void testSeven() {
        String[] sevens = {
            "7",
            "7.",
            "7.0",
            "70.0e-1",
            "0.7e1",
            "7000.0e-3",
            ".007e+3",
        };
        for (String s : sevens) {
            System.out.println("parsing: '"+s+"' now");
            double d = DoubleParser.parse(s);
            System.out.println("expected: 7.0");
            System.out.println("actual: "+d);
            assertEquals(7.0, d, 0);
        }
    }

    @Test
    public void testVerySmallNumbers() {
        String[] numbers = {
            "1.e-320",
            "-1.e-320",
            "1.0013378241589014e-303"
        };
        for (String s : numbers) {
            System.out.println("parsing: '"+s+"' now");
            double d1 = Double.parseDouble(s);
            double d2 = DoubleParser.parse(s);
            System.out.println("expected: "+d1);
            System.out.println("actual: "+d2);
            assertEquals(d1, d2, 0);
        }
    }

    @Test
    public void testRandomNumbers() {
        java.util.Random rgen = new java.util.Random(0xCafeBabe);
        for (int i = 0; i < 123456; i++) {
            double d = rgen.nextDouble();
            int exp = rgen.nextInt();
            d *= Math.pow(1.0000006, exp);
            String s = Double.toString(d);
            double d2 = Double.parseDouble(s);
            double d3 = DoubleParser.parse(s);

            if (d != d2) {
                System.out.println("WARNING: value ["+d+"] parses as ["+d2+"] by Java");
            }
            double allow = 1.0e-14 * d2;
            if (allow < 0) {
                allow = -allow;
            }
            if (d2 != d3) {
                long lb2 = Double.doubleToRawLongBits(d2);
                long lb3 = Double.doubleToRawLongBits(d3);
                if (lb2 - lb3 > 15 || lb3 - lb2 > 15) {
                    System.out.println("WARNING: string '"+s+"' parses as");
                    System.out.println("["+d2+"] by Java, ["+d3+"] by our method");
                    System.out.println("["+Long.toHexString(lb2)+"] bits vs ["+Long.toHexString(lb3)+"]");
                    System.out.println("==> "+(lb2 - lb3)+" <== diff value");
                }
            }
            assertEquals(d2, d3, allow);
        }
    }

}
