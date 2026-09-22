// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import com.yahoo.api.annotations.Beta;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a filter expression that matches when the string value of the evaluated
 * expression is equal to one of the string arguments.
 *
 * @author johsol
 */
@Beta
public class InPredicate extends FilterExpression {

    private final GroupingExpression expression;
    private final List<String> args;

    public InPredicate(GroupingExpression expression, List<String> args) {
        if (args == null || args.isEmpty()) {
            throw new IllegalArgumentException("InPredicate requires args to contain at least one element.");
        }
        validateExpression(expression);
        this.expression = expression;
        this.args = List.copyOf(args);
    }

    private static void validateExpression(GroupingExpression exp) {
        // Fail in obviously invalid expressions
        if (exp instanceof BucketValue) {
            throw new IllegalArgumentException("In predicate cannot be used with a bucket value");
        } else if (exp instanceof AggregatorNode) {
            throw new IllegalArgumentException("In predicate cannot be used with an aggregator");
        } else if (exp instanceof PredefinedFunction) {
            throw new IllegalArgumentException("In predicate cannot be used with a predefined function");
        } else if (exp instanceof FixedWidthFunction) {
            throw new IllegalArgumentException("In predicate cannot be used with a fixed width function");
        }
    }

    public GroupingExpression getExpression() {
        return expression;
    }

    public List<String> getArgs() {
        return args;
    }

    @Override
    public String toString() {
        return "in(" + expression + ", " + args.stream()
                .map(arg -> "\"" + arg + "\"")
                .collect(Collectors.joining(", ")) + ")";
    }

    @Override
    public FilterExpression copy() {
        return new InPredicate(expression.copy(), args);
    }
}
