// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt.slobrok.api;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author arnej27959
 */
public class BackOffTestCase {

    static final double[] expectWait = {
        0.5, 1.0, 1.5, 2.0, 2.5,
        3.0, 3.5, 4.0, 4.5,
        5.0, 6.0, 7.0, 8.0, 9.0,
        10, 15, 20, 25, 30, 30, 30
    };

    @Test
    public void requireThatWaitTimesAreExpected() {
        double sum = 0;
        BackOffPolicy two = new BackOff();
        for (int i = 0; i < expectWait.length; i++) {
            double got = two.get();
            sum += got;
            assertEquals(expectWait[i], got, 0.001);
            boolean sw = two.shouldWarn(got);
/*
            System.err.println("i = "+i);
            System.err.println("got = "+got);
            System.err.println("sum = "+sum);
            System.err.println("sw = "+sw);
*/
            if (i == 13 || i > 17) {
                assertTrue(two.shouldWarn(got));
            } else {
                assertFalse(two.shouldWarn(got));
            }
        }
        two.reset();
        for (int i = 0; i < expectWait.length; i++) {
            double got = two.get();
            assertEquals(expectWait[i], got, 0.001);
            if (i == 13 || i > 17) {
                assertTrue(two.shouldWarn(got));
            } else {
                assertFalse(two.shouldWarn(got));
            }
        }

    }
}
