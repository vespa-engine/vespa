// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * A node which flips the logical value produced from the nested expression.
 *
 * @author lesters
 */
public class NotNode extends BooleanNode {

    private final ExpressionNode value;

    public NotNode(ExpressionNode value) {
        this.value = value;
    }

    public ExpressionNode getValue() {
        return value;
    }

    @Override
    public List<ExpressionNode> children() {
        return Collections.singletonList(value);
    }

    @Override
    public String toString(SerializationContext context, Deque<String> path, CompositeNode parent) {
        return "!" + value.toString(context, path, parent);
    }

    @Override
    public TensorType type(TypeContext context) {
        return value.type(context);
    }

    @Override
    public Value evaluate(Context context) {
        return value.evaluate(context).not();
    }

    @Override
    public NotNode setChildren(List<ExpressionNode> children) {
        if (children.size() != 1) throw new IllegalArgumentException("Expected 1 children but got " + children.size());
        return new NotNode(children.get(0));
    }

}

