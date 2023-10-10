// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.test;

import org.junit.Test;

/**
 * @author Vegard Sjonfjell
 */
public class OrderTesterTest {
    private class PartialInt implements Comparable<PartialInt>  {
        int value;

        public PartialInt(int value) {
            this.value = value;
        }

        @Override
        public int compareTo(PartialInt other) {
            if (Math.abs(value - other.value) < 3) {
                return 0;
            }
            else if (value > other.value)  {
                return 1;
            }
            else if (value < other.value)  {
                return 1;
            }

            return 0;
        }

        @Override
        public String toString() {
            return Integer.toString(value);
        }
    }

    @Test
    public void testTotalOrderTester() {
        new TotalOrderTester<Integer>()
                .theseObjects(3, 3)
                .areLessThan(4)
                .areLessThan(5)
                .areLessThan(6)
                .testOrdering();
    }

    @Test
    public void testPartialOrderTester() {
        new PartialOrderTester<PartialInt>()
                .theseObjects(new PartialInt(3))
                .areLessThan(new PartialInt(3), new PartialInt(3))
                .areLessThan(new PartialInt(4))
                .areLessThan(new PartialInt(4))
                .areLessThan(new PartialInt(5))
                .testOrdering();
    }

    @Test (expected = AssertionError.class)
    public void testTotalOrderTesterFailsOnIncorrectOrdering() {
        new TotalOrderTester<Integer>()
                .theseObjects(3)
                .areLessThan(2)
                .areLessThan(5)
                .testOrdering();
    }

    @Test (expected = AssertionError.class)
    public void testPartialOrderTesterFailsOnIncorrectOrdering() {
        new PartialOrderTester<PartialInt>()
                .theseObjects(new PartialInt(6))
                .areLessThan(new PartialInt(2))
                .areLessThan(new PartialInt(3))
                .testOrdering();
    }
}
