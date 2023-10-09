// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;

import java.io.Serializable;
import java.util.List;

/**
 * A set of argument expressions to a function or feature.
 * This is a value object.
 *
 * @author bratseth
 */
public final class Arguments implements Serializable {

    public static final Arguments EMPTY = new Arguments();

    private final List<ExpressionNode> expressions;

    public Arguments() {
        this(List.of());
    }

    public Arguments(ExpressionNode singleArgument) {
        this(List.of(singleArgument));
    }

    public Arguments(List<? extends ExpressionNode> expressions) {
        if (expressions == null) {
            this.expressions = List.of();
            return;
        }

        this.expressions = List.copyOf(expressions);
    }

    /** Returns an unmodifiable list of the expressions in this, never null */
    public List<ExpressionNode> expressions() { return expressions; }

    /** Returns the number of arguments in this */
    public int size() { return expressions.size(); }

    /** Evaluate all arguments in this */
    public Value[] evaluate(Context context) {
        Value[] values = new Value[expressions.size()];
        for (int i = 0; i < expressions.size(); i++)
            values[i] = expressions.get(i).evaluate(context);
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
