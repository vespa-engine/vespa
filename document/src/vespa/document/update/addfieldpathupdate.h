// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/update/fieldpathupdate.h>

namespace document {

class AddFieldPathUpdate : public FieldPathUpdate
{
public:
    /** For deserialization */
    AddFieldPathUpdate();

    AddFieldPathUpdate(const DocumentTypeRepo& repo,
                       const DataType& type,
                       stringref fieldPath,
                       stringref whereClause,
                       const ArrayFieldValue& values);
    ~AddFieldPathUpdate();

    FieldPathUpdate* clone() const override;

    bool operator==(const FieldPathUpdate& other) const;

    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;

    const ArrayFieldValue & getValues() const { return *_values; }

    DECLARE_IDENTIFIABLE(AddFieldPathUpdate);
    ACCEPT_UPDATE_VISITOR;

private:
    uint8_t getSerializedType() const override { return AddMagic; }
    virtual void deserialize(const DocumentTypeRepo& repo,
                             const DataType& type,
                             ByteBuffer& buffer, uint16_t version);

    class AddIteratorHandler : public FieldValue::IteratorHandler
    {
    public:
        AddIteratorHandler(const ArrayFieldValue& values) : _values(values) { }

        ModificationStatus doModify(FieldValue& fv) override;

        bool createMissingPath() const override { return true; }

        bool onComplex(const Content&) override { return false; }
    private:
        const ArrayFieldValue& _values;
    };

    std::unique_ptr<FieldValue::IteratorHandler> getIteratorHandler(Document&) const {
        return std::unique_ptr<FieldValue::IteratorHandler>(
                new AddIteratorHandler(*_values));
    }

    vespalib::CloneablePtr<ArrayFieldValue> _values;
};


} // ns document

