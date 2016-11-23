// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.functions.ReduceFunction;

import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * A node which performs a dimension reduction over a tensor
 *
 * @author bratseth
 */
 @Beta
public class TensorReduceNode extends CompositeNode {

    /** The tensor to aggregate over */
    private final ExpressionNode argument;

    private final ReduceFunction.Aggregator aggregator;

    /** The dimensions to sum over, or empty to sum all cells */
    private final ImmutableList<String> dimensions;

    public TensorReduceNode(ExpressionNode argument, ReduceFunction.Aggregator aggregator, List<String> dimensions) {
        this.argument = argument;
        this.aggregator = aggregator;
        this.dimensions = ImmutableList.copyOf(dimensions);
    }

    @Override
    public List<ExpressionNode> children() {
        return Collections.singletonList(argument);
    }

    @Override
    public CompositeNode setChildren(List<ExpressionNode> children) {
        if (children.size() != 1) throw new IllegalArgumentException("A tensor reduce node must have one tensor argument");
        return new TensorReduceNode(children.get(0), aggregator, dimensions);
    }

    @Override
    public String toString(SerializationContext context, Deque<String> path, CompositeNode parent) {
        return "reduce(" + argument.toString(context, path, parent) + ", " + 
                       aggregator + leadingCommaSeparated(dimensions) + ")";
    }
    
    private String leadingCommaSeparated(List<String> list) {
        StringBuilder b = new StringBuilder();
        for (String element : list)
            b.append(", ").append(element);
        return b.toString();
    }

    @Override
    public Value evaluate(Context context) {
        Value argumentValue = argument.evaluate(context);
        if ( ! ( argumentValue instanceof TensorValue))
            throw new IllegalArgumentException("Attempted to reduce '" + argument + "', " +
                                               "but this returns " + argumentValue + ", not a tensor");
        TensorValue tensorArgument = (TensorValue)argumentValue;
        return new TensorValue(tensorArgument.asTensor().reduce(aggregator, dimensions));
    }

}
