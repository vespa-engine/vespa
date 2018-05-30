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
};

}
