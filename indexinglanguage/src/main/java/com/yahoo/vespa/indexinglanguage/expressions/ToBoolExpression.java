// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.NumericDataType;
import com.yahoo.document.datatypes.BoolFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.NumericFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;

/**
 * @author bratseth
 */
public final class ToBoolExpression extends Expression {

    @Override
    public DataType setInputType(DataType input, VerificationContext context) {
        super.setInputType(input, context);
        if (input == null) return null;
        if ( ! (input.isAssignableTo(DataType.STRING) && ! (input instanceof NumericDataType)))
            throw new VerificationException(this, "Input must be a string or number, but got " + input.getName());
        return DataType.BOOL;
    }

    @Override
    public DataType setOutputType(DataType output, VerificationContext context) {
        super.setOutputType(DataType.BOOL, output, null, context);
        return getInputType(context);
    }

    @Override
    protected void doVerify(VerificationContext context) {
        if (context.getCurrentType() == null)
            throw new VerificationException(this, "Expected input, but no input is provided");
        context.setCurrentType(createdOutputType());
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setCurrentValue(new BoolFieldValue(toBooleanValue(context.getCurrentValue())));
    }

    private boolean toBooleanValue(FieldValue value) {
        if (value instanceof NumericFieldValue)
            return ((NumericFieldValue)value).getNumber().intValue() != 0;
        if (value instanceof StringFieldValue)
            return ! ((StringFieldValue)value).getString().isEmpty();
        return false;
    }

    @Override
    public DataType createdOutputType() { return DataType.BOOL; }

    @Override
    public String toString() { return "to_bool"; }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ToBoolExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
