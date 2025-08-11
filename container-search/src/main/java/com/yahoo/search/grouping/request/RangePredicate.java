// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import com.yahoo.api.annotations.Beta;

/**
 * Represents a filter expression that matches a value from the evaluated expression within a range.
 *
 * @author johsol
 */
@Beta
public class RangePredicate extends FilterExpression {

    private final Number lower;
    private final Number upper;
    private final boolean lowerInclusive;
    private final boolean upperInclusive;
    private final GroupingExpression expression;

    public RangePredicate(Number lower, Number upper, GroupingExpression expression) {
        this(lower, upper, true, false, expression);
    }

    public RangePredicate(Number lower, Number upper, boolean lowerInclusive, boolean upperInclusive, GroupingExpression expression) {
        this.lower = lower;
        this.upper = upper;
        this.lowerInclusive = lowerInclusive;
        this.upperInclusive = upperInclusive;
        this.expression = expression;
    }

    public Number getLower() { return lower; }
    public Number getUpper() { return upper; }
    public boolean getLowerInclusive() { return lowerInclusive; }
    public boolean getUpperInclusive() { return upperInclusive; }
    public GroupingExpression getExpression() { return expression; }

    @Override
    public String toString() {
        return "range(%s, %s, %b, %b, %s)".formatted(lower, upper, lowerInclusive, upperInclusive, expression);
    }

    @Override
    public FilterExpression copy() {
        return new RangePredicate(lower, upper, lowerInclusive, upperInclusive, expression.copy());
    }
}
