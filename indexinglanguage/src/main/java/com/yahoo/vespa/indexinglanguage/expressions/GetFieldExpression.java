// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructuredDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.datatypes.StructuredFieldValue;

/**
 * @author Simon Thoresen Hult
 */
public final class GetFieldExpression extends Expression {

    // The special "field names" used to access a map entry key and value
    private static final String keyName = "$key";
    private static final String valueName = "$value";

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
        if (input instanceof StructuredDataType)
            verifyStructField(((StructuredDataType)input), context);
        else if (input instanceof MapDataType)
            verifyMapEntry(((MapDataType)input), context);
        else
            throw new VerificationException(this, "Expected structured input, got " + input.getName());
    }

    private void verifyMapEntry(MapDataType entryType, VerificationContext context) {
        if (fieldName.equals(keyName))
            context.setCurrentType(entryType.getKeyType());
        else if (fieldName.equals(valueName))
            context.setCurrentType(entryType.getValueType());
        else if (entryType.getValueType() instanceof StructuredDataType)
            verifyStructField(((StructuredDataType) entryType.getValueType()), context);
        else
            throw new VerificationException(this, "Expected a structured map value type, got " +
                                                  entryType.getValueType().getName());
    }

    private void verifyStructField(StructuredDataType structType, VerificationContext context) {
        Field field = structType.getField(fieldName);
        if (field == null)
            throw new VerificationException(this, "Field '" + fieldName + "' not found in struct type '" +
                                                  structType.getName() + "'");
        context.setCurrentType(field.getDataType());
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
        if (fieldName.equals(keyName))
            context.setCurrentValue(entry.getKey());
        else if (fieldName.equals(valueName))
            context.setCurrentValue(entry.getValue());
        else if (entry.getValue() instanceof StructuredFieldValue struct)
            executeStructField(struct, context);
        else
            throw new IllegalArgumentException("In " + this + ": Expected structured input, got " +
                                               entry.getValue().getDataType().getName());
    }

    private void executeStructField(StructuredFieldValue struct, ExecutionContext context) {
        Field field = struct.getField(fieldName);
        if (field == null)
            throw new IllegalArgumentException("In " + this +": Field '" + fieldName + "' not found in struct type '" +
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
