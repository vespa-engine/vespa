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
        this.value = value;
    }

    @Override
    public boolean requiresInput() { return false; }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        super.setInputType(inputType, context);
        return DataType.BOOL;
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        super.setOutputType(DataType.BOOL, outputType, null, context);
        return AnyDataType.instance;
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setCurrentType(createdOutputType());
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setCurrentValue(new BoolFieldValue(value));
    }

    @Override
    public DataType createdOutputType() { return DataType.BOOL; }

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
