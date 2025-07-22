// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import com.yahoo.api.annotations.Beta;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a logical conjunction (AND) of filter expressions used to match grouping elements.
 *
 * @author johsol
 */
@Beta
public class AndPredicate extends FilterExpression {
    private final List<FilterExpression> args;

    public AndPredicate(List<FilterExpression> args) {
        this.args = args;
    }

    public List<FilterExpression> getArgs() {
        return args;
    }

    @Override
    public String toString() {
        return "and(" + args.stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ")) + ")";
    }

    @Override
    public FilterExpression copy() {
        return new AndPredicate(args.stream().map(FilterExpression::copy).toList());
    }
}
