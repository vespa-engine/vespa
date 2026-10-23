// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.text.Text;

/**
 * Converts an int into its 8-hex-digit string excess representation such that
 * the string sort in the same order as the int values, cf. {@link Text#toExcessHex8(int)}.
 *
 * @author boeker
 */
public final class ExcessHex8EncodeExpression extends Expression {

    @Override
    public DataType setInputType(DataType inputType, TypeContext context) {
        super.setInputType(inputType, DataType.INT, context);
        return DataType.STRING;
    }

    @Override
    public DataType setOutputType(DataType outputType, TypeContext context) {
        super.setOutputType(DataType.STRING, outputType, null, context);
        return DataType.INT;
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        int input = ((IntegerFieldValue) context.getCurrentValue()).getInteger();
        context.setCurrentValue(new StringFieldValue(Text.toExcessHex8(input)));
    }

    @Override
    public String toString() { return "exhex8encode"; }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ExcessHex8EncodeExpression)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
