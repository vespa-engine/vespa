// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * OrderTester is a an abstract helper class in the spirit of EqualsTester that
 * tests an objects total or partial ordering with respect to T#compareTo.
 *
 * @author Vegard Sjonfjell
 * @see com.yahoo.test.TotalOrderTester
 * @see com.yahoo.test.PartialOrderTester
 *
 */

public abstract class OrderTester<T extends Comparable<? super T>> {
    private final ArrayList<List<T>> groups = new ArrayList<>();

    abstract protected void lessTest(T a, T b);
    abstract protected void greaterTest(T a, T b);
    abstract protected void equalTest(T a, T b);

    @SafeVarargs
    @SuppressWarnings("varargs")
    private OrderTester<T> addGroup(T... group) {
        groups.add(Arrays.asList(group));
        return this;
    }

    /**
     * Add group of objects being "less" (wrt. compareTo) than all the objects which follow.
     * @param group group of objects
     * @return the {@link OrderTester} instance, for method chaining
     */
    @SafeVarargs
    public final OrderTester<T> theseObjects(T... group) {
        return addGroup(group);
    }

    /**
     * Add group of objects being "less" (wrt. compareTo) than all the objects which follow.
     * @param group group of objects
     * @return the {@link OrderTester} instance, for method chaining
     */
    @SafeVarargs
    public final OrderTester<T> areLessThan(T... group) {
        return addGroup(group);
    }

    /**
     * Test the ordering defined with {@link OrderTester#theseObjects} and {@link OrderTester#areLessThan}
     * with respect to T#compareTo and the {@link OrderTester} subclass (e.g. {@link com.yahoo.test.TotalOrderTester}).
     */
    public void testOrdering() {
        for (int i = 0; i < groups.size(); i++) {
            for (T item : groups.get(i)) {
                for (T otherItem : groups.get(i)) {
                    equalTest(item, otherItem);
                }
            }

            for (int j = i+1; j < groups.size(); j++) {
                for (T lessItem : groups.get(i)) {
                    for (T greaterItem : groups.get(j)) {
                        lessTest(lessItem, greaterItem);
                        greaterTest(greaterItem, lessItem);
                    }
                }
            }
        }
    }
}
