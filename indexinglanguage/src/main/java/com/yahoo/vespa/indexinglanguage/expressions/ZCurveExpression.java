// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.geo.ZCurve;

/**
 * @author Simon Thoresen Hult
 */
public class ZCurveExpression extends Expression {

    @Override
    protected void doExecute(ExecutionContext ctx) {
        Struct input = ((Struct)ctx.getValue());
        Integer x = getFieldValue(input, PositionDataType.FIELD_X);
        Integer y = getFieldValue(input, PositionDataType.FIELD_Y);
        if (x != null && y != null) {
            ctx.setValue(new LongFieldValue(ZCurve.encode(x, y)));
        } else {
            ctx.setValue(null);
        }
    }

    private static Integer getFieldValue(Struct struct, String fieldName) {
        IntegerFieldValue val = (IntegerFieldValue)struct.getFieldValue(fieldName);
        return val != null ? val.getInteger() : null;
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setValue(createdOutputType());
    }

    @Override
    public DataType requiredInputType() {
        return PositionDataType.INSTANCE;
    }

    @Override
    public DataType createdOutputType() {
        return DataType.LONG;
    }

    @Override
    public String toString() {
        return "zcurve";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ZCurveExpression)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
