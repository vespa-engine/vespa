// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    FieldSet* clone() const override { return new AllFields(); }
};

class NoFields final : public FieldSet
{
public:
    static constexpr const char * NAME = "[none]";
    bool contains(const FieldSet& f) const override { return f.getType() == Type::NONE; }
    Type getType() const override { return Type::NONE; }
    FieldSet* clone() const override { return new NoFields(); }
};

class DocIdOnly final : public FieldSet
{
public:
    static constexpr const char * NAME = "[id]";
    bool contains(const FieldSet& fields) const override {
        return fields.getType() == Type::DOCID || fields.getType() == Type::NONE;
    }
    Type getType() const override { return Type::DOCID; }
    FieldSet* clone() const override { return new DocIdOnly(); }
};

class FieldCollection : public FieldSet
{
public:
    typedef std::unique_ptr<FieldCollection> UP;

    FieldCollection(const DocumentType& docType, Field::Set set);
    FieldCollection(const FieldCollection &);
    FieldCollection(FieldCollection&&) = default;
    ~FieldCollection() override;

    bool contains(const FieldSet& fields) const override;
    Type getType() const override { return Type::SET; }

    /**
     * @return Returns the document type the collection is associated with.
     */
    const DocumentType& getDocumentType() const {
        return _docType;
    }

    /**
     * Returns all the fields contained in this collection.
     */
    const Field::Set& getFields() const { return _set; }

    FieldSet* clone() const override { return new FieldCollection(*this); }

    uint64_t hash() const { return _hash; }
private:
    Field::Set _set;
    uint64_t   _hash;
    const DocumentType& _docType;
};

}
