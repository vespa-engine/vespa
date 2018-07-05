// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.List;

/**
 * This class represents a mod-function in a {@link GroupingExpression}. It evaluates to a number that equals the result
 * of mod'ing the results of all arguments in the order they were given to the constructor (modulo first argument by
 * second, result by third, ...).
 *
 * @author Simon Thoresen Hult
 */
public class ModFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param arg1 The first compulsory argument, must evaluate to a number.
     * @param arg2 The second compulsory argument, must evaluate to a number.
     * @param argN The optional arguments, must evaluate to a number.
     */
    public ModFunction(GroupingExpression arg1, GroupingExpression arg2, GroupingExpression... argN) {
        this(asList(arg1, arg2, argN));
    }

    private ModFunction(List<GroupingExpression> args) {
        super("mod", args);
    }

    /**
     * Constructs a new instance of this class from a list of arguments.
     *
     * @param args The arguments to pass to the constructor.
     * @return The created instance.
     * @throws IllegalArgumentException Thrown if the number of arguments is less than 2.
     */
    public static ModFunction newInstance(List<GroupingExpression> args) {
        if (args.size() < 2) {
            throw new IllegalArgumentException("Expected 2 or more arguments, got " + args.size() + ".");
        }
        return new ModFunction(args);
    }
}
