// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.geo.ZCurve;

/**
 * @author Simon Thoresen Hult
 */
public final class ZCurveExpression extends Expression {

    public ZCurveExpression() {
        super(PositionDataType.INSTANCE);
    }

    @Override
    public DataType setInputType(DataType input, VerificationContext context) {
        if (input == null) return null;
        if ( ! (input instanceof StructDataType struct))
            throw new VerificationException(this, "This requires a struct as input, but got " + input.getName());
        requireIntegerField(PositionDataType.FIELD_X, struct);
        requireIntegerField(PositionDataType.FIELD_Y, struct);

        super.setInputType(input, context);
        return DataType.LONG;
    }

    private void requireIntegerField(String fieldName, StructDataType struct) {
        var field = struct.getField(fieldName);
        if (field == null || field.getDataType() != DataType.INT)
            throw new VerificationException(this, "The struct '" + struct.getName() +
                                                  "' does not have an integer field named '" + fieldName + "'");
    }

    @Override
    public DataType setOutputType(DataType output, VerificationContext context) {
        super.setOutputType(DataType.LONG, output, null, context);
        return PositionDataType.INSTANCE;
    }

    @Override
    protected void doVerify(VerificationContext context) {
        if (context.getCurrentType() == null)
            throw new VerificationException(this, "Expected input, but no input is provided");
        context.setCurrentType(createdOutputType());
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        Struct input = ((Struct) context.getCurrentValue());
        Integer x = getFieldValue(input, PositionDataType.FIELD_X);
        Integer y = getFieldValue(input, PositionDataType.FIELD_Y);
        if (x != null && y != null) {
            context.setCurrentValue(new LongFieldValue(ZCurve.encode(x, y)));
        } else {
            context.setCurrentValue(DataType.LONG.createFieldValue());
        }
    }

    private static Integer getFieldValue(Struct struct, String fieldName) {
        IntegerFieldValue val = (IntegerFieldValue)struct.getFieldValue(fieldName);
        return val != null ? val.getInteger() : null;
    }

    @Override
    public DataType createdOutputType() { return DataType.LONG; }

    @Override
    public String toString() { return "zcurve"; }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ZCurveExpression)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
