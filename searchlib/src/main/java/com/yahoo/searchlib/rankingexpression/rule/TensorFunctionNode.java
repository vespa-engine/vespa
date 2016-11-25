// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.google.common.annotations.Beta;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.functions.EvaluationContext;
import com.yahoo.tensor.functions.PrimitiveTensorFunction;
import com.yahoo.tensor.functions.TensorFunction;
import com.yahoo.tensor.functions.ToStringContext;

import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A node which performs a tensor function
 *
 * @author bratseth
 */
 @Beta
public class TensorFunctionNode extends CompositeNode {

    private final TensorFunction function;
    
    public TensorFunctionNode(TensorFunction function) {
        this.function = function;
    }

    @Override
    public List<ExpressionNode> children() {
        return function.functionArguments().stream()
                                           .map(f -> ((TensorFunctionExpressionNode)f).expression)
                                           .collect(Collectors.toList());
    }

    @Override
    public CompositeNode setChildren(List<ExpressionNode> children) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String toString(SerializationContext context, Deque<String> path, CompositeNode parent) {   
        // Serialize as primitive
        return function.toPrimitive().toString(new ExpressionNodeToStringContext(context, path, this));
    }
    
    @Override
    public Value evaluate(Context context) {
        return new TensorValue(function.evaluate(context));
    }

    public static TensorFunctionExpressionNode wrapArgument(ExpressionNode node) {
        return new TensorFunctionExpressionNode(node);
    }
    
    /** 
     * A tensor function implemented by an expression.
     * This allows us to pass expressions as tensor function arguments.
     */
    public static class TensorFunctionExpressionNode extends PrimitiveTensorFunction {

        /** An expression which produces a tensor */
        private final ExpressionNode expression;
        
        public TensorFunctionExpressionNode(ExpressionNode expression) {
            this.expression = expression;
        }
        
        @Override
        public List<TensorFunction> functionArguments() { return Collections.emptyList(); }

        @Override
        public PrimitiveTensorFunction toPrimitive() { return this; }

        @Override
        public Tensor evaluate(EvaluationContext context) {
            Value result = expression.evaluate((Context)context);
            if ( ! ( result instanceof TensorValue))
                throw new IllegalArgumentException("Attempted to evaluate tensor function '" + expression + "', " +
                                                   "but this returns " + result + ", not a tensor");
            return ((TensorValue)result).asTensor();
        }

        @Override
        public String toString(ToStringContext c) {
            ExpressionNodeToStringContext context = (ExpressionNodeToStringContext)c;
            return expression.toString(context.context, context.path, context.parent);
        }

    }
    
    /** Allows passing serialization context arguments through TensorFunctions */
    private static class ExpressionNodeToStringContext implements ToStringContext {
        
        final SerializationContext context;
        final Deque<String> path;
        final CompositeNode parent;
        
        public ExpressionNodeToStringContext(SerializationContext context, Deque<String> path, CompositeNode parent) {
            this.context = context;
            this.path = path;
            this.parent = parent;
        }

    }

}
