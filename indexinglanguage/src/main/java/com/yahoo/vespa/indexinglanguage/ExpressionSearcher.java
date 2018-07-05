// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.vespa.indexinglanguage.expressions.Expression;

/**
 * @author Simon Thoresen Hult
 */
public class ExpressionSearcher<T extends Expression> {

    private final Class<T> searchFor;

    public ExpressionSearcher(Class<T> searchFor) {
        this.searchFor = searchFor;
    }

    public boolean containedIn(Expression searchIn) {
        return searchIn(searchIn) != null;
    }

    public T searchIn(Expression searchIn) {
        MyConverter searcher = new MyConverter();
        searcher.convert(searchIn);
        return searcher.found;
    }

    private class MyConverter extends ExpressionConverter {

        T found = null;

        @Override
        protected boolean shouldConvert(Expression exp) {
            if (searchFor.isInstance(exp)) {
                found = searchFor.cast(exp);
                return true; // terminate search
            }
            return false;
        }

        @Override
        protected Expression doConvert(Expression exp) {
            return exp;
        }
    }
}
