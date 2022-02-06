// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;

/**
 * @author Simon Thoresen Hult
 */
public final class HexEncodeExpression extends Expression {

    public HexEncodeExpression() {
        super(DataType.LONG);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        long input = ((LongFieldValue) context.getValue()).getLong();
        context.setValue(new StringFieldValue(Long.toHexString(input)));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setValueType(createdOutputType());
    }

    @Override
    public DataType createdOutputType() {
        return DataType.STRING;
    }

    @Override
    public String toString() {
        return "hexencode";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof HexEncodeExpression)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
