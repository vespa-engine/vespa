// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructuredDataType;

/**
 * The type of a field with fast map search, the name of its lookup field, and the names of the struct fields
 * holding its key and value. The field is either a map, or an array of a struct acting as a map entry.
 *
 * @author arnej
 */
public record FastMapSearchFields(DataType fieldType, String lookupName, String keyField, String valueField) {

    public static final String MAP_KEY = "key";
    public static final String MAP_VALUE = "value";

    /** Returns the fast map search fields of a map field, whose key and value are always named key and value. */
    public static FastMapSearchFields forMap(DataType fieldType, String lookupName) {
        return new FastMapSearchFields(fieldType, lookupName, MAP_KEY, MAP_VALUE);
    }

    /** Returns the fast map search fields of an array of struct field, with key and value named as given. */
    public static FastMapSearchFields forArrayOfStruct(DataType fieldType, String lookupName, String keyField, String valueField) {
        return new FastMapSearchFields(fieldType, lookupName, keyField, valueField);
    }

    /** Returns whether this is the fields of a map, rather than an array of struct. */
    public boolean isMap() {
        return fieldType instanceof MapDataType;
    }

    /** Returns whether this is the fields of an array of struct, rather than a map. */
    public boolean isArrayOfStruct() {
        return isArrayOfStruct(fieldType);
    }

    /** Returns the type of the key, or null if there is no such key. */
    public DataType keyType() {
        return subType(keyField);
    }

    /** Returns the type of the value, or null if there is no such value. */
    public DataType valueType() {
        return subType(valueField);
    }

    /** Returns whether the field has a struct field, or for a map a key or value, with the given name. */
    public boolean hasSubField(String name) {
        return subType(name) != null;
    }

    /** Returns whether a field of the given type is an array of struct, rather than a map. */
    public static boolean isArrayOfStruct(DataType fieldType) {
        return fieldType instanceof ArrayDataType array && array.getNestedType() instanceof StructuredDataType;
    }

    private DataType subType(String name) {
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
