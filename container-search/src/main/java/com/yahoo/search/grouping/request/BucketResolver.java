// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.LinkedList;
import java.util.List;

/**
 * This is a helper class for resolving buckets to a list of
 * {@link GroupingExpression} objects. To resolve a list simply
 * {@link #push(ConstantValue, boolean)} onto it, before calling
 * {@link #resolve(GroupingExpression)} to retrieve the list of corresponding
 * grouping expression object.
 *
 * @author Simon Thoresen Hult
 */
public class BucketResolver {

    private final List<BucketValue> buckets = new LinkedList<>();
    private ConstantValue<?> prev = null;
    private boolean previnclusive = false;
    private int idx = 0;

    /**
     * Pushes the given expression onto this bucket resolver. Once all buckets have been pushed using this method, call
     * {@link #resolve(GroupingExpression)} to retrieve to combined grouping expression.
     *
     * @param val the expression to push
     * @param inclusive whether or not the value is inclusive or not
     * @throws IllegalArgumentException thrown if the expression is incompatible
     */
    public BucketResolver push(ConstantValue<?> val, boolean inclusive) {
        if (prev == null) {
            prev = val;
        } else if (!(prev instanceof InfiniteValue || val instanceof InfiniteValue)
                   && !prev.getClass().equals(val.getClass())) {
            throw new IllegalArgumentException("Bucket type mismatch, expected '" + prev.getClass().getSimpleName() +
                                               "' got '" + val.getClass().getSimpleName() + "'.");
        } else if (prev instanceof InfiniteValue && val instanceof InfiniteValue) {
            throw new IllegalArgumentException("Bucket type mismatch, cannot both be infinity.");
        }
        if ((++idx % 2) == 0) {
            ConstantValue<?> begin = previnclusive ? prev : nextValue(prev);
            ConstantValue<?> end = inclusive ? nextValue(val) : val;
            if (begin instanceof DoubleValue || end instanceof DoubleValue) {
                buckets.add(new DoubleBucket(begin, end));
            } else if (begin instanceof LongValue || end instanceof LongValue) {
                buckets.add(new LongBucket(begin, end));
            } else if (begin instanceof StringValue || end instanceof StringValue) {
                buckets.add(new StringBucket(begin, end));
            } else if (begin instanceof RawValue || end instanceof RawValue) {
                buckets.add(new RawBucket(begin, end));
            } else {
                throw new UnsupportedOperationException("Bucket type '" + val.getClass() + "' not supported.");
            }
        }
        prev = val;
        previnclusive = inclusive;
        return this;
    }

    /**
     * Resolves and returns the list of grouping expressions that correspond to the previously pushed buckets.
     *
     * @param exp The expression to assign to the function.
     * @return The list corresponding to the pushed buckets.
     */
    public PredefinedFunction resolve(GroupingExpression exp) {
        if ((idx % 2) == 1) {
            throw new IllegalStateException("Missing to-limit of last bucket.");
        }
        int len = buckets.size();
        if (len == 0) {
            throw new IllegalStateException("Expected at least one bucket, got none.");
        }
        ConstantValue<?> begin = buckets.get(0).getFrom();
        ConstantValue<?> end = buckets.get(0).getTo();
        if (begin instanceof DoubleValue || end instanceof DoubleValue) {
            if (len == 1) {
                return new DoublePredefined(exp, (DoubleBucket)buckets.get(0));
            } else {
                return new DoublePredefined(exp, (DoubleBucket)buckets.get(0),
                                            buckets.subList(1, len).toArray(new DoubleBucket[len - 1]));
            }
        } else if (begin instanceof LongValue || end instanceof LongValue) {
            if (len == 1) {
                return new LongPredefined(exp, (LongBucket)buckets.get(0));
            } else {
                return new LongPredefined(exp, (LongBucket)buckets.get(0),
                                          buckets.subList(1, len).toArray(new LongBucket[len - 1]));
            }
        } else if (begin instanceof StringValue || end instanceof StringValue) {
            if (len == 1) {
                return new StringPredefined(exp, (StringBucket)buckets.get(0));
            } else {
                return new StringPredefined(exp, (StringBucket)buckets.get(0),
                                            buckets.subList(1, len).toArray(new StringBucket[len - 1]));
            }
        } else if (begin instanceof RawValue || end instanceof RawValue) {
            if (len == 1) {
                return new RawPredefined(exp, (RawBucket)buckets.get(0));
            } else {
                return new RawPredefined(exp, (RawBucket)buckets.get(0),
                                            buckets.subList(1, len).toArray(new RawBucket[len - 1]));
            }
        }
        throw new UnsupportedOperationException("Bucket type '" + begin.getClass() + "' not supported.");
    }

    private ConstantValue<?> nextValue(ConstantValue<?> value) {
        if (value instanceof LongValue) {
            return LongBucket.nextValue((LongValue)value);
        } else if (value instanceof DoubleValue) {
            return DoubleBucket.nextValue((DoubleValue)value);
        } else if (value instanceof StringValue) {
            return StringBucket.nextValue((StringValue)value);
        } else if (value instanceof RawValue) {
            return RawBucket.nextValue((RawValue)value);
        }
        return value;
    }
}
