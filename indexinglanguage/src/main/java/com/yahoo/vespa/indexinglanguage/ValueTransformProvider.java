// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;

/**
 * Inserts a "newTransform()" before expressions that "requiresTransform()"
 *
 * @author Simon Thoresen Hult
 */
public abstract class ValueTransformProvider extends ExpressionConverter {

    private final Class<? extends Expression> transformClass;
    private boolean transformed = false;
    private boolean duplicate = false;

    public ValueTransformProvider(Class<? extends Expression> transformClass) {
        this.transformClass = transformClass;
    }

    @Override
    public final ExpressionConverter branch() {
        return clone();
    }

    @Override
    protected final boolean shouldConvert(Expression expression) {
        if (transformClass.isInstance(expression)) {
            if (transformed) {
                duplicate = true;
                return true;
            }
            transformed = true;
            return false;
        }
        if ( ! requiresTransform(expression)) return false;
        if (transformed) return false;
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
