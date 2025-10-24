// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructuredDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.MapFieldValue;
import com.yahoo.document.datatypes.StructuredFieldValue;

import java.util.Objects;

/**
 * Returns a value from a map by key lookup.
 *
 * @author bratseth
 */
public final class GetValueExpression extends Expression {

    private final String mapKey;

    public GetValueExpression(String mapKey) {
        this.mapKey = mapKey;
    }

    public String getMapKey() { return mapKey; }

    @Override
    public DataType setInputType(DataType inputType, TypeContext context) {
        super.setInputType(inputType, context);
        if (inputType == null) return null;
        if ( ! (inputType instanceof MapDataType mapInputType))
            throw new VerificationException(this, "Expected a Map input, got " + inputType.getName());
        return mapInputType.getValueType();
    }

    @Override
    public DataType setOutputType(DataType outputType, TypeContext context) {
        super.setOutputType(outputType, context);
        return getInputType(context);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        MapFieldValue<?,?> input = (MapFieldValue<?,?>)context.getCurrentValue();
        var key = input.getDataType().getKeyType().getPrimitiveType().createFieldValue(mapKey);
        context.setCurrentValue(input.get(key));
    }

    @Override
    public String toString() {
        return "get_value " + mapKey;
    }

    @Override
    public boolean equals(Object o) {
        if ( ! (o instanceof GetValueExpression other)) return false;
        if ( ! this.mapKey.equals(other.mapKey)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), mapKey);
    }

}
