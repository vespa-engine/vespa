// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.evaluation.ValueType;

import java.util.Deque;

/**
 * A node which holds a constant (frozen) value.
 *
 * @author Simon Thoresen
 */
public final class ConstantNode extends ExpressionNode {

    private final String sourceImage;

    private final Value value;

    public ConstantNode(Value value) {
        this(value,null);
    }

    /**
     * Creates a constant value
     *
     * @param value the value. Ownership of this value is transferred to this.
     * @param sourceImage the source string image producing this value
     */
    public ConstantNode(Value value, String sourceImage) {
        value.freeze();
        this.value = value;
        this.sourceImage = sourceImage;
    }

    public Value getValue() { return value; }

    @Override
    public String toString(SerializationContext context, Deque<String> path, CompositeNode parent) {
        return sourceString();
    }

    /** Returns the string which created this, or the value.toString() if not known */
    public String sourceString() {
        if (sourceImage != null) return sourceImage;
        return value.toString();
    }

    @Override
    public ValueType type(Context context) { return value.type(); }

    @Override
    public Value evaluate(Context context) {
        return value;
    }

}
