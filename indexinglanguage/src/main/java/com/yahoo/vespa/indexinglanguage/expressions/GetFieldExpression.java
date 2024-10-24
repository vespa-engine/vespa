// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.StructuredDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StructuredFieldValue;

/**
 * @author Simon Thoresen Hult
 */
public final class GetFieldExpression extends Expression {

    private final String fieldName;

    public GetFieldExpression(String fieldName) {
        super(UnresolvedDataType.INSTANCE);
        this.fieldName = fieldName;
    }

    public String getFieldName() { return fieldName; }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        super.setInputType(inputType, context);
        return context.getFieldType(fieldName, this);
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        super.setOutputType(context.getFieldType(fieldName, this), outputType, context);
        return AnyDataType.instance;
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType input = context.getCurrentType();
        if (!(input instanceof StructuredDataType)) {
            throw new VerificationException(this, "Expected structured input, got " + input.getName());
        }
        Field field = ((StructuredDataType)input).getField(fieldName);
        if (field == null) {
            throw new VerificationException(this, "Field '" + fieldName + "' not found in struct type '" +
                                                  input.getName() + "'");
        }
        context.setCurrentType(field.getDataType());
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        FieldValue input = context.getCurrentValue();
        if (!(input instanceof StructuredFieldValue struct))
            throw new IllegalArgumentException("Expected structured input, got " + input.getDataType().getName());

        Field field = struct.getField(fieldName);
        if (field == null)
            throw new IllegalArgumentException("Field '" + fieldName + "' not found in struct type '" +
                                               struct.getDataType().getName() + "'");
        context.setCurrentValue(struct.getFieldValue(field));
    }

    @Override
    public DataType createdOutputType() {
        return UnresolvedDataType.INSTANCE;
    }

    @Override
    public String toString() {
        return "get_field " + fieldName;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GetFieldExpression rhs)) return false;
        if (!fieldName.equals(rhs.fieldName)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + fieldName.hashCode();
    }

}
