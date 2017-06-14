// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/base/field.h>

namespace document {

class AllFields final : public FieldSet
{
public:
    bool contains(const FieldSet&) const override { return true; }
    Type getType() const override { return ALL; }
    FieldSet* clone() const override { return new AllFields(); }
};

class NoFields final : public FieldSet
{
public:
    bool contains(const FieldSet& f) const override { return f.getType() == NONE; }
    Type getType() const override { return NONE; }
    FieldSet* clone() const override { return new NoFields(); }
};

class DocIdOnly final : public FieldSet
{
public:
    bool contains(const FieldSet& fields) const override {
        return fields.getType() == DOCID || fields.getType() == NONE;
    }
    Type getType() const override { return DOCID; }
    FieldSet* clone() const override { return new DocIdOnly(); }
};

class HeaderFields final : public FieldSet
{
public:
    bool contains(const FieldSet& fields) const override;
    Type getType() const override { return HEADER; }
    FieldSet* clone() const override { return new HeaderFields(); }
};

class BodyFields final : public FieldSet
{
public:
    bool contains(const FieldSet& fields) const override;
    Type getType() const override { return BODY; }
    FieldSet* clone() const override { return new BodyFields(); }
};

class FieldCollection : public FieldSet
{
public:
    typedef std::unique_ptr<FieldCollection> UP;

    FieldCollection(const DocumentType& docType) : _docType(docType) {};
    FieldCollection(const DocumentType& docType, const Field::Set& set);

    bool contains(const FieldSet& fields) const override;
    Type getType() const override { return SET; }

    /**
     * @return Returns the document type the collection is associated with.
     */
    const DocumentType& getDocumentType() const {
        return _docType;
    }

    /**
     * Inserts the given field into the collection.
     */
    void insert(const Field& f);

    /**
     * Inserts all the field in the given collection into this collection.
     */
    void insert(const Field::Set& f);

    /**
     * Returns all the fields contained in this collection.
     */
    const Field::Set& getFields() const { return _set; }

    FieldSet* clone() const override { return new FieldCollection(*this); }

private:
    Field::Set _set;
    const DocumentType& _docType;
};

}
