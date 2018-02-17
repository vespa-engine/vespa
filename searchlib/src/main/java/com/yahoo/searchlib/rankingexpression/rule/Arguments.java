// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.google.common.collect.ImmutableList;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A set of argument expressions to a function or feature.
 * This is a value object.
 *.
 *
 * @author bratseth
 */
public final class Arguments implements Serializable {

    private final ImmutableList<ExpressionNode> expressions;

    public Arguments() {
        this(ImmutableList.of());
    }

    public Arguments(ExpressionNode singleArgument) {
        this(ImmutableList.of(singleArgument));
    }

    public Arguments(List<? extends ExpressionNode> expressions) {
        if (expressions == null) {
            this.expressions = ImmutableList.of();
            return;
        }

        // Build in a roundabout way because java generics and lists
        ImmutableList.Builder<ExpressionNode> b = ImmutableList.builder();
        for (ExpressionNode node : expressions)
            b.add(node);
        this.expressions = b.build();
    }

    /** Returns an unmodifiable list of the expressions in this, never null */
    public List<ExpressionNode> expressions() { return expressions; }

    /** Evaluate all arguments in this */
    public Value[] evaluate(Context context) {
        Value[] values=new Value[expressions.size()];
        for (int i=0; i<expressions.size(); i++)
            values[i]=expressions.get(i).evaluate(context);
        return values;
    }

    /** Evaluate the i'th argument */
    public Value evaluate(int i,Context context) {
        return expressions.get(i).evaluate(context);
    }

    public boolean isEmpty() { return expressions.isEmpty(); }

    @Override
    public int hashCode() {
        return expressions.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        return other instanceof Arguments && expressions.equals(((Arguments)other).expressions);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("(");
        for (ExpressionNode argument : expressions)
            b.append(argument).append(",");
        b.setLength(b.length()-1);
        if (b.length() > 0)
            b.append(")");
        return b.toString();
    }

}
