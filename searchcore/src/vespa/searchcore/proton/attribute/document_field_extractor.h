// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <memory>

namespace document
{

class Document;
class FieldValue;
class FieldPath;
class FieldPathEntry;

}

namespace proton {

/**
 * Class used to extract a field value from a document field or from a
 * nested field in an array/map of structs.
 */
class DocumentFieldExtractor
{
    const document::Document &_doc;
    vespalib::hash_map<vespalib::string, std::unique_ptr<document::FieldValue>> _cachedFieldValues;

    const document::FieldValue *getCachedFieldValue(const document::FieldPathEntry &fieldPathEntry);
    std::unique_ptr<document::FieldValue> getSimpleFieldValue(const document::FieldPath &fieldPath);
    std::unique_ptr<document::FieldValue> getStructArrayFieldValue(const document::FieldPath &fieldPath);
    std::unique_ptr<document::FieldValue> getStructMapKeyFieldValue(const document::FieldPath &fieldPath);
    std::unique_ptr<document::FieldValue> getStructMapFieldValue(const document::FieldPath &fieldPath);

public:
    DocumentFieldExtractor(const document::Document &doc);
    ~DocumentFieldExtractor();

    std::unique_ptr<document::FieldValue> getFieldValue(const document::FieldPath &fieldPath);

    /**
     * Check if fieldPath is in a supported form.
     */
    static bool isSupported(const document::FieldPath &fieldPath);

    /**
     * Check if two field paths are compatible, i.e. same types in whole path
     * and same data type would be returned from getFieldValue().  This is
     * meant to be used when document type in received document doesn't match
     * the document type for the current config (can happen right before and
     * after live config change when validation override is used).
     */
    static bool isCompatible(const document::FieldPath &fieldPath1, const document::FieldPath &fieldPath2);
};

}
