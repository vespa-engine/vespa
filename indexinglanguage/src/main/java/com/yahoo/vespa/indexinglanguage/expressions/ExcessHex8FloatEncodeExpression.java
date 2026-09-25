// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FloatFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.text.Text;

/**
 * Converts a float into its 8-hex-digit string excess representation such that
 * the strings sort in the same order as the float values, cf. {@link Text#floatToExcessHex8(float)}.
 *
 * @author arnej
 */
public final class ExcessHex8FloatEncodeExpression extends Expression {

    @Override
    public DataType setInputType(DataType inputType, TypeContext context) {
        super.setInputType(inputType, DataType.FLOAT, context);
        return DataType.STRING;
    }

    @Override
    public DataType setOutputType(DataType outputType, TypeContext context) {
        super.setOutputType(DataType.STRING, outputType, null, context);
        return DataType.FLOAT;
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        float input = ((FloatFieldValue) context.getCurrentValue()).getFloat();
        context.setCurrentValue(new StringFieldValue(Text.floatToExcessHex8(input)));
    }

    @Override
    public String toString() { return "exhex8floatencode"; }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ExcessHex8FloatEncodeExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
