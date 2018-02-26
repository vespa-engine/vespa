// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;

/**
 * Inserts a "newTransform()" before expressions that "requiresTransform()"
 *
 * @author Simon Thoresen
 */
public abstract class ValueTransformProvider extends ExpressionConverter {

    private final Class<? extends Expression> transformClass;
    private boolean transformed = false;
    private boolean duplicate = false;

    public ValueTransformProvider(Class<? extends Expression> transformClass) {
        this.transformClass = transformClass;
    }

    @Override
    protected final ExpressionConverter branch() {
        return clone();
    }

    @Override
    protected final boolean shouldConvert(Expression exp) {
        if (transformClass.isInstance(exp)) {
            if (transformed) {
                duplicate = true;
                return true;
            }
            transformed = true;
            return false;
        }
        if (exp.createdOutputType() != null) {
            transformed = false;
            return false;
        }
        if ( ! requiresTransform(exp)) {
            return false;
        }
        if (transformed) {
            return false;
        }
        return true;
    }

    @Override
    protected final Expression doConvert(Expression exp) {
        if (duplicate) {
            duplicate = false;
            return null;
        }
        transformed = true;
        return new StatementExpression(newTransform(), exp);
    }

    protected abstract boolean requiresTransform(Expression exp);

    protected abstract Expression newTransform();
}
