// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.ByteFieldValue;

/**
 * @author Simon Thoresen Hult
 */
public final class ToByteExpression extends Expression {

    public ToByteExpression() {
        super(UnresolvedDataType.INSTANCE);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setValue(new ByteFieldValue(Byte.valueOf(String.valueOf(context.getValue()))));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setValueType(createdOutputType());
    }

    @Override
    public DataType createdOutputType() {
        return DataType.BYTE;
    }

    @Override
    public String toString() {
        return "to_byte";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ToByteExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
