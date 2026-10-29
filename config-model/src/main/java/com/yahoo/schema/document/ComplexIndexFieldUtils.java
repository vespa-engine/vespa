// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    /** Names which would make the backend treat a struct field index as part of an uri index. */
    private static final Set<String> uriPartNames = Set.of("all", "scheme", "host", "port", "path",
                                                           "query", "fragment", "hostname");

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
        // Indexing the complex field as a whole is not supported, also when inherited by its struct fields
        if (field.wasConfiguredToDoIndexing()) return false;
        if ( ! structField.getDataType().equals(DataType.STRING)) return false;
        if (structField.getMatching().getType() == MatchType.GRAM) return false;
        String path = ComplexAttributeFieldUtils.localStructFieldName(field, structField);
        if (uriPartNames.contains(path)) return false;
        return isSupportedPath(field.getDataType(), path);
    }

    private static boolean isSupportedPath(DataType type, String path) {
        if (type instanceof ArrayDataType array) {
            return isSimpleStruct(array.getNestedType()) && ! path.contains(".");
        }
        if (type instanceof MapDataType map) {
            if ( ! ComplexAttributeFieldUtils.isPrimitiveType(map.getKeyType())) return false;
            if (path.equals("key") || path.equals("value")) return true;
            return path.startsWith("value.") &&
                   isSimpleStruct(map.getValueType()) &&
                   ! path.substring("value.".length()).contains(".");
        }
        return false;
    }

    private static boolean isSimpleStruct(DataType type) {
        return type instanceof StructDataType && ! GeoPos.isPos(type);
    }

}
