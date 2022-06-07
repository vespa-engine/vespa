// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.Deque;
import java.util.Objects;

/**
 * A node which holds a constant (frozen) value.
 *
 * @author Simon Thoresen Hult
 */
public final class ConstantNode extends ExpressionNode {

    private final Value value;

    /**
     * Creates a constant value
     *
     * @param value the value. Ownership of this value is transferred to this.
     */
    public ConstantNode(Value value) {
        value.freeze();
        this.value = value;
    }

    public Value getValue() { return value; }

    @Override
    public StringBuilder toString(StringBuilder string, SerializationContext context, Deque<String> path, CompositeNode parent) {
        return string.append(value.toString());
    }

    @Override
    public TensorType type(TypeContext<Reference> context) { return value.type(); }

    @Override
    public Value evaluate(Context context) {
        return value;
    }

    @Override
    public int hashCode() { return Objects.hash("constantNode", value); }

}
