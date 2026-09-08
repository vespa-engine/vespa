// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.text.Text;

/**
 * Converts a long into its 16-hex-digit string excess representation such that
 * the strings sort in the same order as the long values, cf. {@link Text#toExcessHex16(long)}.
 *
 * @author johsol
 */
public final class ExcessHex16EncodeExpression extends Expression {

    @Override
    public DataType setInputType(DataType inputType, TypeContext context) {
        super.setInputType(inputType, DataType.LONG, context);
        return DataType.STRING;
    }

    @Override
    public DataType setOutputType(DataType outputType, TypeContext context) {
        super.setOutputType(DataType.STRING, outputType, null, context);
        return DataType.LONG;
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        long input = ((LongFieldValue) context.getCurrentValue()).getLong();
        context.setCurrentValue(new StringFieldValue(Text.toExcessHex16(input)));
    }

    @Override
    public String toString() { return "exhex16encode"; }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ExcessHex16EncodeExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
