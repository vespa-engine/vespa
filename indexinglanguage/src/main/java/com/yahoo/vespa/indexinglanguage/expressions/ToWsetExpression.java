// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.WeightedSet;

/**
 * @author Simon Thoresen Hult
 */
public class ToWsetExpression extends Expression {

    private final Boolean createIfNonExistent;
    private final Boolean removeIfZero;

    public ToWsetExpression(boolean createIfNonExistent, boolean removeIfZero) {
        this.createIfNonExistent = createIfNonExistent;
        this.removeIfZero = removeIfZero;
    }

    public boolean getCreateIfNonExistent() {
        return createIfNonExistent;
    }

    public boolean getRemoveIfZero() {
        return removeIfZero;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    protected void doExecute(ExecutionContext ctx) {
        FieldValue input = ctx.getValue();
        DataType inputType = input.getDataType();

        WeightedSetDataType outputType = DataType.getWeightedSet(inputType, createIfNonExistent, removeIfZero);
        WeightedSet output = outputType.createFieldValue();
        output.add(input);

        ctx.setValue(output);
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setValue(DataType.getWeightedSet(context.getValue(), createIfNonExistent, removeIfZero));
    }

    @Override
    public DataType requiredInputType() {
        return UnresolvedDataType.INSTANCE;
    }

    @Override
    public DataType createdOutputType() {
        return UnresolvedDataType.INSTANCE;
    }

    @Override
    public String toString() {
        return "to_wset" +
               (createIfNonExistent ? " create_if_non_existent" : "") +
               (removeIfZero ? " remove_if_zero" : "");
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ToWsetExpression)) {
            return false;
        }
        ToWsetExpression rhs = (ToWsetExpression)obj;
        if (createIfNonExistent != rhs.createIfNonExistent) {
            return false;
        }
        if (removeIfZero != rhs.removeIfZero) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() +
               Boolean.valueOf(createIfNonExistent).hashCode() +
               Boolean.valueOf(removeIfZero).hashCode();
    }
}
