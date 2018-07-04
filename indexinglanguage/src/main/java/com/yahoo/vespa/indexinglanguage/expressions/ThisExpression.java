// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;

/**
 * @author Simon Thoresen Hult
 */
public class ThisExpression extends Expression {

    @Override
    protected void doExecute(ExecutionContext ctx) {
        // empty
    }

    @Override
    protected void doVerify(VerificationContext context) {
        // empty
    }

    @Override
    public DataType requiredInputType() {
        return UnresolvedDataType.INSTANCE;
    }

    @Override
    public DataType createdOutputType() {
        return UnresolvedDataType.INSTANCE;
    }

    @Override
    public String toString() {
        return "this";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ThisExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
