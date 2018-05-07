// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.geo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for the ZCurve class.
 *
 * @author gjoranv
 */
public class ZCurveTestCase {

    /**
     * Verify that encoded values return the expected bit pattern
     */
    @Test
    public void testEncoding() {
        int x = 0;
        int y = 0;
        long z = ZCurve.encode(x, y);
        assertEquals(0, z);

        x = Integer.MAX_VALUE;
        y = Integer.MAX_VALUE;
        z = ZCurve.encode(x, y);
        assertEquals(0x3fffffffffffffffL, z);

        x = Integer.MIN_VALUE;
        y = Integer.MIN_VALUE;
        z = ZCurve.encode(x, y);
        assertEquals(0xc000000000000000L, z);

        x = Integer.MIN_VALUE;
        y = Integer.MAX_VALUE;
        z = ZCurve.encode(x, y);
        assertEquals(0x6aaaaaaaaaaaaaaaL, z);

        x = -1;
        y = -1;
        z = ZCurve.encode(x, y);
        assertEquals(0xffffffffffffffffL, z);

        x = Integer.MAX_VALUE / 2;
        y = Integer.MIN_VALUE / 2;
        z = ZCurve.encode(x, y);
        assertEquals(0xa555555555555555L, z);
    }

    /**
     * Verify that decoded values are equal to inputs in different cases
     */
    @Test
    public void testDecoding() {
        int x = 0;
        int y = 0;
        long z = ZCurve.encode(x, y);
        int[] xy = ZCurve.decode(z);
        assertEquals(x, xy[0]);
        assertEquals(y, xy[1]);

        x = Integer.MAX_VALUE;
        y = Integer.MAX_VALUE;
        z = ZCurve.encode(x, y);
        xy = ZCurve.decode(z);
        assertEquals(x, xy[0]);
        assertEquals(y, xy[1]);

        x = Integer.MIN_VALUE;
        y = Integer.MIN_VALUE;
        z = ZCurve.encode(x, y);
        xy = ZCurve.decode(z);
        assertEquals(x, xy[0]);
        assertEquals(y, xy[1]);

        x = Integer.MIN_VALUE;
        y = Integer.MAX_VALUE;
        z = ZCurve.encode(x, y);
        xy = ZCurve.decode(z);
        assertEquals(x, xy[0]);
        assertEquals(y, xy[1]);

        x = -18;
        y = 1333;
        z = ZCurve.encode(x, y);
        xy = ZCurve.decode(z);
        assertEquals(x, xy[0]);
        assertEquals(y, xy[1]);

        x = -1333;
        y = 18;
        z = ZCurve.encode(x, y);
        xy = ZCurve.decode(z);
        assertEquals(x, xy[0]);
        assertEquals(y, xy[1]);
    }

    /**
     * Verify that encoded values return the expected bit pattern
     */
    @Test
    public void testEncoding_slow() {
        int x = 0;
        int y = 0;
        long z = ZCurve.encode_slow(x, y);
        assertEquals(0, z);

        x = Integer.MIN_VALUE;
        y = Integer.MIN_VALUE;
        z = ZCurve.encode_slow(x, y);
        assertEquals(0xc000000000000000L, z);

        x = Integer.MIN_VALUE;
        y = Integer.MAX_VALUE;
        z = ZCurve.encode_slow(x, y);
        assertEquals(0x6aaaaaaaaaaaaaaaL, z);

        x = Integer.MAX_VALUE;
        y = Integer.MAX_VALUE;
        z = ZCurve.encode_slow(x, y);
        assertEquals(0x3fffffffffffffffL, z);

        x = -1;
        y = -1;
        z = ZCurve.encode_slow(x, y);
        assertEquals(0xffffffffffffffffL, z);

        x = Integer.MAX_VALUE / 2;
        y = Integer.MIN_VALUE / 2;
        z = ZCurve.encode_slow(x, y);
        assertEquals(0xa555555555555555L, z);
    }

    /**
     * Verify that decoded values are equal to inputs in different cases
     */
    @Test
    public void testDecoding_slow() {
        int x = 0;
        int y = 0;
        long z = ZCurve.encode_slow(x, y);
        int[] xy = ZCurve.decode_slow(z);
        assertEquals(xy[0], x);
        assertEquals(xy[1], y);

        x = Integer.MAX_VALUE;
        y = Integer.MAX_VALUE;
        z = ZCurve.encode_slow(x, y);
        xy = ZCurve.decode_slow(z);
        assertEquals(xy[0], x);
        assertEquals(xy[1], y);

        x = Integer.MIN_VALUE;
        y = Integer.MIN_VALUE;
        z = ZCurve.encode_slow(x, y);
        xy = ZCurve.decode_slow(z);
        assertEquals(xy[0], x);
        assertEquals(xy[1], y);

        x = Integer.MIN_VALUE;
        y = Integer.MAX_VALUE;
        z = ZCurve.encode_slow(x, y);
        xy = ZCurve.decode_slow(z);
        assertEquals(xy[0], x);
        assertEquals(xy[1], y);

        x = -18;
        y = 1333;
        z = ZCurve.encode_slow(x, y);
        xy = ZCurve.decode_slow(z);
        assertEquals(xy[0], x);
        assertEquals(xy[1], y);

        x = -1333;
        y = 18;
        z = ZCurve.encode_slow(x, y);
        xy = ZCurve.decode_slow(z);
        assertEquals(xy[0], x);
        assertEquals(xy[1], y);
    }

    @Test
    public void testBenchmarkEncoding() {
        int limit = 2000000;

        long z1 = 0L;
        long start = System.currentTimeMillis();
        for (int i=0; i<limit; i++) {
            z1 += ZCurve.encode(i,-i);
        }
        long elapsed = System.currentTimeMillis() - start;
        //System.out.println("Fast method: elapsed time: " + elapsed + " ms");
        //System.out.println("Per encoding: " + elapsed/(1.0*limit) * 1000000 + " ns");

        long z2 = 0L;
        start = System.currentTimeMillis();
        for (int i=0; i<limit; i++) {
            z2 += ZCurve.encode_slow(i,-i);
        }
        elapsed = System.currentTimeMillis() - start;
        //System.out.println("Slow method: elapsed time: " + elapsed + " ms");
        //System.out.println("Per encoding: " + elapsed/(1.0*limit) * 1000000 + " ns");
        assertEquals(z1, z2);
    }

}
