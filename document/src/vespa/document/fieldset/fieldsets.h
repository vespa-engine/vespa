// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/base/field.h>

namespace document {

class DocumentType;

class AllFields final : public FieldSet
{
public:
    static constexpr const char * NAME = "[all]";
    bool contains(const FieldSet&) const override { return true; }
    Type getType() const override { return Type::ALL; }
};

class NoFields final : public FieldSet
{
public:
    static constexpr const char * NAME = "[none]";
    bool contains(const FieldSet& f) const override { return f.getType() == Type::NONE; }
    Type getType() const override { return Type::NONE; }
};

class DocIdOnly final : public FieldSet
{
public:
    static constexpr const char * NAME = "[id]";
    bool contains(const FieldSet& fields) const override {
        return fields.getType() == Type::DOCID || fields.getType() == Type::NONE;
    }
    Type getType() const override { return Type::DOCID; }
};

class DocumentOnly final : public FieldSet
{
public:
    static constexpr const char * NAME = "[document]";
    bool contains(const FieldSet& fields) const override {
        return fields.getType() == Type::DOCUMENT_ONLY
            || fields.getType() == Type::DOCID
            || fields.getType() == Type::NONE;
    }
    Type getType() const override { return Type::DOCUMENT_ONLY; }
};

class FieldCollection : public FieldSet
{
public:
    typedef std::unique_ptr<FieldCollection> UP;

    FieldCollection(const DocumentType& docType, Field::Set set);
    FieldCollection(const FieldCollection &);
    FieldCollection(FieldCollection&&) noexcept = default;
    ~FieldCollection() override;
    FieldCollection& operator=(const FieldCollection&) = default;
    FieldCollection& operator=(FieldCollection&&) noexcept = default;

    bool contains(const FieldSet& fields) const override;
    Type getType() const override { return Type::SET; }

    const DocumentType& getDocumentType() const { return *_docType; }
    const Field::Set& getFields() const { return _set; }
    uint64_t hash() const { return _hash; }
private:
    Field::Set          _set;
    uint64_t            _hash;
    const DocumentType* _docType;
};

}
