// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;

import java.util.Deque;
import java.util.List;

/**
 * A node which maps the values of a tensor
 *
 * @author bratseth
 */
 @Beta
public class TensorMapNode extends CompositeNode {

    /** The tensor to aggregate over */
    private final ExpressionNode argument;
    
    private final LambdaFunctionNode doubleMapper;
    
    public TensorMapNode(ExpressionNode argument, LambdaFunctionNode doubleMapper) {
        this.argument = argument;
        this.doubleMapper = doubleMapper;
    }

    @Override
    public List<ExpressionNode> children() {
        return ImmutableList.of(argument, doubleMapper);
    }

    @Override
    public CompositeNode setChildren(List<ExpressionNode> children) {
        if (children.size() != 2) 
            throw new IllegalArgumentException("A tensor map node must have one tensor and one mapper");
        return new TensorMapNode(children.get(0), (LambdaFunctionNode)children.get(1));
    }

    @Override
    public String toString(SerializationContext context, Deque<String> path, CompositeNode parent) {
        return "map(" + argument.toString(context, path, parent) + ", " + doubleMapper.toString() + ")";
    }

    @Override
    public Value evaluate(Context context) {
        Value argumentValue = argument.evaluate(context);
        if ( ! ( argumentValue instanceof TensorValue))
            throw new IllegalArgumentException("Attempted to map '" + argument + "', " +
                                               "but this returns " + argumentValue + ", not a tensor");
        TensorValue tensorArgument = (TensorValue)argumentValue;
        return new TensorValue(tensorArgument.asTensor().map(doubleMapper.asDoubleUnaryOperator()));
    }

}
