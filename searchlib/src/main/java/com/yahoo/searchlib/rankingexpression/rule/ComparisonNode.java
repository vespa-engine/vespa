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
 * A node which returns the outcome of a comparison.
 *
 * @author bratseth
 */
public class ComparisonNode extends BooleanNode {

    /** The operator string of this condition. */
    private final TruthOperator operator;

    private final List<ExpressionNode> conditions;

    public ComparisonNode(ExpressionNode leftCondition, TruthOperator operator, ExpressionNode rightCondition) {
        conditions = List.of(leftCondition, rightCondition);
        this.operator = operator;
    }

    @Override
    public List<ExpressionNode> children() {
        return conditions;
    }

    public TruthOperator getOperator() { return operator; }

    public ExpressionNode getLeftCondition() { return conditions.get(0); }

    public ExpressionNode getRightCondition() { return conditions.get(1); }

    @Override
    public StringBuilder toString(StringBuilder string, SerializationContext context, Deque<String> path, CompositeNode parent) {
        getLeftCondition().toString(string, context, path, this).append(' ').append(operator).append(' ');
        return getRightCondition().toString(string, context, path, this);
    }

    @Override
    public TensorType type(TypeContext<Reference> context) {
        return TensorType.empty; // by definition
    }

    @Override
    public Value evaluate(Context context) {
        Value leftValue = getLeftCondition().evaluate(context);
        Value rightValue = getRightCondition().evaluate(context);
        return leftValue.compare(operator,rightValue);
    }

    @Override
    public ComparisonNode setChildren(List<ExpressionNode> children) {
        if (children.size() != 2) throw new IllegalArgumentException("A comparison test must have 2 children");
        return new ComparisonNode(children.get(0), operator, children.get(1));
    }

    @Override
    public int hashCode() { return Objects.hash(operator, conditions); }

}
