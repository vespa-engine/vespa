// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * A conditional branch of a ranking expression.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public final class IfNode extends CompositeNode {

    /** [condition, trueExpression, falseExpression] */
    private final List<ExpressionNode> arguments;
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
        this.trueProbability = trueProbability;
        this.arguments = List.of(condition, trueExpression, falseExpression);
    }

    @Override
    public List<ExpressionNode> children() {
        return arguments;
    }

    public ExpressionNode getCondition() { return arguments.get(0); }

    public ExpressionNode getTrueExpression() { return arguments.get(1); }

    public ExpressionNode getFalseExpression() { return arguments.get(2); }

    /** The average probability that the condition of this node will evaluate to true, or null if not known */
    public Double getTrueProbability() { return trueProbability; }

    @Override
    public StringBuilder toString(StringBuilder string, SerializationContext context, Deque<String> path, CompositeNode parent) {
        string.append("if (");
        getCondition().toString(string, context, path, this).append(", ");
        getTrueExpression().toString(string, context, path, this).append(", ");
        getFalseExpression().toString(string, context, path, this);
        if (trueProbability != null) {
            string.append(", ").append(trueProbability);
        }
        return string.append(')');
    }

    @Override
    public TensorType type(TypeContext<Reference> context) {
        TensorType trueType = getTrueExpression().type(context);
        TensorType falseType = getFalseExpression().type(context);
        return trueType.dimensionwiseGeneralizationWith(falseType).orElseThrow(() ->
            new IllegalArgumentException("An if expression must produce compatible types in both " +
                                         "alternatives, but the 'true' type is " + trueType + " while the " +
                                         "'false' type is " + falseType +
                                         "\n'true' branch: " + getTrueExpression() +
                                         "\n'false' branch: " + getFalseExpression())
        );
    }

    @Override
    public Value evaluate(Context context) {
        if (getCondition().evaluate(context).asBoolean())
            return getTrueExpression().evaluate(context);
        else
            return getFalseExpression().evaluate(context);
    }

    @Override
    public IfNode setChildren(List<ExpressionNode> children) {
        if (children.size() != 3) throw new IllegalArgumentException("Expected 3 children but got " + children.size());
        return new IfNode(children.get(0), children.get(1), children.get(2));
    }

    @Override
    public int hashCode() { return Objects.hash("if", arguments, trueProbability); }

}
