// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents a argmax-aggregator in a {@link GroupingExpression}.
 *
 * @author johsol
 */
public class ArgmaxAggregator extends AggregatorNode {

    private final GroupingExpression key;

    /**
     * Constructs a new instance of this class, selecting the highest ranked hit.
     *
     * @param expression the expression to aggregate on.
     */
    public ArgmaxAggregator(GroupingExpression expression) {
        this(null, null, expression, null);
    }

    /**
     * Constructs a new instance of this class, selecting the hit with the smallest key.
     *
     * @param expression the expression to aggregate on.
     * @param key the expression producing the ordering key.
     */
    public ArgmaxAggregator(GroupingExpression expression, GroupingExpression key) {
        this(null, null, expression, key);
    }

    private ArgmaxAggregator(String label, Integer level, GroupingExpression expression, GroupingExpression key) {
        super("argmax", label, level, expression);
        this.key = key;
    }

    /**
     * Returns the expression producing the ordering key, or null when selecting the highest ranked hit.
     *
     * @return The key expression, or null.
     */
    public GroupingExpression getKeyOrNull() {
        return key;
    }

    @Override
    public void resolveLevel(int level) {
        super.resolveLevel(level);
        if (key != null) {
            key.resolveLevel(level - 1);
        }
    }

    @Override
    public void visit(ExpressionVisitor visitor) {
        super.visit(visitor);
        if (key != null) {
            key.visit(visitor);
        }
    }

    @Override
    public ArgmaxAggregator copy() {
        return new ArgmaxAggregator(getLabel(), getLevelOrNull(), getExpression().copy(),
                                   key == null ? null : key.copy());
    }

    @Override
    public String toString() {
        if (key == null) {
            return "argmax(" + getExpression() + ")";
        }
        return "argmax(" + getExpression() + ", " + key + ")";
    }
}
