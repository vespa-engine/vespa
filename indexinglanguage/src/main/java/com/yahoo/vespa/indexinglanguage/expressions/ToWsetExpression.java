// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.WeightedSet;

/**
 * @author Simon Thoresen Hult
 */
public final class ToWsetExpression extends Expression {

    private final Boolean createIfNonExistent;
    private final Boolean removeIfZero;

    public ToWsetExpression(boolean createIfNonExistent, boolean removeIfZero) {
        super(UnresolvedDataType.INSTANCE);
        this.createIfNonExistent = createIfNonExistent;
        this.removeIfZero = removeIfZero;
    }

    public boolean getCreateIfNonExistent() { return createIfNonExistent; }

    public boolean getRemoveIfZero() { return removeIfZero; }

    @Override
    public DataType setInputType(DataType input, VerificationContext context) {
        super.setInputType(input, context);
        if (input == null) return null;
        return outputType(input);
    }

    @Override
    public DataType setOutputType(DataType output, VerificationContext context) {
        if ( ! (output instanceof WeightedSetDataType))
            throw new VerificationException(this, "This creates a WeightedSet, but type " + output.getName() + " is needed");
        super.setOutputType(output, context);
        return getInputType(context);
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setCurrentType(outputType(context.getCurrentType()));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    protected void doExecute(ExecutionContext context) {
        FieldValue input = context.getCurrentValue();
        WeightedSet output = outputType(input.getDataType()).createFieldValue();
        output.add(input);
        context.setCurrentValue(output);
    }

    private WeightedSetDataType outputType(DataType inputType) {
        return DataType.getWeightedSet(inputType, createIfNonExistent, removeIfZero);
    }

    @Override
    public DataType createdOutputType() { return UnresolvedDataType.INSTANCE; }

    @Override
    public String toString() {
        return "to_wset" +
               (createIfNonExistent ? " create_if_non_existent" : "") +
               (removeIfZero ? " remove_if_zero" : "");
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ToWsetExpression rhs)) return false;

        if (createIfNonExistent != rhs.createIfNonExistent) return false;
        if (removeIfZero != rhs.removeIfZero) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + createIfNonExistent.hashCode() + removeIfZero.hashCode();
    }

}
