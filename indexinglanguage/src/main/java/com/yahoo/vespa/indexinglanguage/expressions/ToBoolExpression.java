// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.BoolFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.NumericFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;

/**
 * @author bratseth
 */
public final class ToBoolExpression extends Expression {

    public ToBoolExpression() {
        super(UnresolvedDataType.INSTANCE);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setValue(new BoolFieldValue(toBooleanValue(context.getValue())));
    }

    private boolean toBooleanValue(FieldValue value) {
        if (value instanceof NumericFieldValue)
            return ((NumericFieldValue)value).getNumber().intValue() != 0;
        if (value instanceof StringFieldValue)
            return ! ((StringFieldValue)value).getString().isEmpty();
        return false;
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setValueType(createdOutputType());
    }

    @Override
    public DataType createdOutputType() {
        return DataType.BOOL;
    }

    @Override
    public String toString() {
        return "to_bool";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ToBoolExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
