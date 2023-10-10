// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.treenet.rule;

import java.util.Iterator;

/**
 * Represents a condition
 *
 * @author  bratseth
 */
public abstract class Condition extends TreeNode {

    private final String leftValue;
    private final String trueLabel;
    private final String falseLabel;

    public Condition(String leftValue, String trueLabel, String falseLabel) {
        this.leftValue = leftValue;
        this.trueLabel = trueLabel;
        this.falseLabel = falseLabel;
    }

    /** Returns the name of the feature to compare to a constant. */
    public String getLeftValue() { return leftValue; }

    /** Return the label to jump to if this condition is true. */
    public String getTrueLabel() { return trueLabel; }

    /** Return the label to jump to if this condition is false. */
    public String getFalseLabel() { return falseLabel; }

    @Override
    public final String toRankingExpression() {
        StringBuilder b = new StringBuilder("if (");
        b.append(getLeftValue());
        b.append(" ");
        b.append(conditionToRankingExpression());
        b.append(", ");
        b.append(getParent().getNodes().get(getTrueLabel()).toRankingExpression());
        b.append(", ");
        b.append(getParent().getNodes().get(getFalseLabel()).toRankingExpression());
        b.append(")");
        return b.toString();
    }

    /**
     * Returns the ranking expression string for the condition part of this condition, i.e the ... part of
     * <pre>
     *     if(leftValue ..., trueExpression, falseExpression)
     * </pre>
     */
    protected abstract String conditionToRankingExpression();

}
