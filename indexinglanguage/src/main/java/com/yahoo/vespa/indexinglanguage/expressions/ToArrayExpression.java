// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    protected void doExecute(ExecutionContext context) {
        FieldValue input = context.getValue();
        DataType inputType = input.getDataType();

        ArrayDataType outputType = DataType.getArray(inputType);
        Array output = outputType.createFieldValue();
        output.add(input);

        context.setValue(output);
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setValueType(DataType.getArray(context.getValueType()));
    }

    @Override
    public DataType createdOutputType() {
        return UnresolvedDataType.INSTANCE;
    }

    @Override
    public String toString() {
        return "to_array";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ToArrayExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
