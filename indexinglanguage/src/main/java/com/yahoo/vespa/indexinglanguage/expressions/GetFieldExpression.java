// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructuredDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StructuredFieldValue;

/**
 * Returns the value of a struct field.
 *
 * @author Simon Thoresen Hult
 */
public final class GetFieldExpression extends Expression {

    // The special "field names" used to access a map entry key and value
    private static final String keyName = "$key";
    private static final String valueName = "$value";

    private final String structFieldName;

    public GetFieldExpression(String structFieldName) {
        super(UnresolvedDataType.INSTANCE);
        this.structFieldName = structFieldName;
    }

    public String getFieldName() { return structFieldName; }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        super.setInputType(inputType, context);
        return getFieldType(context);
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        super.setOutputType(getFieldType(context), outputType, context);
        return AnyDataType.instance;
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setCurrentType(getFieldType(context));
    }

    private DataType getFieldType(VerificationContext context) {
        DataType input = context.getCurrentType();
        if (input instanceof MapDataType entryInput) {
            if (structFieldName.equals(keyName))
                return entryInput.getKeyType();
            else if (structFieldName.equals(valueName))
                return entryInput.getValueType();
            else if (entryInput.getValueType() instanceof StructuredDataType structInput)
                return getStructFieldType(structInput);
        }
        else if (input instanceof StructuredDataType structInput) {
            return getStructFieldType(structInput);
        }
        throw new VerificationException(this, "Expected a struct or map, bit got an " + input.getName());
    }

    private DataType getStructFieldType(StructuredDataType structInput) {
        Field field = structInput.getField(structFieldName);
        if (field == null)
            throw new VerificationException(this, "Field '" + structFieldName + "' not found in struct type '" +
                                                  structInput.getName() + "'");
        return field.getDataType();
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        FieldValue input = context.getCurrentValue();
        if (input instanceof StructuredFieldValue struct)
            executeStructField(struct, context);
        else if (input instanceof MapEntryFieldValue entry)
            executeMapEntry(entry, context);
        else
            throw new IllegalArgumentException("In " + this + ": Expected structured input, got " + input.getDataType().getName());
    }

    private void executeMapEntry(MapEntryFieldValue entry, ExecutionContext context) {
        if (structFieldName.equals(keyName))
            context.setCurrentValue(entry.getKey());
        else if (structFieldName.equals(valueName))
            context.setCurrentValue(entry.getValue());
        else if (entry.getValue() instanceof StructuredFieldValue struct)
            executeStructField(struct, context);
        else
            throw new IllegalArgumentException("In " + this + ": Expected structured input, got " +
                                               entry.getValue().getDataType().getName());
    }

    private void executeStructField(StructuredFieldValue struct, ExecutionContext context) {
        Field field = struct.getField(structFieldName);
        if (field == null)
            throw new IllegalArgumentException("In " + this +": Field '" + structFieldName + "' not found in struct type '" +
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
