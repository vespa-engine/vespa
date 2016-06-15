// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.time;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

public class WallClockSourceTestCase {

    @Test
    public void testSimple() {
        long actualBefore = System.currentTimeMillis();
        WallClockSource clock = new WallClockSource();
        long nanos = clock.currentTimeNanos();
        long micros = nanos / 1000;
        long millis = micros / 1000;
        long actualAfter = System.currentTimeMillis();

        assertTrue(actualBefore <= millis);
        assertTrue(millis <= actualAfter);
    }

    @Test
    public void testWithAdjust() {
        WallClockSource clock = new WallClockSource();
        long diffB = 0;
        long diffA = 0;
        for (int i = 0; i < 66666; i++) {
            long actualB = System.currentTimeMillis();
            clock.adjust();
            long nanos = clock.currentTimeNanos();
            long actualA = System.currentTimeMillis();
            long micros = nanos / 1000;
            long millis = micros / 1000;
            diffB = Math.max(diffB, actualB - millis);
            diffA = Math.max(diffA, millis - actualA);
            // System.out.println("adj Timing values, before: "+actualB+" <= guess: "+millis+" <= after: "+actualA);
        }
        System.out.println("adjust test: biggest difference (beforeTime - guess): "+diffB);
        System.out.println("adjust test: biggest difference (guess - afterTime): "+diffA);
        assertTrue("actual time before sample must be <= wallclocksource, diff: " + diffB, diffB < 2);
        assertTrue("actual time  after sample must be >= wallclocksource, diff: " + diffA, diffA < 2);
    }

    @Test
    public void testNoAdjust() {
        WallClockSource clock = new WallClockSource();
        long diffB = 0;
        long diffA = 0;
        for (int i = 0; i < 66666; i++) {
            long actualB = System.currentTimeMillis();
            long nanos = clock.currentTimeNanos();
            long actualA = System.currentTimeMillis();
            long micros = nanos / 1000;
            long millis = micros / 1000;
            diffB = Math.max(diffB, actualB - millis);
            diffA = Math.max(diffA, millis - actualA);
            // System.out.println("noadj Timing values, before: "+actualB+" <= guess: "+millis+" <= after: "+actualA);
        }
        System.out.println("noadjust test: biggest difference (beforeTime - guess): "+diffB);
        System.out.println("noadjust test: biggest difference (guess - afterTime): "+diffA);
        assertTrue("actual time before sample must be <= wallclocksource, diff: " + diffB, diffB < 3);
        assertTrue("actual time  after sample must be >= wallclocksource, diff: " + diffA, diffA < 3);
    }

    @Test
    public void testAutoAdjust() {
        WallClockSource clock = WallClockSource.get();
        long diffB = 0;
        long diffA = 0;
        for (int i = 0; i < 66666; i++) {
            long actualB = System.currentTimeMillis();
            long nanos = clock.currentTimeNanos();
            long actualA = System.currentTimeMillis();
            long micros = nanos / 1000;
            long millis = micros / 1000;
            diffB = Math.max(diffB, actualB - millis);
            diffA = Math.max(diffA, millis - actualA);
            // System.out.println("noadj Timing values, before: "+actualB+" <= guess: "+millis+" <= after: "+actualA);
        }
        System.out.println("autoadjust test: biggest difference (beforeTime - guess): "+diffB);
        System.out.println("autoadjust test: biggest difference (guess - afterTime): "+diffA);
        assertTrue("actual time before sample must be <= wallclocksource, diff: " + diffB, diffB < 3);
        assertTrue("actual time  after sample must be >= wallclocksource, diff: " + diffA, diffA < 3);
    }

}
