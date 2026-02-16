// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import com.yahoo.api.annotations.Beta;

/**
 * Represents a filter that evaluates to true when its expression evaluates to a boolean true value.
 *
 * @author johsol
 */
@Beta
public class IsTruePredicate extends FilterExpression {

    private final GroupingExpression expression;

    public IsTruePredicate(GroupingExpression expression) {
        this.expression = expression;
    }

    public GroupingExpression getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return String.format("istrue(%s)", expression);
    }

    @Override
    public FilterExpression copy() {
        return new IsTruePredicate(expression.copy());
    }
}
