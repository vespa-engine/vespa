// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StructuredFieldValue;

/**
 * @author Simon Thoresen Hult
 */
public class SimpleDocument {

    private final Document document;

    public SimpleDocument(Document document) {
        this.document = document;
    }

    public final Object get(Field field) {
        return get(document, field);
    }

    public final Object get(String fieldName) {
        return get(document, document.getField(fieldName));
    }

    public final Object set(Field field, Object value) {
        return set(document, field, value);
    }

    public final Object set(String fieldName, Object value) {
        return set(document.getField(fieldName), value);
    }

    public final Object remove(Field field) {
        return remove(document, field);
    }

    public final Object remove(String fieldName) {
        return remove(document.getField(fieldName));
    }

    public static Object get(StructuredFieldValue struct, Field field) {
        return field == null ? null : unwrapValue(struct.getFieldValue(field));
    }

    public static Object set(StructuredFieldValue struct, Field field, Object value) {
        return unwrapValue(struct.setFieldValue(field, wrapValue(field.getDataType(), value)));
    }

    public static Object remove(StructuredFieldValue struct, Field field) {
        return field == null ? null : unwrapValue(struct.removeFieldValue(field));
    }

    private static FieldValue wrapValue(DataType type, Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof FieldValue) {
            return (FieldValue)val;
        }
        FieldValue ret = type.createFieldValue();
        ret.assign(val);
        return ret;
    }

    private static Object unwrapValue(FieldValue val) {
        if (val == null) {
            return null;
        }
        return val.getWrappedValue();
    }
}
