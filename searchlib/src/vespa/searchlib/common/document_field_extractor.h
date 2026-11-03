// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/hash_map.h>

#include <memory>
#include <string>

namespace document {

class Document;
class FieldValue;
class FieldPath;
class FieldPathEntry;

} // namespace document

namespace search {

/**
 * Class used to extract a field value from a document field or from a
 * nested field in an array/map of structs.
 *
 * A nested field is extracted as an array with one element per element in
 * the array/map, so element ids are preserved. When a struct element lacks
 * the nested field, the array element is set to the undefined value used
 * by attributes (see search::attribute::getUndefined), i.e. an empty string
 * for string fields. Only primitive nested field types are supported.
 */
class DocumentFieldExtractor {
    using Document = document::Document;
    using FieldPath = document::FieldPath;
    using FieldPathEntry = document::FieldPathEntry;
    using FieldValue = document::FieldValue;

    const Document&                                              _doc;
    vespalib::hash_map<std::string, std::unique_ptr<FieldValue>> _cachedFieldValues;

    [[nodiscard]] const FieldValue* getCachedFieldValue(const FieldPathEntry& fieldPathEntry);
    [[nodiscard]] std::unique_ptr<FieldValue> getSimpleFieldValue(const FieldPath& fieldPath);
    [[nodiscard]] std::unique_ptr<FieldValue> extractFieldFromStructArray(const FieldPath& fieldPath);
    [[nodiscard]] std::unique_ptr<FieldValue> extractKeyFieldFromMap(const FieldPath& fieldPath);
    [[nodiscard]] std::unique_ptr<FieldValue> extractValueFieldFromPrimitiveMap(const FieldPath& fieldPath);
    [[nodiscard]] std::unique_ptr<FieldValue> extractValueFieldFromStructMap(const FieldPath& fieldPath);

public:
    explicit DocumentFieldExtractor(const Document& doc);
    ~DocumentFieldExtractor();

    [[nodiscard]] std::unique_ptr<FieldValue> getFieldValue(const FieldPath& fieldPath);

    /**
     * Check if fieldPath is in a supported form, including that a nested
     * field has a primitive type.
     */
    [[nodiscard]] static bool isSupported(const FieldPath& fieldPath);
};

} // namespace search
