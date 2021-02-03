// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A min-function in a {@link GroupingExpression}. It evaluates to a number that equals the
 * smallest of the results of all arguments.
 *
 * @author Simon Thoresen Hult
 */
public class MinFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param arg1 the first compulsory argument, must evaluate to a number
     * @param arg2 the second compulsory argument, must evaluate to a number
     * @param argN the optional arguments, must evaluate to a number
     */
    public MinFunction(GroupingExpression arg1, GroupingExpression arg2, GroupingExpression... argN) {
        this(null, null, asList(arg1, arg2, argN));
    }

    private MinFunction(String label, Integer level, List<GroupingExpression> args) {
        super("min", label, level, args);
    }

    @Override
    public MinFunction copy() {
        return new MinFunction(getLabel(),
                               getLevelOrNull(),
                               args().stream().map(arg -> arg.copy()).collect(Collectors.toList()));
    }

    /**
     * Constructs a new instance of this class from a list of arguments.
     *
     * @param args The arguments to pass to the constructor.
     * @return The created instance.
     * @throws IllegalArgumentException Thrown if the number of arguments is less than 2.
     */
    public static MinFunction newInstance(List<GroupingExpression> args) {
        if (args.size() < 2) {
            throw new IllegalArgumentException("Expected 2 or more arguments, got " + args.size() + ".");
        }
        return new MinFunction(null, null, args);
    }
}

