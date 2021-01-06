// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.document;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.MapDataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.StructDataType;

/**
 * Utils used to check whether a complex field supports being represented as struct field attributes.
 *
 * Currently we support:
 *   - array of simple struct
 *   - map of primitive type to simple struct
 *   - map of primitive type to primitive type
 *
 * A simple struct can contain fields of any type, but only fields of primitive type can be defined as
 * struct field attributes in the complex field using the simple struct.
 *
 * @author geirst
 */
public class ComplexAttributeFieldUtils {

    public static boolean isSupportedComplexField(ImmutableSDField field) {
        return (isArrayOfSimpleStruct(field) ||
                isMapOfSimpleStruct(field) ||
                isMapOfPrimitiveType(field));
    }

    public static boolean isArrayOfSimpleStruct(ImmutableSDField field) {
        if (field.getDataType() instanceof ArrayDataType) {
            ArrayDataType arrayType = (ArrayDataType)field.getDataType();
            return isStructWithPrimitiveStructFieldAttributes(arrayType.getNestedType(), field);
        } else {
            return false;
        }
    }

    public static boolean isMapOfSimpleStruct(ImmutableSDField field) {
        if (field.getDataType() instanceof MapDataType) {
            MapDataType mapType = (MapDataType)field.getDataType();
            return isPrimitiveType(mapType.getKeyType()) &&
                    isStructWithPrimitiveStructFieldAttributes(mapType.getValueType(),
                            field.getStructField("value"));
        } else {
            return false;
        }
    }

    public static boolean isMapOfPrimitiveType(ImmutableSDField field) {
        if (field.getDataType() instanceof MapDataType) {
            MapDataType mapType = (MapDataType)field.getDataType();
            return isPrimitiveType(mapType.getKeyType()) &&
                    isPrimitiveType(mapType.getValueType());
        } else {
            return false;
        }
    }

    private static boolean isStructWithPrimitiveStructFieldAttributes(DataType type, ImmutableSDField field) {
        if (type instanceof StructDataType &&
                !(type.equals(PositionDataType.INSTANCE))) {
            for (ImmutableSDField structField : field.getStructFields()) {
                Attribute attribute = structField.getAttributes().get(structField.getName());
                if (attribute != null) {
                    if (!isPrimitiveType(attribute)) {
                        return false;
                    }
                } else if (structField.wasConfiguredToDoAttributing()) {
                    if (!isPrimitiveType(structField.getDataType())) {
                        return false;
                    }
                }
            }
            return true;
        } else {
            return false;
        }
    }

    public static boolean isPrimitiveType(Attribute attribute) {
        return attribute.getCollectionType().equals(Attribute.CollectionType.SINGLE) &&
                isPrimitiveType(attribute.getDataType());
    }

    public static boolean isPrimitiveType(DataType dataType) {
        return dataType.equals(DataType.BYTE) ||
                dataType.equals(DataType.INT) ||
                dataType.equals(DataType.LONG) ||
                dataType.equals(DataType.FLOAT) ||
                dataType.equals(DataType.DOUBLE) ||
                dataType.equals(DataType.STRING);
    }

    public static boolean isComplexFieldWithOnlyStructFieldAttributes(ImmutableSDField field) {
        if (isArrayOfSimpleStruct(field)) {
            return hasOnlyStructFieldAttributes(field);
        } else if (isMapOfSimpleStruct(field)) {
            return hasSingleAttribute(field.getStructField("key")) &&
                    hasOnlyStructFieldAttributes(field.getStructField("value"));
        } else if (isMapOfPrimitiveType(field)) {
            return hasSingleAttribute(field.getStructField("key")) &&
                    hasSingleAttribute(field.getStructField("value"));
        }
        return false;
    }

    private static boolean hasOnlyStructFieldAttributes(ImmutableSDField field) {
        for (ImmutableSDField structField : field.getStructFields()) {
            if (!hasSingleAttribute(structField)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasSingleAttribute(ImmutableSDField field) {
        if (field.getAttributes().size() != 1) {
            return false;
        }
        return (field.getAttributes().get(field.getName()) != null);
    }

}
