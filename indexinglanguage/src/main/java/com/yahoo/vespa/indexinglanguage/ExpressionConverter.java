// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.vespa.indexinglanguage.expressions.Expression;

/**
 * @author Simon Thoresen Hult
 */
public abstract class ExpressionConverter implements Cloneable {

    public final Expression convert(Expression expression) {
        if (expression == null) return null;
        if (shouldConvert(expression))
            return doConvert(expression);
        else
            return expression.convertChildren(this);
    }

    public ExpressionConverter branch() {
        return this;
    }

    @Override
    public ExpressionConverter clone() {
        try {
            return (ExpressionConverter)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    protected abstract boolean shouldConvert(Expression exp);

    protected abstract Expression doConvert(Expression exp);

}
