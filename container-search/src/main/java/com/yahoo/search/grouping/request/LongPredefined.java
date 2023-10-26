// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This class represents a predefined bucket-function in a {@link GroupingExpression} for expressions that evaluate to a
 * long.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class LongPredefined extends PredefinedFunction {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp  The expression to evaluate, must evaluate to a long.
     * @param arg1 The compulsory bucket.
     * @param argN The optional buckets.
     */
    public LongPredefined(GroupingExpression exp, LongBucket arg1, LongBucket... argN) {
        this(null, null, exp, asList(arg1, argN));
    }

    private LongPredefined(String label, Integer level, GroupingExpression exp, List<LongBucket> args) {
        super(label, level, exp, args);
    }

    @Override
    public LongPredefined copy() {
        return new LongPredefined(getLabel(),
                                  getLevelOrNull(),
                                  getArg(0),
                                  args().stream()
                                        .skip(1)
                                        .map(LongBucket.class::cast)
                                        .map(arg -> arg.copy())
                                        .toList());
    }

    @Override
    public LongBucket getBucket(int i) {
        return (LongBucket)getArg(i + 1);
    }

    /**
     * Constructs a new instance of this class from a list of arguments.
     *
     * @param exp  The expression to evaluate, must evaluate to a long.
     * @param args The buckets to pass to the constructor.
     * @return The created instance.
     * @throws IllegalArgumentException Thrown if the list of buckets is empty.
     */
    public static LongPredefined newInstance(GroupingExpression exp, List<LongBucket> args) {
        if (args.isEmpty()) {
            throw new IllegalArgumentException("Expected at least one bucket, got none.");
        }
        return new LongPredefined(null, null, exp, args);
    }
}
