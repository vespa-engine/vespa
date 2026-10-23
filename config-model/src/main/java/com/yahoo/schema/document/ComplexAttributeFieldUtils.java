// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructDataType;

import java.util.Collection;
import java.util.List;

/**
 * Utils used to check whether a complex field supports being represented as struct field attributes.
 *
 * Currently, we support:
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

    /** The prefix used when selecting a sub-field of the value struct of a map. */
    private static final String VALUE_PREFIX = "value.";

    public static boolean isSupportedComplexField(ImmutableSDField field) {
        return (isArrayOfSimpleStruct(field) ||
                isMapOfSimpleStruct(field) ||
                isMapOfPrimitiveType(field));
    }

    public static boolean isArrayOfSimpleStruct(ImmutableSDField field) {
        return isArrayOfSimpleStruct(field, List.of());
    }

    /**
     * As {@link #isArrayOfSimpleStruct(ImmutableSDField)}, but only requires the struct sub-fields named in
     * {@code selectedFields} to be eligible as struct field attributes. An empty collection means all
     * sub-fields are considered (same as the single-argument overload).
     */
    public static boolean isArrayOfSimpleStruct(ImmutableSDField field, Collection<String> selectedFields) {
        if (field.getDataType() instanceof ArrayDataType arrayType) {
            return isStructWithPrimitiveStructFieldAttributes(arrayType.getNestedType(), field, selectedFields);
        } else {
            return false;
        }
    }

    public static boolean isMapOfSimpleStruct(ImmutableSDField field) {
        return isMapOfSimpleStruct(field, List.of());
    }

    /**
     * As {@link #isMapOfSimpleStruct(ImmutableSDField)}, but only requires the sub-fields of the value struct
     * named "value.&lt;name&gt;" in {@code selectedFields} to be eligible as struct field attributes. A
     * selection naming no value sub-field means all of them are considered (same as the single-argument
     * overload).
     */
    public static boolean isMapOfSimpleStruct(ImmutableSDField field, Collection<String> selectedFields) {
        if (field.getDataType() instanceof MapDataType mapType) {
            return isPrimitiveType(mapType.getKeyType()) &&
                    isStructWithPrimitiveStructFieldAttributes(mapType.getValueType(),
                            field.getStructField("value"), selectedValueFields(selectedFields));
        } else {
            return false;
        }
    }

    /**
     * The sub-fields of the value struct of a map named in a struct field selection, i.e. the "value.&lt;name&gt;"
     * entries with the "value." prefix stripped.
     */
    private static List<String> selectedValueFields(Collection<String> selectedFields) {
        return selectedFields.stream()
                             .filter(name -> name.startsWith(VALUE_PREFIX))
                             .map(name -> name.substring(VALUE_PREFIX.length()))
                             .toList();
    }

    public static boolean isMapOfPrimitiveType(ImmutableSDField field) {
        if (field.getDataType() instanceof MapDataType mapType) {
            return isPrimitiveType(mapType.getKeyType()) &&
                    isPrimitiveType(mapType.getValueType());
        } else {
            return false;
        }
    }

    /**
     * The name of a struct sub-field as declared by the user, i.e. without the
     * "&lt;parent field name&gt;." prefix which is used internally to make the sub-field name unique.
     */
    public static String localStructFieldName(ImmutableSDField parent, ImmutableSDField structField) {
        String prefix = parent.getName() + ".";
        String name = structField.getName();
        return name.startsWith(prefix) ? name.substring(prefix.length()) : name;
    }

    private static boolean isStructWithPrimitiveStructFieldAttributes(DataType type, ImmutableSDField field,
                                                                      Collection<String> selectedFields) {
        if (type instanceof StructDataType && ! GeoPos.isPos(type)) {
            for (ImmutableSDField structField : field.getStructFields()) {
                if (!selectedFields.isEmpty() && !selectedFields.contains(localStructFieldName(field, structField))) {
                    continue;
                }
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
                if (!structField.isImportedField() && hasStructFieldAttributes(structField)) {
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
        return dataType.equals(DataType.BOOL) ||
                dataType.equals(DataType.BYTE) ||
                dataType.equals(DataType.INT) ||
                dataType.equals(DataType.LONG) ||
                dataType.equals(DataType.FLOAT) ||
                dataType.equals(DataType.DOUBLE) ||
                dataType.equals(DataType.STRING);
    }

    public static boolean isComplexFieldWithOnlyStructFieldAttributes(ImmutableSDField field) {
        return isComplexFieldWithOnlyStructFieldAttributes(field, List.of());
    }

    /**
     * As {@link #isComplexFieldWithOnlyStructFieldAttributes(ImmutableSDField)}, but only requires the struct
     * sub-fields named in {@code selectedFields} to have a single-value attribute. An empty collection means
     * all sub-fields are considered (same as the single-argument overload). For a map, the sub-fields of the
     * value struct are named "value.&lt;name&gt;".
     */
    public static boolean isComplexFieldWithOnlyStructFieldAttributes(ImmutableSDField field, Collection<String> selectedFields) {
        if (isArrayOfSimpleStruct(field, selectedFields)) {
            return hasOnlyStructFieldAttributes(field, selectedFields);
        } else if (isMapOfSimpleStruct(field, selectedFields)) {
            return (field.getStructField("key").hasSingleAttribute()) &&
                    hasOnlyStructFieldAttributes(field.getStructField("value"), selectedValueFields(selectedFields));
        } else if (isMapOfPrimitiveType(field)) {
            return (field.getStructField("key").hasSingleAttribute() &&
                    field.getStructField("value").hasSingleAttribute());
        }
        return false;
    }

    private static boolean hasOnlyStructFieldAttributes(ImmutableSDField field, Collection<String> selectedFields) {
        for (ImmutableSDField structField : field.getStructFields()) {
            if (!selectedFields.isEmpty() && !selectedFields.contains(localStructFieldName(field, structField))) {
                continue;
            }
            if (!structField.hasSingleAttribute()) {
                return false;
            }
        }
        return true;
    }

}
