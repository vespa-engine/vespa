// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/base/field.h>

namespace document {

class AllFields : public FieldSet
{
public:
    virtual bool contains(const FieldSet&) const {
        return true;
    }

    /**
     * @return Returns the type of field set this is.
     */
    virtual Type getType() const {
        return ALL;
    }

    virtual FieldSet* clone() const {
        return new AllFields();
    }
};

class NoFields : public FieldSet
{
public:
    virtual bool contains(const FieldSet& f) const {
        return f.getType() == NONE;
    }

    /**
     * @return Returns the type of field set this is.
     */
    virtual Type getType() const {
        return NONE;
    }

    virtual FieldSet* clone() const {
        return new NoFields();
    }
};

class DocIdOnly : public FieldSet
{
public:
    virtual bool contains(const FieldSet& fields) const {
        return fields.getType() == DOCID || fields.getType() == NONE;
    }

    /**
     * @return Returns the type of field set this is.
     */
    virtual Type getType() const {
        return DOCID;
    }

    virtual FieldSet* clone() const {
        return new DocIdOnly();
    }

};

class HeaderFields : public FieldSet
{
public:
    virtual bool contains(const FieldSet& fields) const;

    /**
     * @return Returns the type of field set this is.
     */
    virtual Type getType() const {
        return HEADER;
    }

    virtual FieldSet* clone() const {
        return new HeaderFields();
    }

};

class BodyFields : public FieldSet
{
public:
    virtual bool contains(const FieldSet& fields) const;

    /**
     * @return Returns the type of field set this is.
     */
    virtual Type getType() const {
        return BODY;
    }

    virtual FieldSet* clone() const {
        return new BodyFields();
    }
};

class FieldCollection : public FieldSet
{
public:
    typedef std::unique_ptr<FieldCollection> UP;

    FieldCollection(const DocumentType& docType)
        : _docType(docType) {};

    FieldCollection(const DocumentType& docType, const Field::Set& set);

    virtual bool contains(const FieldSet& fields) const;

    /**
     * @return Returns the type of field set this is.
     */
    virtual Type getType() const {
        return SET;
    }

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

    virtual FieldSet* clone() const {
        return new FieldCollection(*this);
    }

private:
    Field::Set _set;
    const DocumentType& _docType;
};


}

