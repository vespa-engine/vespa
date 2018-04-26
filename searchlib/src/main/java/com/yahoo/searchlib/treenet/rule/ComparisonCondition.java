// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.treenet.rule;

/**
 * Represents a condition which comparing two values
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class ComparisonCondition extends Condition {

    private final double rhs;

    /**
     * Constructs a new instance of this class.
     *
     * @param lhs The name of the feature to compare to a constant.
     * @param rhs The constant to compare the feature with.
     * @param ift The label to jump to if left &lt; right.
     * @param iff The label to jump to if left &gt;= right;
     */
    public ComparisonCondition(String lhs, double rhs, String ift, String iff) {
        super(lhs, ift, iff);
        this.rhs = rhs;
    }

    /**
     * Returns the constant to compare the feature with.
     *
     * @return The constant.
     */
    public double getConstant() { return rhs; }

    @Override
    public String conditionToRankingExpression() {
        return "< " + String.valueOf(rhs);
    }
}
