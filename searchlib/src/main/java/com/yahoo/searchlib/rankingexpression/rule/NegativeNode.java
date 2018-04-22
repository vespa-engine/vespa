// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * A node which flips the sign of the value produced from the nested expression
 *
 * @author bratseth
 */
public class NegativeNode extends CompositeNode {

    private final ExpressionNode value;

    /** Constructs a new negative node */
    public NegativeNode(ExpressionNode value) {
        this.value = value;
    }

    /** Returns the node creating the value negated by this */
    public ExpressionNode getValue() { return value; }

    @Override
    public List<ExpressionNode> children() {
        return Collections.singletonList(value);
    }

    @Override
    public StringBuilder toString(StringBuilder string, SerializationContext context, Deque<String> path, CompositeNode parent) {
        return value.toString(string.append('-'), context, path, parent);
    }

    @Override
    public TensorType type(TypeContext<Reference> context) {
        return value.type(context);
    }

    @Override
    public Value evaluate(Context context) {
        return value.evaluate(context).negate();
    }

    @Override
    public NegativeNode setChildren(List<ExpressionNode> children) {
        if (children.size() != 1) throw new IllegalArgumentException("Expected 1 children but got " + children.size());
        return new NegativeNode(children.get(0));
    }

}
