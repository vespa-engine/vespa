// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.PositionDataType;

/**
 * @author Simon Thoresen Hult
 */
public final class ToPositionExpression extends Expression {

    public ToPositionExpression() {
        super(DataType.STRING);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setValue(PositionDataType.fromString(String.valueOf(context.getValue())));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setValueType(createdOutputType());
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

