// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents an argmax-aggregator in a {@link GroupingExpression}. It evaluates to the value of the
 * contained expression for the input whose key expression evaluated to the largest value.
 *
 * @author johsol
 */
public class ArgmaxAggregator extends AggregatorNode {

    private final GroupingExpression key;

    /**
     * Constructs a new instance of this class.
     */
    public ArgmaxAggregator(GroupingExpression key, GroupingExpression expression) {
        this(null, null, key, expression);
    }

    private ArgmaxAggregator(String label, Integer level, GroupingExpression key, GroupingExpression expression) {
        super("argmax", label, level, expression);
        this.key = key;
    }

    /**
     * Returns the expression that this node maximizes.
     */
    public GroupingExpression getKey() {
        return key;
    }

    @Override
    public void resolveLevel(int level) {
        super.resolveLevel(level);
        key.resolveLevel(level - 1);
    }

    @Override
    public void visit(ExpressionVisitor visitor) {
        super.visit(visitor);
        key.visit(visitor);
    }

    @Override
    public ArgmaxAggregator copy() {
        return new ArgmaxAggregator(getLabel(), getLevelOrNull(), key.copy(), getExpression().copy());
    }

    @Override
    public String toString() {
        return "argmax(" + key + ", " + getExpression() + ")";
    }
}
