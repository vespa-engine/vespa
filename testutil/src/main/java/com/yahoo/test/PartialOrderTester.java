// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.test;

/**
 * PartialOrderTester implements a partial order test for OrderTester
 *
 * Usage: see {@link com.yahoo.test.TotalOrderTester}
 *
 * @author Vegard Sjonfjell
 */

public class PartialOrderTester<T extends Comparable<T>> extends OrderTester<T> {
    protected void lessTest(T a, T b) throws AssertionError {
        JunitCompat.assertTrue(a + " must be less than or equal to " + b, a.compareTo(b) <= 0);
    }

    protected void greaterTest(T a, T b) throws AssertionError {
        JunitCompat.assertTrue(a + " must be greater than or equal to " + b, a.compareTo(b) >= 0);
    }

    protected void equalTest(T a, T b) throws AssertionError {
        JunitCompat.assertEquals(a + " must be compared equal to " + b, 0, a.compareTo(b));
    }
}
