// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This class represents an add-function in a {@link GroupingExpression}. It evaluates to a number that equals the
 * result of adding the results of all arguments together in the order they were given to the constructor.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class AddFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param arg1 The first compulsory argument, must evaluate to a number.
     * @param arg2 The second compulsory argument, must evaluate to a number.
     * @param argN The optional arguments, must evaluate to a number.
     */
    public AddFunction(GroupingExpression arg1, GroupingExpression arg2, GroupingExpression... argN) {
        this(null, null, asList(arg1, arg2, argN));
    }

    private AddFunction(String label, Integer level, List<GroupingExpression> args) {
        super("add", label, level, args);
    }

    @Override
    public AddFunction copy() {
        return new AddFunction(getLabel(),
                               getLevelOrNull(),
                               args().stream().map(arg -> arg.copy()).toList());
    }

    /**
     * Constructs a new instance of this class from a list of arguments.
     *
     * @param args the arguments to pass to the constructor.
     * @return the created instance.
     * @throws IllegalArgumentException thrown if the number of arguments is less than 2.
     */
    public static AddFunction newInstance(List<GroupingExpression> args) {
        if (args.size() < 2) {
            throw new IllegalArgumentException("Expected 2 or more arguments, got " + args.size() + ".");
        }
        return new AddFunction(null, null, args);
    }
}
