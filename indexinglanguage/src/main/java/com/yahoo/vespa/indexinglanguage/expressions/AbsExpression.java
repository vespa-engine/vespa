// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.NumericDataType;
import com.yahoo.document.datatypes.ByteFieldValue;
import com.yahoo.document.datatypes.DoubleFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.FloatFieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;

/**
 * Returns the absolute value of a numeric input, keeping its type.
 * As in Java, the absolute value of the most negative byte, int or long is itself.
 */
public final class AbsExpression extends Expression {

    @Override
    public DataType setInputType(DataType input, TypeContext context) {
        super.setInputType(input, AnyNumericDataType.instance, context);
        return input;
    }

    @Override
    public DataType setOutputType(DataType output, TypeContext context) {
        if (output != null && ! (output instanceof NumericDataType) && ! (output instanceof AnyDataType))
            throw new VerificationException(this, "Produces a numeric value, but type " + output.getName() + " is required");
        super.setOutputType(output, context);
        return output instanceof NumericDataType ? output : AnyNumericDataType.instance;
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setCurrentValue(abs(context.getCurrentValue()));
    }

    private FieldValue abs(FieldValue value) {
        if (value instanceof IntegerFieldValue v) return new IntegerFieldValue(Math.abs(v.getInteger()));
        if (value instanceof LongFieldValue v) return new LongFieldValue(Math.abs(v.getLong()));
        if (value instanceof DoubleFieldValue v) return new DoubleFieldValue(Math.abs(v.getDouble()));
        if (value instanceof FloatFieldValue v) return new FloatFieldValue(Math.abs(v.getFloat()));
        if (value instanceof ByteFieldValue v) return new ByteFieldValue((byte)Math.abs(v.getByte()));
        throw new IllegalArgumentException("Expected a numeric value, got " + value.getDataType().getName());
    }

    @Override
    public String toString() { return "abs"; }

    @Override
    public boolean equals(Object obj) { return obj instanceof AbsExpression; }

    @Override
    public int hashCode() { return getClass().hashCode(); }

}
