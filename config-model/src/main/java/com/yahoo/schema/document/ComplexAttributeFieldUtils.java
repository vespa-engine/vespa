// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.MapDataType;
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
        return isSupportedComplexField(field, false);
    }

    // TODO: Remove the stricterValidation flag when this is changed to being always on.
    public static boolean isSupportedComplexField(ImmutableSDField field, boolean stricterValidation) {
        return (isArrayOfSimpleStruct(field, stricterValidation) ||
                isMapOfSimpleStruct(field, stricterValidation) ||
                isMapOfPrimitiveType(field));
    }

    public static boolean isArrayOfSimpleStruct(ImmutableSDField field, boolean stricterValidation) {
        if (field.getDataType() instanceof ArrayDataType) {
            ArrayDataType arrayType = (ArrayDataType)field.getDataType();
            return isStructWithPrimitiveStructFieldAttributes(arrayType.getNestedType(), field, stricterValidation);
        } else {
            return false;
        }
    }

    public static boolean isMapOfSimpleStruct(ImmutableSDField field, boolean stricterValidation) {
        if (field.getDataType() instanceof MapDataType) {
            MapDataType mapType = (MapDataType)field.getDataType();
            return isPrimitiveType(mapType.getKeyType()) &&
                    isStructWithPrimitiveStructFieldAttributes(mapType.getValueType(),
                            field.getStructField("value"), stricterValidation);
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

    private static boolean isStructWithPrimitiveStructFieldAttributes(DataType type, ImmutableSDField field, boolean stricterValidation) {
        if (type instanceof StructDataType && ! GeoPos.isPos(type)) {
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
                if (stricterValidation && !structField.isImportedField() && hasStructFieldAttributes(structField)) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    private static boolean hasStructFieldAttributes(ImmutableSDField field) {
        for (var structField : field.getStructFields()) {
            var attribute = structField.getAttributes().get(structField.getName());
            if (attribute != null) {
                return true;
            }
            if (hasStructFieldAttributes(structField)) {
                return true;
            }
        }
        return false;
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
        if (isArrayOfSimpleStruct(field, false)) {
            return hasOnlyStructFieldAttributes(field);
        } else if (isMapOfSimpleStruct(field, false)) {
            return (field.getStructField("key").hasSingleAttribute()) &&
                    hasOnlyStructFieldAttributes(field.getStructField("value"));
        } else if (isMapOfPrimitiveType(field)) {
            return (field.getStructField("key").hasSingleAttribute() &&
                    field.getStructField("value").hasSingleAttribute());
        }
        return false;
    }

    private static boolean hasOnlyStructFieldAttributes(ImmutableSDField field) {
        for (ImmutableSDField structField : field.getStructFields()) {
            if (!structField.hasSingleAttribute()) {
                return false;
            }
        }
        return true;
    }

}
