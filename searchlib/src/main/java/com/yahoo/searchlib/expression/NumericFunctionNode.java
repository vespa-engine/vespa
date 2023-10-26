// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

/**
 * This is an abstract class for all functions that perform arithmetics. This node implements the necessary API for
 * doing arithmetic operations.
 *
 * @author Ulf Lilleengen
 */
public abstract class NumericFunctionNode extends MultiArgFunctionNode {

    @Override
    public void onPrepare() {
        super.onPrepare();

        ResultNode result = getResult();
        if (!(result instanceof IntegerResultNode) &&
            !(result instanceof FloatResultNode) &&
            !(result instanceof StringResultNode) &&
            !(result instanceof RawResultNode))
        {
            throw new RuntimeException("Can not perform numeric function on value of type '" +
                                       getResult().getClass().getName() + "'.");
        }
    }

    @Override
    protected final boolean equalsMultiArgFunction(MultiArgFunctionNode obj) {
        return true;
    }
}
