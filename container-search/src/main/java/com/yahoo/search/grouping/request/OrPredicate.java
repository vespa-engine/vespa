// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import com.yahoo.api.annotations.Beta;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a logical disjunction (OR) of filter expressions used to match grouping elements.
 *
 * @author johsol
 */
@Beta
public class OrPredicate extends FilterExpression {
    private final List<FilterExpression> args;

    public OrPredicate(List<FilterExpression> args) {
        if (args == null || args.size() < 2) {
            throw new IllegalArgumentException("OrPredicate requires args to contain at least two elements.");
        }
        this.args = args;
    }

    public List<FilterExpression> getArgs() {
        return args;
    }

    @Override
    public String toString() {
        return args.stream()
                .map(Object::toString)
                .collect(Collectors.joining(" or "));
    }

    @Override
    public FilterExpression copy() {
        return new OrPredicate(args.stream().map(FilterExpression::copy).toList());
    }
}
