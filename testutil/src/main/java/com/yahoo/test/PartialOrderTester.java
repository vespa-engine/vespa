// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.test;

import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

/**
 * PartialOrderTester implements a partial order test for OrderTester
 *
 * Usage: see {@link com.yahoo.test.TotalOrderTester}
 *
 * @author Vegard Sjonfjell
 */

public class PartialOrderTester<T extends Comparable<T>> extends OrderTester<T> {
    protected void lessTest(T a, T b) throws AssertionError {
        assertThat(a + " must be less than or equal to " + b, a.compareTo(b), lessThanOrEqualTo(0));
    }

    protected void greaterTest(T a, T b) throws AssertionError {
        assertThat(a + " must be greater than or equal to " + b, a.compareTo(b), greaterThanOrEqualTo(0));
    }

    protected void equalTest(T a, T b) throws AssertionError {
        assertThat(a + " must be compared equal to " + b, a.compareTo(b), is(0));
    }
}
