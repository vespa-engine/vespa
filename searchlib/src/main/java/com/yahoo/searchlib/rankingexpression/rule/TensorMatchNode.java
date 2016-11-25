// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.google.common.annotations.Beta;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * @author bratseth
 */
 @Beta
public class TensorMatchNode extends CompositeNode {

    private final ExpressionNode left, right;

    public TensorMatchNode(ExpressionNode left, ExpressionNode right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public List<ExpressionNode> children() {
        List<ExpressionNode> children = new ArrayList<>(2);
        children.add(left);
        children.add(right);
        return children;
    }

    @Override
    public CompositeNode setChildren(List<ExpressionNode> children) {
        if ( children.size() != 2)
            throw new IllegalArgumentException("A match product must have two children");
        return new TensorMatchNode(children.get(0), children.get(1));

    }

    @Override
    public String toString(SerializationContext context, Deque<String> path, CompositeNode parent) {
        return "match(" + left.toString(context, path, parent) + ", " + right.toString(context, path, parent) + ")";
    }

    @Override
    public Value evaluate(Context context) {
        return asTensor(left.evaluate(context)).match(asTensor(right.evaluate(context)));
    }

    private TensorValue asTensor(Value value) {
        if ( ! (value instanceof TensorValue))
            throw new IllegalArgumentException("Attempted to take the tensor product with an argument which is " +
                                               "not a tensor: " + value);
        return (TensorValue)value;
    }

}
