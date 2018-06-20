// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::StringFieldValue
 * \ingroup fieldvalue
 *
 * \brief Wrapper for string field values.
 */
#pragma once

#include "literalfieldvalue.h"
#include <vespa/document/annotation/spantree.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/util/buffer.h>
#include <vespa/fastos/dynamiclibrary.h>

namespace document {

class FixedTypeRepo;
class DocumentTypeRepo;

class StringFieldValue : public LiteralFieldValue<StringFieldValue, DataType::T_STRING, true> {
public:
    typedef LiteralFieldValue<StringFieldValue, DataType::T_STRING, true> Parent;
    typedef std::vector<SpanTree::UP> SpanTrees;

    StringFieldValue() : Parent(), _annotationData() { }
    StringFieldValue(const string &value)
            : Parent(value), _annotationData() { }

    StringFieldValue(const StringFieldValue &rhs);

    StringFieldValue &operator=(const StringFieldValue &rhs);
    StringFieldValue &operator=(const vespalib::stringref &value) override;
    ~StringFieldValue();

    FieldValue &assign(const FieldValue &) override;

    void accept(FieldValueVisitor &visitor) override { visitor.visit(*this); }
    void accept(ConstFieldValueVisitor &visitor) const override { visitor.visit(*this); }
    StringFieldValue *clone() const override { return new StringFieldValue(*this); }
    int compare(const FieldValue &other) const override;
    void print(std::ostream &out, bool verbose, const std::string &indent) const override;
    void setSpanTrees(vespalib::ConstBufferRef serialized, const FixedTypeRepo &repo, uint8_t version,
                      bool isSerializedDataLongLived);
    void setSpanTrees(const SpanTrees &trees, const FixedTypeRepo &repo);
    SpanTrees getSpanTrees() const;
    vespalib::ConstBufferRef getSerializedAnnotations() const {
        return _annotationData ? _annotationData->getSerializedAnnotations() : vespalib::ConstBufferRef();
    }
    bool hasSpanTrees() const { return _annotationData ? _annotationData->hasSpanTrees() : false; }
    static const SpanTree *findTree(const SpanTrees &trees, const vespalib::stringref &name);
    void clearSpanTrees() {
        if (_annotationData) {
            doClearSpanTrees();
        }
    }

    using LiteralFieldValueB::operator=;
    DECLARE_IDENTIFIABLE(StringFieldValue);
private:
    void doClearSpanTrees();

    class AnnotationData {
    public:
        typedef std::vector<char> BackingBlob;
        typedef std::unique_ptr<AnnotationData> UP;
        VESPA_DLL_LOCAL AnnotationData(const AnnotationData & rhs);
        AnnotationData & operator = (const AnnotationData &) = delete;
        VESPA_DLL_LOCAL AnnotationData(vespalib::ConstBufferRef serialized, const FixedTypeRepo &repo,
                                       uint8_t version, bool isSerializedDataLongLived);

        bool hasSpanTrees() const { return _serialized.size() > 0u; }
        vespalib::ConstBufferRef getSerializedAnnotations() const { return _serialized; }
        VESPA_DLL_LOCAL SpanTrees getSpanTrees() const;
    private:
        vespalib::ConstBufferRef _serialized;
        BackingBlob              _backingBlob;
        const DocumentTypeRepo  &_repo;
        const DocumentType      &_docType;
        uint8_t                  _version;
    };
    VESPA_DLL_LOCAL AnnotationData::UP copyAnnotationData() const;
    AnnotationData::UP _annotationData;
};

} // document

