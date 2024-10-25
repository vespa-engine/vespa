// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FloatFieldValue;

/**
 * @author Simon Thoresen Hult
 */
public final class ToFloatExpression extends Expression {

    public ToFloatExpression() {
        super(UnresolvedDataType.INSTANCE);
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setCurrentType(createdOutputType());
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setCurrentValue(new FloatFieldValue(Float.valueOf(String.valueOf(context.getCurrentValue()))));
    }

    @Override
    public DataType createdOutputType() { return DataType.FLOAT; }

    @Override
    public String toString() { return "to_float"; }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ToFloatExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
