// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;

import java.io.Serializable;
import java.util.Deque;

/**
 * Superclass of all expression nodes. Expression nodes have their identity determined by their content.
 * All expression nodes are immutable.
 *
 * @author Simon Thoresen
 */
public abstract class ExpressionNode implements Serializable {

    @Override
    public final int hashCode() {
        return toString().hashCode();
    }

    @Override
    public final boolean equals(Object obj) {
        return obj instanceof ExpressionNode && toString().equals(obj.toString());
    }

    @Override
    public final String toString() {
        return toString(new SerializationContext(), null, null);
    }

    /**
     * Returns a script instance of this based on the supplied script functions.
     *
     * @param context the serialization context
     * @param path the call path to this, used for cycle detection, or null if this is a root
     * @param parent the parent node of this, or null if it a root
     * @return the main script, referring to script instances.
     */
    public abstract String toString(SerializationContext context, Deque<String> path, CompositeNode parent);

    /**
     * Returns the type this will return if evaluated with the given context.
     *
     * @param context the variable type bindings to use for this evaluation
     * @throws IllegalArgumentException if there are variables which are not bound in the given map
     */
    public abstract TensorType type(TypeContext context);

    /**
     * Returns the value of evaluating this expression over the given context.
     *
     * @param context the variable bindings to use for this evaluation
     * @throws IllegalArgumentException if there are variables which are not bound in the given map
     */
    public abstract Value evaluate(Context context);

}
