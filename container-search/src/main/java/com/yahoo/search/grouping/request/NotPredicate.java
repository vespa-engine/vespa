// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import com.yahoo.api.annotations.Beta;

import java.util.Objects;

/**
 * Represents a logical negation (NOT) of a filter expression used to exclude grouping elements.
 *
 * @author johsol
 */
@Beta
public class NotPredicate extends FilterExpression {
    private final FilterExpression expression;

    public NotPredicate(FilterExpression expression) {
        Objects.requireNonNull(expression, "Expression cannot be null");
        this.expression = expression;
    }

    public FilterExpression getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return "(not %s".formatted(expression) + ")";
    }

    @Override
    public FilterExpression copy() {
        return new NotPredicate(expression.copy());
    }
}
