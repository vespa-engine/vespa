// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This class represents a predefined bucket-function in a {@link GroupingExpression} for expressions that evaluate to a
 * double.
 *
 * @author Simon Thoresen Hult
 */
public class DoublePredefined extends PredefinedFunction {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp  The expression to evaluate, must evaluate to a double.
     * @param arg1 The compulsory bucket.
     * @param argN The optional buckets.
     */
    public DoublePredefined(GroupingExpression exp, DoubleBucket arg1, DoubleBucket... argN) {
        this(null, null, exp, asList(arg1, argN));
    }

    private DoublePredefined(String label, Integer level, GroupingExpression exp, List<DoubleBucket> args) {
        super(label, level, exp, args);
    }

    @Override
    public DoublePredefined copy() {
        return new DoublePredefined(getLabel(),
                                    getLevelOrNull(),
                                    getArg(0),
                                    args().stream().skip(1)
                                                   .map(DoubleBucket.class::cast)
                                                   .map(arg -> arg.copy())
                                                   .toList());
    }

    @Override
    public DoubleBucket getBucket(int i) {
        return (DoubleBucket)getArg(i + 1);
    }

    /**
     * Constructs a new instance of this class from a list of arguments.
     *
     * @param exp  The expression to evaluate, must evaluate to a double.
     * @param args The buckets to pass to the constructor.
     * @return The created instance.
     * @throws IllegalArgumentException Thrown if the list of buckets is empty.
     */
    public static DoublePredefined newInstance(GroupingExpression exp, List<DoubleBucket> args) {
        if (args.isEmpty()) {
            throw new IllegalArgumentException("Expected at least one bucket, got none.");
        }
        return new DoublePredefined(null, null, exp, args);
    }
}
