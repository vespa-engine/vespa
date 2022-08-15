// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "document.h"
#include <vespa/document/fieldvalue/document.h>

namespace vsm {

typedef vespalib::CloneablePtr<document::FieldValue> FieldValueContainer;
typedef document::FieldPath FieldPath; // field path to navigate a field value
typedef std::vector<FieldPath> FieldPathMapT; // map from field id to field path
typedef std::shared_ptr<FieldPathMapT> SharedFieldPathMap;

class StorageDocument : public Document {
public:
    typedef std::unique_ptr<StorageDocument> UP;

    class SubDocument {
    public:
        SubDocument() : _fieldValue(nullptr) {}
        SubDocument(document::FieldValue *fv, document::FieldValue::PathRange nested) :
                _fieldValue(fv),
                _range(nested)
        { }

        const document::FieldValue *getFieldValue() const { return _fieldValue; }
        void setFieldValue(document::FieldValue *fv) { _fieldValue = fv; }
        const document::FieldValue::PathRange & getRange() const { return _range; }
        void swap(SubDocument &rhs) {
            std::swap(_fieldValue, rhs._fieldValue);
            std::swap(_range, rhs._range);
        }
    private:
        FieldPath::const_iterator begin() const;
        FieldPath::const_iterator end() const;
        document::FieldValue *_fieldValue;
        document::FieldValue::PathRange _range;
    };
public:
    StorageDocument(document::Document::UP doc, const SharedFieldPathMap &fim, size_t fieldNoLimit);
    StorageDocument(const StorageDocument &) = delete;
    StorageDocument & operator = (const StorageDocument &) = delete;
    ~StorageDocument();

    const document::Document &docDoc() const { return *_doc; }
    bool valid() const { return _doc.get() != nullptr; }
    const SubDocument &getComplexField(FieldIdT fId) const;
    const document::FieldValue *getField(FieldIdT fId) const override;
    bool setField(FieldIdT fId, document::FieldValue::UP fv) override ;
private:
    document::Document::UP _doc;
    SharedFieldPathMap     _fieldMap;
    mutable std::vector<SubDocument> _cachedFields;
    mutable std::vector<document::FieldValue::UP> _backedFields;
};

}

