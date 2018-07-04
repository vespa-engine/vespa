// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;

/**
 * @author Simon Thoresen Hult
 */
public class ClearStateExpression extends Expression {

    @Override
    protected void doExecute(ExecutionContext ctx) {
        ctx.clear();
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.clear();
    }

    @Override
    public DataType requiredInputType() {
        return null;
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
