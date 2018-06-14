// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.document;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.TemporaryStructuredDataType;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * Utils used to check whether a complex field supports being represented as struct field attributes.
 *
 * Currently we support:
 *   - array of simple struct
 *   - map of primitive type to simple struct
 *   - map of primitive type to primitive type
 *
 * @author geirst
 */
public class ComplexAttributeFieldUtils {

    public static boolean isSupportedComplexField(ImmutableSDField field, SDDocumentType docType) {
        return (isArrayOfSimpleStruct(field, docType) ||
                isMapOfSimpleStruct(field, docType) ||
                isMapOfPrimitiveType(field));
    }

    public static boolean isArrayOfSimpleStruct(ImmutableSDField field, SDDocumentType docType) {
        return isArrayOfSimpleStruct(field.getDataType(), Optional.of(docType));
    }

    public static boolean isArrayOfSimpleStruct(DataType fieldType) {
        return isArrayOfSimpleStruct(fieldType, Optional.empty());
    }

    private static boolean isArrayOfSimpleStruct(DataType fieldType, Optional<SDDocumentType> docType) {
        if (fieldType instanceof ArrayDataType) {
            ArrayDataType arrayType = (ArrayDataType)fieldType;
            return isSimpleStruct(arrayType.getNestedType(), docType);
        } else {
            return false;
        }
    }

    public static boolean isMapOfSimpleStruct(ImmutableSDField field, SDDocumentType docType) {
        return isMapOfSimpleStruct(field.getDataType(), Optional.of(docType));
    }

    public static boolean isMapOfSimpleStruct(DataType fieldType) {
        return isMapOfSimpleStruct(fieldType, Optional.empty());
    }

    private static boolean isMapOfSimpleStruct(DataType fieldType, Optional<SDDocumentType> docType) {
        if (fieldType instanceof MapDataType) {
            MapDataType mapType = (MapDataType)fieldType;
            return isPrimitiveType(mapType.getKeyType()) &&
                    isSimpleStruct(mapType.getValueType(), docType);
        } else {
            return false;
        }
    }

    public static boolean isMapOfPrimitiveType(ImmutableSDField field) {
        return isMapOfPrimitiveType(field.getDataType());
    }

    public static boolean isMapOfPrimitiveType(DataType fieldType) {
        if (fieldType instanceof MapDataType) {
            MapDataType mapType = (MapDataType)fieldType;
            return isPrimitiveType(mapType.getKeyType()) &&
                    isPrimitiveType(mapType.getValueType());
        } else {
            return false;
        }
    }

    private static boolean isSimpleStruct(DataType type, Optional<SDDocumentType> docType) {
        if (type instanceof StructDataType &&
                !(type.equals(PositionDataType.INSTANCE))) {
            StructDataType structType = (StructDataType) type;
            Collection<Field> structFields = getStructFields(structType, docType);
            if (structFields.isEmpty()) {
                return false;
            }
            for (Field innerField : structFields) {
                if (!isPrimitiveType(innerField.getDataType())) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    private static Collection<Field> getStructFields(StructDataType structType, Optional<SDDocumentType> docType) {
        // The struct data type might be unresolved at this point. If so we use the document type to resolve it.
        if (docType.isPresent() && (structType instanceof TemporaryStructuredDataType)) {
            SDDocumentType realStructType = docType.get().getOwnedType(structType.getName());
            if (structType != null) {
                return realStructType.getDocumentType().getFields();
            }
            return Collections.emptyList();
        } else {
            return structType.getFields();
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

    public static boolean isComplexFieldWithOnlyStructFieldAttributes(ImmutableSDField field, SDDocumentType docType) {
        if (isArrayOfSimpleStruct(field, docType)) {
            return hasOnlyStructFieldAttributes(field);
        } else if (isMapOfSimpleStruct(field, docType)) {
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
