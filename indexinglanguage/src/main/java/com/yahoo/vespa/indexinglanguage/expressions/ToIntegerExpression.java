// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.IntegerFieldValue;

/**
 * @author Simon Thoresen Hult
 */
public final class ToIntegerExpression extends Expression {

    public ToIntegerExpression() {
        super(UnresolvedDataType.INSTANCE);
    }

    @Override
    public DataType setInputType(DataType input, VerificationContext context) {
        super.setInputType(input, context);
        return DataType.INT;
    }

    @Override
    public DataType setOutputType(DataType output, VerificationContext context) {
        super.setOutputType(DataType.INT, output, null, context);
        return null;
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setCurrentType(createdOutputType());
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setCurrentValue(new IntegerFieldValue(Integer.valueOf(String.valueOf(context.getCurrentValue()))));
    }

    @Override
    public DataType createdOutputType() { return DataType.INT; }

    @Override
    public String toString() { return "to_int"; }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ToIntegerExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
