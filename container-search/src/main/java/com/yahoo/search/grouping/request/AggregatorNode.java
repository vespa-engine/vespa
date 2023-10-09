// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents an aggregated value in a {@link GroupingExpression}. Because it operates on a list of data, it
 * can not be used as a document-level expression (i.e. level 0, see {@link GroupingExpression#resolveLevel(int)}). The
 * contained expression is evaluated at the level of the aggregator minus 1.
 *
 * @author Simon Thoresen Hult
 */
public abstract class AggregatorNode extends GroupingExpression {

    private final GroupingExpression exp;

    protected AggregatorNode(String image, String label, Integer level) {
        super(image + "()", label, level);
        this.exp = null;
    }

    protected AggregatorNode(String image, String label, Integer level, GroupingExpression exp) {
        super(image + "(" + exp.toString() + ")", label, level);
        this.exp = exp;
    }

    /**
     * Returns the expression that this node aggregates on.
     *
     * @return The expression.
     */
    public GroupingExpression getExpression() {
        return exp;
    }

    @Override
    public void resolveLevel(int level) {
        super.resolveLevel(level);
        if (level < 1) {
            throw new IllegalArgumentException("Expression '" + this + "' not applicable for " +
                                               GroupingOperation.getLevelDesc(level) + ".");
        }
        if (exp != null) {
            exp.resolveLevel(level - 1);
        }
    }

    @Override
    public void visit(ExpressionVisitor visitor) {
        super.visit(visitor);
        if (exp != null) {
            exp.visit(visitor);
        }
    }
}
