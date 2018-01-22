// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.google.common.annotations.Beta;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.evaluation.EvaluationContext;
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

    /** Returns the tensor function wrapped by this */
    public TensorFunction function() { return function; }

    @Override
    public List<ExpressionNode> children() {
        return function.functionArguments().stream()
                                           .map(this::toExpressionNode)
                                           .collect(Collectors.toList());
    }

    private ExpressionNode toExpressionNode(TensorFunction f) {
        if (f instanceof TensorFunctionExpressionNode)
            return ((TensorFunctionExpressionNode)f).expression;
        else
            return new TensorFunctionNode(f);
    }

    @Override
    public CompositeNode setChildren(List<ExpressionNode> children) {
        List<TensorFunction> wrappedChildren = children.stream()
                                                        .map(TensorFunctionExpressionNode::new)
                                                        .collect(Collectors.toList());
        return new TensorFunctionNode(function.replaceArguments(wrappedChildren));
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
        public List<TensorFunction> functionArguments() {
            if (expression instanceof CompositeNode)
                return ((CompositeNode)expression).children().stream()
                                                             .map(TensorFunctionExpressionNode::new)
                                                             .collect(Collectors.toList());
            else
                return Collections.emptyList();
        }

        @Override
        public TensorFunction replaceArguments(List<TensorFunction> arguments) {
            if (arguments.size() == 0) return this;
            List<ExpressionNode> unwrappedChildren = arguments.stream()
                                                              .map(arg -> ((TensorFunctionExpressionNode)arg).expression)
                                                              .collect(Collectors.toList());
            return new TensorFunctionExpressionNode(((CompositeNode)expression).setChildren(unwrappedChildren));
        }

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
        public String toString() {
            return toString(ExpressionNodeToStringContext.empty);
        }

        @Override
        public String toString(ToStringContext c) {
            if (c instanceof ExpressionNodeToStringContext) {
                ExpressionNodeToStringContext context = (ExpressionNodeToStringContext) c;
                return expression.toString(context.context, context.path, context.parent);
            }
            else {
                return expression.toString();
            }
        }

    }

    /** Allows passing serialization context arguments through TensorFunctions */
    private static class ExpressionNodeToStringContext implements ToStringContext {

        final SerializationContext context;
        final Deque<String> path;
        final CompositeNode parent;

        public static final ExpressionNodeToStringContext empty = new ExpressionNodeToStringContext(new SerializationContext(),
                                                                                                    null,
                                                                                                    null);

        public ExpressionNodeToStringContext(SerializationContext context, Deque<String> path, CompositeNode parent) {
            this.context = context;
            this.path = path;
            this.parent = parent;
        }

    }

}
