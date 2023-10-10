// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.BoolFieldValue;

/**
 * 'true' or 'false
 *
 * @author bratseth
 */
public class LiteralBoolExpression extends Expression {

    private final boolean value;

    public LiteralBoolExpression(boolean value) {
        super(null);
        this.value = value;
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setValue(new BoolFieldValue(value));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setValueType(createdOutputType());
    }

    @Override
    public DataType createdOutputType() {
        return DataType.BOOL;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof LiteralBoolExpression)) return false;
        return ((LiteralBoolExpression)other).value == this.value;
    }

    @Override
    public int hashCode() {
        return value ? 1 : 0;
    }

}
