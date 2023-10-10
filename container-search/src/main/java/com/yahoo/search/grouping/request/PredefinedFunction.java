// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class represents a predefined bucket-function in a {@link GroupingExpression}. It maps the input into one of the
 * given buckets by the result of the argument expression.
 *
 * @author Simon Thoresen Hult
 */
public abstract class PredefinedFunction extends FunctionNode {

    protected PredefinedFunction(String label, Integer level, GroupingExpression exp, List<? extends BucketValue> args) {
        super("predefined", label, level, asList(exp, args));
        Iterator<? extends BucketValue> it = args.iterator();
        BucketValue prev = it.next();
        while (it.hasNext()) {
            BucketValue arg = it.next();
            if (prev.compareTo(arg) >= 0) {
                throw new IllegalArgumentException("Buckets must be monotonically increasing, got " + prev +
                                                   " before " + arg + ".");
            }
            prev = arg;
        }
    }

    /**
     * Returns the number of buckets to divide the result into.
     *
     * @return The bucket count.
     */
    public int getNumBuckets() {
        return getNumArgs() - 1;
    }

    /**
     * Returns the bucket at the given index.
     *
     * @param i The index of the bucket to return.
     * @return The bucket at the given index.
     * @throws IndexOutOfBoundsException If the index is out of range.
     */
    public BucketValue getBucket(int i) {
        return (BucketValue)getArg(i + 1);
    }

    private static List<GroupingExpression> asList(GroupingExpression exp, List<? extends BucketValue> args) {
        List<GroupingExpression> ret = new LinkedList<>();
        ret.add(exp);
        ret.addAll(args);
        return ret;
    }

}

