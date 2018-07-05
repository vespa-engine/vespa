// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.PositionDataType;

/**
 * @author Simon Thoresen Hult
 */
public class ToPositionExpression extends Expression {

    @Override
    protected void doExecute(ExecutionContext ctx) {
        ctx.setValue(PositionDataType.fromString(String.valueOf(ctx.getValue())));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setValue(createdOutputType());
    }

    @Override
    public DataType requiredInputType() {
        return DataType.STRING;
    }

    @Override
    public DataType createdOutputType() {
        return PositionDataType.INSTANCE;
    }

    @Override
    public String toString() {
        return "to_pos";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ToPositionExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
