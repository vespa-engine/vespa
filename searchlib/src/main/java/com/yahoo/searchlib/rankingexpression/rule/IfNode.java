// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * A conditional branch of a ranking expression.
 *
 * @author Simon Thoresen
 * @author bratseth
 */
public final class IfNode extends CompositeNode {

    /** The expression nodes that make up this condition. */
    private final ExpressionNode condition, trueExpression, falseExpression;

    private final Double trueProbability;

    public IfNode(ExpressionNode condition, ExpressionNode trueExpression, ExpressionNode falseExpression) {
        this(condition, trueExpression, falseExpression, null);
    }

    /**
     * Creates a new condition node.
     *
     * @param condition the condition of this
     * @param trueExpression  the expression to evaluate if the comparison is true
     * @param falseExpression the expression to evaluate if the comparison is false
     * @param trueProbability the probability that the condition will evaluate to true, or null if not known.
     * @throws IllegalArgumentException if trueProbability is non-null and not between 0.0 and 1.0
     */
    public IfNode(ExpressionNode condition, ExpressionNode trueExpression, ExpressionNode falseExpression,
                  Double trueProbability) {
        if (trueProbability != null && ( trueProbability < 0.0 || trueProbability > 1.0) )
            throw new IllegalArgumentException("trueProbability must be a between 0.0 and 1.0, not " + trueProbability);
        this.condition = condition;
        this.trueProbability = trueProbability;
        this.trueExpression = trueExpression;
        this.falseExpression = falseExpression;
    }

    @Override
    public List<ExpressionNode> children() {
        List<ExpressionNode> children = new ArrayList<>(4);
        children.add(condition);
        children.add(trueExpression);
        children.add(falseExpression);
        return Collections.unmodifiableList(children);
    }

    public ExpressionNode getCondition() { return condition; }

    public ExpressionNode getTrueExpression() { return trueExpression; }

    public ExpressionNode getFalseExpression() { return falseExpression; }

    /** The average probability that the condition of this node will evaluate to true, or null if not known */
    public Double getTrueProbability() { return trueProbability; }

    @Override
    public String toString(SerializationContext context, Deque<String> path, CompositeNode parent) {
        return "if (" +
               condition.toString(context, path, this) + ", " +
               trueExpression.toString(context, path, this) + ", " +
               falseExpression.toString(context, path, this) +
                (trueProbability != null ? ", " + trueProbability : "") + ")";
    }

    @Override
    public TensorType type(TypeContext context) {
        TensorType trueType = trueExpression.type(context);
        TensorType falseType = falseExpression.type(context);

        // Types of each branch must be compatible; the resulting type is the most general
        if (trueType.isAssignableTo(falseType)) return falseType;
        if (falseType.isAssignableTo(trueType)) return trueType;
        throw new IllegalArgumentException("An if expression must produce compatible types in both " +
                                           "alternatives, but the 'true' type is " + trueType + " while the " +
                                           "'false' type is " + falseType);
    }

    @Override
    public Value evaluate(Context context) {
        if (condition.evaluate(context).asBoolean())
            return trueExpression.evaluate(context);
        else
            return falseExpression.evaluate(context);
    }

    @Override
    public IfNode setChildren(List<ExpressionNode> children) {
        if (children.size() != 3) throw new IllegalArgumentException("Expected 3 children but got " + children.size());
        return new IfNode(children.get(0), children.get(1), children.get(2));
    }

}
