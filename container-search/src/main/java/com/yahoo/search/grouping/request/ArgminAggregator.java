// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents an argmin-aggregator in a {@link GroupingExpression}. It evaluates to the value of the
 * contained expression for the input whose key expression evaluated to the smallest value.
 *
 * @author johsol
 */
public class ArgminAggregator extends AggregatorNode {

    private final GroupingExpression key;

    /**
     * Constructs a new instance of this class.
     */
    public ArgminAggregator(GroupingExpression key, GroupingExpression expression) {
        this(null, null, key, expression);
    }

    private ArgminAggregator(String label, Integer level, GroupingExpression key, GroupingExpression expression) {
        super("argmin", label, level, expression);
        this.key = key;
    }

    /**
     * Returns the expression that this node minimizes.
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
    public ArgminAggregator copy() {
        return new ArgminAggregator(getLabel(), getLevelOrNull(), key.copy(), getExpression().copy());
    }

    @Override
    public String toString() {
        return "argmin(" + key + ", " + getExpression() + ")";
    }
}
