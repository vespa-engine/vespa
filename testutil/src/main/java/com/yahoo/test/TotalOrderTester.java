// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.test;

/**
 * TotalOrderTester implements a total order test for OrderTester
 *
 * Usage:
 * <code>
 * new TotalOrderTester&lt;Integer&gt;()
 *      .theseObjects(3, 3)
 *      .areLessThan(4)
 *      .areLessThan(5)
 *      .areLessThan(6)
 *      .testOrdering();
 * </code>
 *
 * @author Vegard Sjonfjell
 */

public class TotalOrderTester<T extends Comparable<? super T>> extends OrderTester<T> {
    protected void lessTest(T a, T b) throws AssertionError {
        JunitCompat.assertTrue(a + " must be less than " + b, a.compareTo(b) <= -1);
    }

    protected void greaterTest(T a, T b) throws AssertionError {
        JunitCompat.assertTrue(a + " must be greater than " + b, a.compareTo(b) >= 1);
    }

    protected void equalTest(T a, T b) throws AssertionError {
        JunitCompat.assertEquals(a + " must be compared equal to " + b, 0, a.compareTo(b));
    }
}
