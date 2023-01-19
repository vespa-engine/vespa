// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    protected void doExecute(ExecutionContext context) {
        context.setValue(new IntegerFieldValue(Integer.valueOf(String.valueOf(context.getValue()))));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setValueType(createdOutputType());
    }

    @Override
    public DataType createdOutputType() {
        return DataType.INT;
    }

    @Override
    public String toString() {
        return "to_int";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ToIntegerExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
