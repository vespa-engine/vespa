// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.vespa.indexinglanguage.expressions.Expression;

/**
 * @author Simon Thoresen Hult
 */
public abstract class TypedExpressionConverter<T extends Expression> extends ExpressionConverter {

    private final Class<T> expClass;

    public TypedExpressionConverter(Class<T> expClass) {
        this.expClass = expClass;
    }

    @Override
    protected final boolean shouldConvert(Expression exp) {
        return expClass.isInstance(exp);
    }

    @Override
    protected final Expression doConvert(Expression exp) {
        return typedConvert(expClass.cast(exp));
    }

    protected abstract Expression typedConvert(T exp);
}
