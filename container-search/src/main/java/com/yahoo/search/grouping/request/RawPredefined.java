// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This class represents a predefined bucket-function in a {@link GroupingExpression} for expressions that evaluate to a
 * raw.
 *
 * @author Ulf Lilleengen
 */
public class RawPredefined extends PredefinedFunction {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp  The expression to evaluate, must evaluate to a string.
     * @param arg1 The compulsory bucket.
     * @param argN The optional buckets.
     */
    public RawPredefined(GroupingExpression exp, RawBucket arg1, RawBucket... argN) {
        this(null, null, exp, asList(arg1, argN));
    }

    private RawPredefined(String label, Integer level, GroupingExpression exp, List<RawBucket> args) {
        super(label, level, exp, args);
    }

    @Override
    public RawPredefined copy() {
        return new RawPredefined(getLabel(),
                                 getLevelOrNull(),
                                 getArg(0),
                                 args().stream()
                                       .skip(1)
                                       .map(RawBucket.class::cast)
                                       .map(arg -> arg.copy())
                                       .toList());
    }

    @Override
    public RawBucket getBucket(int i) {
        return (RawBucket)getArg(i + 1);
    }

    /**
     * Constructs a new instance of this class from a list of arguments.
     *
     * @param exp  The expression to evaluate, must evaluate to a string.
     * @param args The buckets to pass to the constructor.
     * @return The created instance.
     * @throws IllegalArgumentException Thrown if the list of buckets is empty.
     */
    public static RawPredefined newInstance(GroupingExpression exp, List<RawBucket> args) {
        if (args.isEmpty()) {
            throw new IllegalArgumentException("Expected at least one bucket, got none.");
        }
        return new RawPredefined(null, null, exp, args);
    }
}
