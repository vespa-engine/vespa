// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructuredDataType;

/**
 * The names of the struct fields holding the key and the value of a field with fast map search,
 * which is either a map, or an array of a struct acting as a map entry.
 *
 * @author arnej
 */
public record FastMapSearchFields(String keyField, String valueField) {

    public static final String MAP_KEY = "key";
    public static final String MAP_VALUE = "value";

    /** The fields of a map, which are always named key and value. */
    public static final FastMapSearchFields MAP = new FastMapSearchFields(MAP_KEY, MAP_VALUE);

    /** Returns the type of the key in a field of the given type, or null if there is no such key. */
    public DataType keyType(DataType fieldType) {
        return subType(fieldType, keyField);
    }

    /** Returns the type of the value in a field of the given type, or null if there is no such value. */
    public DataType valueType(DataType fieldType) {
        return subType(fieldType, valueField);
    }

    /** Returns whether a field of the given type is an array of struct, rather than a map. */
    public static boolean isArrayOfStruct(DataType fieldType) {
        return fieldType instanceof ArrayDataType array && array.getNestedType() instanceof StructuredDataType;
    }

    private static DataType subType(DataType fieldType, String name) {
        if (fieldType instanceof MapDataType map) {
            if (name.equals(MAP_KEY)) return map.getKeyType();
            if (name.equals(MAP_VALUE)) return map.getValueType();
            return null;
        }
        if (fieldType instanceof ArrayDataType array && array.getNestedType() instanceof StructuredDataType struct) {
            var field = struct.getField(name);
            return field == null ? null : field.getDataType();
        }
        return null;
    }

}
