// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;

/**
 * @author Simon Thoresen Hult
 */
public final class ThisExpression extends Expression {

    @Override
    public boolean isMutating() { return false; }

    @Override
    protected void doVerify(VerificationContext context) {
        if (context.getCurrentType() == null)
            throw new VerificationException(this, "Expected input, but no input is provided");
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        // empty
    }

    @Override
    public DataType createdOutputType() { return UnresolvedDataType.INSTANCE; }

    @Override
    public String toString() { return "this"; }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ThisExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
