// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;

/**
 * @author Simon Thoresen Hult
 */
public final class ToArrayExpression extends Expression {

    public ToArrayExpression() {
        super(UnresolvedDataType.INSTANCE);
    }

    @Override
    public DataType setInputType(DataType input, VerificationContext context) {
        super.setInputType(input, context);
        if (input == null) return null;
        return new ArrayDataType(input);
    }

    @Override
    public DataType setOutputType(DataType output, VerificationContext context) {
        super.setOutputType(output, context);
        if (output instanceof ArrayDataType arrayType)
            return arrayType.getNestedType();
        if (output instanceof AnyDataType)
            return AnyDataType.instance;
        else
            throw new VerificationException(this, "Produces an array,  but " + output + " is required");
    }

    @Override
    protected void doVerify(VerificationContext context) {
        if (context.getCurrentType() == null)
            throw new VerificationException(this, "Expected input, but no input is provided");
        context.setCurrentType(DataType.getArray(context.getCurrentType()));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    protected void doExecute(ExecutionContext context) {
        FieldValue input = context.getCurrentValue();
        DataType inputType = input.getDataType();

        ArrayDataType outputType = DataType.getArray(inputType);
        Array output = outputType.createFieldValue();
        output.add(input);

        context.setCurrentValue(output);
    }

    @Override
    public DataType createdOutputType() {
        return UnresolvedDataType.INSTANCE;
    }

    @Override
    public String toString() { return "to_array"; }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ToArrayExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
