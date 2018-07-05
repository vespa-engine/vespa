// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.List;

/**
 * This class represents a predefined bucket-function in a {@link GroupingExpression} for expressions that evaluate to a
 * string.
 *
 * @author Simon Thoresen Hult
 */
public class StringPredefined extends PredefinedFunction {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp  The expression to evaluate, must evaluate to a string.
     * @param arg1 The compulsory bucket.
     * @param argN The optional buckets.
     */
    public StringPredefined(GroupingExpression exp, StringBucket arg1, StringBucket... argN) {
        this(exp, asList(arg1, argN));
    }

    private StringPredefined(GroupingExpression exp, List<StringBucket> args) {
        super(exp, args);
    }

    @Override
    public StringBucket getBucket(int i) {
        return (StringBucket)getArg(i + 1);
    }

    /**
     * Constructs a new instance of this class from a list of arguments.
     *
     * @param exp  The expression to evaluate, must evaluate to a string.
     * @param args The buckets to pass to the constructor.
     * @return The created instance.
     * @throws IllegalArgumentException Thrown if the list of buckets is empty.
     */
    public static StringPredefined newInstance(GroupingExpression exp, List<StringBucket> args) {
        if (args.isEmpty()) {
            throw new IllegalArgumentException("Expected at least one bucket, got none.");
        }
        return new StringPredefined(exp, args);
    }
}
