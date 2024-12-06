// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.PositionDataType;

/**
 * @author Simon Thoresen Hult
 */
public final class ToPositionExpression extends Expression {

    @Override
    public DataType setInputType(DataType input, VerificationContext context) {
        super.setInputType(input, DataType.STRING, context);
        return PositionDataType.INSTANCE;
    }

    @Override
    public DataType setOutputType(DataType output, VerificationContext context) {
        super.setOutputType(PositionDataType.INSTANCE, output, null, context);
        return DataType.STRING;
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setCurrentType(createdOutputType());
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setCurrentValue(PositionDataType.fromString(String.valueOf(context.getCurrentValue())));
    }

    @Override
    public DataType createdOutputType() { return PositionDataType.INSTANCE; }

    @Override
    public String toString() { return "to_pos"; }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ToPositionExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}

