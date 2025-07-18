// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import com.yahoo.api.annotations.Beta;

/**
 * Represents a filter expression that negates the subexpression.
 *
 * @author johsol
 */
@Beta
public class NotPredicate extends FilterExpression {

    private final FilterExpression expression;

    public NotPredicate(FilterExpression expression) {
        this.expression = expression;
    }

    public FilterExpression getExpression() { return expression; }

    @Override public String toString() { return "not(%s)".formatted(expression); }
    @Override public FilterExpression copy() { return new NotPredicate(expression.copy()); }
}