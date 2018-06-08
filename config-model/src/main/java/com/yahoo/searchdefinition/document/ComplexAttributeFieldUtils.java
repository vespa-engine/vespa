package com.yahoo.searchdefinition.document;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.StructDataType;

/**
 * Utils used to check whether a complex field supports being represented as struct field attributes.
 *
 * Currently we support:
 *   - array of simple struct
 *   - map of primitive type to simple struct
 *
 * @author geirst
 */
public class ComplexAttributeFieldUtils {

    public static boolean isArrayOfSimpleStruct(ImmutableSDField field) {
        DataType fieldType = field.getDataType();
        if (fieldType instanceof ArrayDataType) {
            ArrayDataType arrayType = (ArrayDataType)fieldType;
            return isSimpleStruct(arrayType.getNestedType());
        } else {
            return false;
        }
    }

    public static boolean isMapOfSimpleStruct(ImmutableSDField field) {
        DataType fieldType = field.getDataType();
        if (fieldType instanceof MapDataType) {
            MapDataType mapType = (MapDataType)fieldType;
            return isPrimitiveType(mapType.getKeyType()) &&
                    isSimpleStruct(mapType.getValueType());
        } else {
            return false;
        }
    }

    public static boolean isMapOfPrimitiveType(ImmutableSDField field) {
        DataType fieldType = field.getDataType();
        if (fieldType instanceof MapDataType) {
            MapDataType mapType = (MapDataType)fieldType;
            return isPrimitiveType(mapType.getKeyType()) &&
                    isPrimitiveType(mapType.getValueType());
        } else {
            return false;
        }
    }

    private static boolean isSimpleStruct(DataType type) {
        if (type instanceof StructDataType &&
                !(type.equals(PositionDataType.INSTANCE))) {
            StructDataType structType = (StructDataType) type;
            for (Field innerField : structType.getFields()) {
                if (!isPrimitiveType(innerField.getDataType())) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    private static boolean isPrimitiveType(DataType dataType) {
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
