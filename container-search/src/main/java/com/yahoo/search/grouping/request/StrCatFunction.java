// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.List;

/**
 * This class represents a strcat-function in a {@link GroupingExpression}. It evaluates to a string that equals the
 * contatenation of the string results of all arguments in the order they were given to the constructor.
 *
 * @author Simon Thoresen Hult
 */
public class StrCatFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param arg1 The first compulsory argument, must evaluate to a string.
     * @param arg2 The second compulsory argument, must evaluate to a string.
     * @param argN The optional arguments, must evaluate to a string.
     */
    public StrCatFunction(GroupingExpression arg1, GroupingExpression arg2, GroupingExpression... argN) {
        this(asList(arg1, arg2, argN));
    }

    private StrCatFunction(List<GroupingExpression> args) {
        super("strcat", args);
    }

    /**
     * Constructs a new instance of this class from a list of arguments.
     *
     * @param args The arguments to pass to the constructor.
     * @return The created instance.
     * @throws IllegalArgumentException Thrown if the number of arguments is less than 2.
     */
    public static StrCatFunction newInstance(List<GroupingExpression> args) {
        if (args.size() < 2) {
            throw new IllegalArgumentException("Expected 2 or more arguments, got " + args.size() + ".");
        }
        return new StrCatFunction(args);
    }
}

