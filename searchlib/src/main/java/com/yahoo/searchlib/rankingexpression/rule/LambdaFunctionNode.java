// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.google.common.collect.ImmutableList;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

/**
 * A free, parametrized function
 *
 * @author bratseth
 */
public class LambdaFunctionNode extends CompositeNode {

    private final ImmutableList<String> arguments;
    private final ExpressionNode functionExpression;

    public LambdaFunctionNode(List<String> arguments, ExpressionNode functionExpression) {
        // TODO: Verify that the function only accesses the given arguments
        this.arguments = ImmutableList.copyOf(arguments);
        this.functionExpression = functionExpression;
    }

    @Override
    public List<ExpressionNode> children() {
        return Collections.singletonList(functionExpression);
    }

    @Override
    public CompositeNode setChildren(List<ExpressionNode> children) {
        if ( children.size() != 1)
            throw new IllegalArgumentException("A lambda function must have a single child expression");
        return new LambdaFunctionNode(arguments, children.get(0));
    }

    @Override
    public String toString(SerializationContext context, Deque<String> path, CompositeNode parent) {
        return ("f(" + commaSeparated(arguments) + ")(" + functionExpression.toString(context, path, this)) + ")";
    }

    private String commaSeparated(List<String> list) {
        StringBuilder b = new StringBuilder();
        for (String element : list)
            b.append(element).append(",");
        if (b.length() > 0)
            b.setLength(b.length()-1);
        return b.toString();
    }

    @Override
    public TensorType type(TypeContext<Reference> context) {
        return TensorType.empty; // by definition - no nested lambdas
    }

    /** Evaluate this in a context which must have the arguments bound */
    @Override
    public Value evaluate(Context context) {
        return functionExpression.evaluate(context);
    }

    /**
     * Returns this as a double unary operator
     *
     * @throws IllegalStateException if this has more than one argument
     */
    public DoubleUnaryOperator asDoubleUnaryOperator() {
        if (arguments.size() > 1)
            throw new IllegalStateException("Cannot apply " + this + " as a DoubleUnaryOperator: " +
                                            "Must have at most one argument " + " but has " + arguments);
        return new DoubleUnaryLambda();
    }

    /**
     * Returns this as a double binary operator
     *
     * @throws IllegalStateException if this has more than two arguments
     */
    public DoubleBinaryOperator asDoubleBinaryOperator() {
        if (arguments.size() > 2)
            throw new IllegalStateException("Cannot apply " + this + " as a DoubleBinaryOperator: " +
                                            "Must have at most two argument " + " but has " + arguments);
        return new DoubleBinaryLambda();
    }

    private class DoubleUnaryLambda implements DoubleUnaryOperator {

        @Override
        public double applyAsDouble(double operand) {
            MapContext context = new MapContext();
            if (arguments.size() > 0)
                context.put(arguments.get(0), operand);
            return evaluate(context).asDouble();
        }

        @Override
        public String toString() {
            return LambdaFunctionNode.this.toString();
        }

    }

    private class DoubleBinaryLambda implements DoubleBinaryOperator {

        @Override
        public double applyAsDouble(double left, double right) {
            MapContext context = new MapContext();
            if (arguments.size() > 0)
                context.put(arguments.get(0), left);
            if (arguments.size() > 1)
                context.put(arguments.get(1), right);
            return evaluate(context).asDouble();
        }

        @Override
        public String toString() {
            return LambdaFunctionNode.this.toString();
        }

    }

}
