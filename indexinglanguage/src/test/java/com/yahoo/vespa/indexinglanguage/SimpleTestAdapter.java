// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.FieldPath;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.FieldValueAdapter;
import com.yahoo.vespa.indexinglanguage.expressions.VerificationException;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Simon Thoresen Hult
 */
public class SimpleTestAdapter implements FieldValueAdapter {

    final Map<String, DataType> types = new HashMap<>();
    final Map<String, FieldValue> values = new HashMap<>();

    public SimpleTestAdapter(Field... fields) {
        for (Field field : fields) {
            types.put(field.getName(), field.getDataType());
        }
    }

    public SimpleTestAdapter createField(Field field) {
        types.put(field.getName(), field.getDataType());
        return this;
    }

    @Override
    public DataType getInputType(Expression exp, String fieldName) {
        return types.get(fieldName);
    }

    @Override
    public FieldValue getInputValue(String fieldName) {
        return values.get(fieldName);
    }

    @Override
    public FieldValue getInputValue(FieldPath fieldPath) {
        return values.get(fieldPath.toString());
    }

    @Override
    public void tryOutputType(Expression exp, String fieldName, DataType valueType) {
        DataType fieldType = types.get(fieldName);
        if (fieldType == null) {
            throw new VerificationException(exp, "Field '" + fieldName + "' not found.");
        }
        if (!fieldType.isAssignableFrom(valueType)) {
            throw new VerificationException(exp, "Can not assign " + valueType.getName() + " to field '" +
                                                 fieldName + "' which is " + fieldType.getName() + ".");
        }
    }

    public SimpleTestAdapter setValue(String fieldName, FieldValue fieldValue) {
        values.put(fieldName, fieldValue);
        return this;
    }

    @Override
    public SimpleTestAdapter setOutputValue(Expression exp, String fieldName, FieldValue fieldValue) {
        values.put(fieldName, fieldValue);
        return this;
    }

    @Override
    public boolean isComplete() {
        return false;
    }

}
