// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;

/**
 * @author Simon Thoresen Hult
 */
public final class ClearStateExpression extends Expression {

    public ClearStateExpression() {
        super(null);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.clear();
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.clear();
    }

    @Override
    public DataType createdOutputType() {
        return null;
    }

    @Override
    public String toString() {
        return "clear_state";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ClearStateExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
