// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This class represents an xor-function in a {@link GroupingExpression}. It evaluates to a long that equals the result
 * of and'ing the results of all arguments together in the order they were given to the constructor.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class XorFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param arg1 The first compulsory argument, must evaluate to a long.
     * @param arg2 The second compulsory argument, must evaluate to a long.
     * @param argN The optional arguments, must evaluate to a long.
     */
    public XorFunction(GroupingExpression arg1, GroupingExpression arg2, GroupingExpression... argN) {
        this(null, null, asList(arg1, arg2, argN));
    }

    private XorFunction(String label, Integer level, List<GroupingExpression> args) {
        super("xor", label, level, args);
    }

    @Override
    public XorFunction copy() {
        return new XorFunction(getLabel(),
                               getLevelOrNull(),
                               args().stream().map(arg -> arg.copy()).toList());
    }

    /**
     * Constructs a new instance of this class from a list of arguments.
     *
     * @param args The arguments to pass to the constructor.
     * @return The created instance.
     * @throws IllegalArgumentException Thrown if the number of arguments is less than 2.
     */
    public static XorFunction newInstance(List<GroupingExpression> args) {
        if (args.size() < 2) {
            throw new IllegalArgumentException("Expected 2 or more arguments, got " + args.size() + ".");
        }
        return new XorFunction(null, null, args);
    }
}

