// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.StringFieldValue;

/**
 * @author Simon Thoresen Hult
 */
public final class TrimExpression extends Expression {

    public TrimExpression() {
        super(DataType.STRING);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setValue(new StringFieldValue(String.valueOf(context.getValue()).trim()));
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
        return "trim";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TrimExpression)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
