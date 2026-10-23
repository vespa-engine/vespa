// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.DoubleFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.text.Text;

/**
 * Converts a double into its 16-hex-digit string excess representation such that
 * the strings sort in the same order as the double values, cf. {@link Text#doubleToExcessHex16(double)}.
 *
 * @author arnej
 */
public final class ExcessHex16DoubleEncodeExpression extends Expression {

    @Override
    public DataType setInputType(DataType inputType, TypeContext context) {
        super.setInputType(inputType, DataType.DOUBLE, context);
        return DataType.STRING;
    }

    @Override
    public DataType setOutputType(DataType outputType, TypeContext context) {
        super.setOutputType(DataType.STRING, outputType, null, context);
        return DataType.DOUBLE;
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        double input = ((DoubleFieldValue) context.getCurrentValue()).getDouble();
        context.setCurrentValue(new StringFieldValue(Text.doubleToExcessHex16(input)));
    }

    @Override
    public String toString() { return "exhex16doubleencode"; }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ExcessHex16DoubleEncodeExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
