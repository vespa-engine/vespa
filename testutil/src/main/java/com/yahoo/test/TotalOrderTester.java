// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

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

public class TotalOrderTester<T extends Comparable<T>> extends OrderTester<T> {
    protected void lessTest(T a, T b) throws AssertionError {
        assertThat(a + " must be less than " + b, a.compareTo(b), lessThanOrEqualTo(-1));
    }

    protected void greaterTest(T a, T b) throws AssertionError {
        assertThat(a + " must be greater than " + b, a.compareTo(b), greaterThanOrEqualTo(1));
    }

    protected void equalTest(T a, T b) throws AssertionError {
        assertThat(a + " must be compared equal to " + b, a.compareTo(b), is(0));
    }
}
