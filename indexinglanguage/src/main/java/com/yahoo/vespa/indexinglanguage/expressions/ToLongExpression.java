// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.LongFieldValue;

/**
 * @author Simon Thoresen Hult
 */
public final class ToLongExpression extends Expression {

    public ToLongExpression() {
        super(UnresolvedDataType.INSTANCE);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setValue(new LongFieldValue(Long.valueOf(String.valueOf(context.getValue()))));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setValueType(createdOutputType());
    }

    @Override
    public DataType createdOutputType() {
        return DataType.LONG;
    }

    @Override
    public String toString() {
        return "to_long";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ToLongExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
