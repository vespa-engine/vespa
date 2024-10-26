// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.StructDataType;
import com.yahoo.document.StructuredDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StructuredFieldValue;

/**
 * Returns the value of a struct field.
 *
 * @author Simon Thoresen Hult
 */
public final class GetFieldExpression extends Expression {

    private final String structFieldName;

    public GetFieldExpression(String structFieldName) {
        super(UnresolvedDataType.INSTANCE);
        this.structFieldName = structFieldName;
    }

    public String getFieldName() { return structFieldName; }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        super.setInputType(inputType, context);
        return getStructFieldType(context);
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        super.setOutputType(getStructFieldType(context), outputType, context);
        return AnyDataType.instance;
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setCurrentType(getStructFieldType(context));
    }

    private DataType getStructFieldType(VerificationContext context) {
        DataType input = context.getCurrentType();
        if ( ! (input instanceof StructuredDataType structInput))
            throw new VerificationException(this, "Expected structured input, got " + input.getName());
        Field field = structInput.getField(structFieldName);
        if (field == null)
            throw new VerificationException(this, "Field '" + structFieldName + "' not found in struct type '" +
                                                  input.getName() + "'");
        return field.getDataType();
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        FieldValue input = context.getCurrentValue();
        if (!(input instanceof StructuredFieldValue struct))
            throw new IllegalArgumentException("Expected structured input, got " + input.getDataType().getName());

        Field field = struct.getField(structFieldName);
        if (field == null)
            throw new IllegalArgumentException("Field '" + structFieldName + "' not found in struct type '" +
                                               struct.getDataType().getName() + "'");
        context.setCurrentValue(struct.getFieldValue(field));
    }

    @Override
    public DataType createdOutputType() {
        return UnresolvedDataType.INSTANCE;
    }

    @Override
    public String toString() {
        return "get_field " + structFieldName;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GetFieldExpression rhs)) return false;
        if (!structFieldName.equals(rhs.structFieldName)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + structFieldName.hashCode();
    }

}
