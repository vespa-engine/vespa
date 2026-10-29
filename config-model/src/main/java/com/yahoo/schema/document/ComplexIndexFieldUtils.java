// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.schema.Schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Utils used to find the struct fields of a complex field which are indexed ('indexing: index'),
 * and to check whether these can be represented as struct field indexes in an indexed search cluster.
 *
 * Currently, we support string struct fields in:
 *   - array of struct: "field.f"
 *   - map of primitive type to string: "field.key" and "field.value"
 *   - map of primitive type to struct: "field.key" and "field.value.f"
 *
 * with text, word or exact matching (not gram).
 *
 * Each such struct field is indexed like an array of string, with one element per element in the complex field.
 *
 * @author arnej
 */
public class ComplexIndexFieldUtils {

    /** Returns all struct fields (at any depth) of the given field which were configured with 'indexing: index'. */
    public static List<ImmutableSDField> indexedStructFields(ImmutableSDField field) {
        var result = new ArrayList<ImmutableSDField>();
        if (field.isImportedField() || ! field.usesStructOrMap()) return result;
        addIndexedStructFields(field, result);
        return result;
    }

    private static void addIndexedStructFields(ImmutableSDField field, List<ImmutableSDField> result) {
        for (var structField : field.getStructFields()) {
            if (structField.wasConfiguredToDoIndexing())
                result.add(structField);
            addIndexedStructFields(structField, result);
        }
    }

    /** Returns the struct fields of the given field which are configured with 'indexing: index' and are supported. */
    public static List<ImmutableSDField> supportedIndexedStructFields(ImmutableSDField field) {
        return indexedStructFields(field).stream()
                                         .filter(structField -> isSupportedIndexedStructField(field, structField))
                                         .toList();
    }

    /** Returns the struct fields of the given field which are configured with 'indexing: index' but are not supported. */
    public static List<ImmutableSDField> unsupportedIndexedStructFields(ImmutableSDField field) {
        return indexedStructFields(field).stream()
                                         .filter(structField -> ! isSupportedIndexedStructField(field, structField))
                                         .toList();
    }

    /** Returns whether the given field has any supported struct field index. */
    public static boolean hasSupportedIndexedStructFields(ImmutableSDField field) {
        return ! supportedIndexedStructFields(field).isEmpty();
    }

    /** Returns whether the given struct field, of the given complex field, can be indexed. */
    public static boolean isSupportedIndexedStructField(ImmutableSDField field, ImmutableSDField structField) {
        return unsupportedReason(field, structField).isEmpty();
    }

    /**
     * Returns whether the given field is indexed in an indexed search cluster: either it does indexing itself,
     * or it is a supported struct field index (whose own indexing script may have been replaced by that of its parent).
     */
    public static boolean doesIndexing(ImmutableSDField field, Schema schema) {
        if (field.doesIndexing()) return true;
        if (schema == null || ! field.getName().contains(".")) return false;
        ImmutableSDField topLevelField = schema.getField(field.getName().substring(0, field.getName().indexOf('.')));
        if (topLevelField == null) return false;
        return supportedIndexedStructFields(topLevelField).stream()
                                                          .anyMatch(structField -> structField.getName().equals(field.getName()));
    }

    /** Returns why the given struct field, of the given complex field, cannot be indexed, or empty if it can. */
    public static Optional<String> unsupportedReason(ImmutableSDField field, ImmutableSDField structField) {
        // Indexing the complex field as a whole is not supported, also when inherited by its struct fields
        if (field.wasConfiguredToDoIndexing())
            return Optional.of("the complex field itself has 'indexing: index'");
        if ( ! structField.getDataType().equals(DataType.STRING))
            return Optional.of("type " + structField.getDataType().getName() + " is not string");
        if (structField.getMatching().getType() == MatchType.GRAM)
            return Optional.of("gram matching is not supported");
        return unsupportedPathReason(field.getDataType(), ComplexAttributeFieldUtils.localStructFieldName(field, structField));
    }

    private static Optional<String> unsupportedPathReason(DataType type, String path) {
        if (type instanceof ArrayDataType array && isSimpleStruct(array.getNestedType())) {
            if (path.contains("."))
                return Optional.of("only struct fields directly in the array elements can be indexed");
            return Optional.empty();
        }
        if (type instanceof MapDataType map) {
            if ( ! ComplexAttributeFieldUtils.isPrimitiveType(map.getKeyType()))
                return Optional.of("the map key type is not primitive");
            if (path.equals("key") || path.equals("value")) return Optional.empty();
            if ( ! path.startsWith("value.") || ! isSimpleStruct(map.getValueType()) ||
                 path.substring("value.".length()).contains("."))
                return Optional.of("only struct fields directly in the map values can be indexed");
            return Optional.empty();
        }
        return Optional.of("only supported in array of struct and map");
    }

    private static boolean isSimpleStruct(DataType type) {
        return type instanceof StructDataType && ! GeoPos.isPos(type);
    }

}
