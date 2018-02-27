// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.vespa.indexinglanguage.expressions.Expression;

/**
 * @author Simon Thoresen
 */
public abstract class ExpressionVisitor {

    private final MyConverter converter = new MyConverter();

    public void visit(Expression exp) {
        converter.convert(exp);
    }

    protected abstract void doVisit(Expression exp);

    private class MyConverter extends ExpressionConverter {

        @Override
        protected boolean shouldConvert(Expression exp) {
            doVisit(exp);
            return false;
        }

        @Override
        protected Expression doConvert(Expression exp) {
            throw new AssertionError();
        }

    }

}
