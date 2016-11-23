package com.yahoo.searchlib.rankingexpression.rule;

import com.google.common.collect.ImmutableList;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;

import java.util.Collections;
import java.util.Deque;
import java.util.List;
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
        // TODO: Verify that the function only accesses the arguments in mapperVariables
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
            b.append(", ").append(element);
        return b.toString();
    }

    /** Evaluate this in a context which must have the arguments bound */
    @Override
    public Value evaluate(Context context) {
        return functionExpression.evaluate(context);
    }
    
    /** 
     * Returns this as a double unary operator
     * 
     * @throws IllegalStateException if this does not have exactly one argument 
     */
    public DoubleUnaryOperator asDoubleUnaryOperator() {
        if (arguments.size() != 1) 
            throw new IllegalStateException("Cannot apply " + this + " as a DoubleUnaryOperator: " +
                                            "Must have one argument " + " but has " + arguments);
        return new DoubleUnaryLambda();
    }
    
    private class DoubleUnaryLambda implements DoubleUnaryOperator {

        @Override
        public double applyAsDouble(double operand) {
            MapContext context = new MapContext();
            context.put(arguments.get(0), operand);
            return evaluate(context).asDouble();
        }

    }

}
