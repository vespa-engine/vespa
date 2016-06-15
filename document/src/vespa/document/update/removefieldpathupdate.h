// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/update/fieldpathupdate.h>

namespace document {

class RemoveFieldPathUpdate : public FieldPathUpdate
{
public:
    /** For deserialization */
    RemoveFieldPathUpdate();

    RemoveFieldPathUpdate(const DocumentTypeRepo& repo,
                          const DataType& type,
                          stringref fieldPath,
                          stringref whereClause = stringref());

    FieldPathUpdate* clone() const { return new RemoveFieldPathUpdate(*this); }

    bool operator==(const FieldPathUpdate& other) const;

    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;

    DECLARE_IDENTIFIABLE(RemoveFieldPathUpdate);
    ACCEPT_UPDATE_VISITOR;

private:
    uint8_t getSerializedType() const override { return RemoveMagic; }
    virtual void deserialize(const DocumentTypeRepo& repo,
                             const DataType& type,
                             ByteBuffer& buffer, uint16_t version);

    class RemoveIteratorHandler : public FieldValue::IteratorHandler
    {
    public:
        RemoveIteratorHandler() {}

        ModificationStatus doModify(FieldValue&) {
            return REMOVED;
        }
    };

    std::unique_ptr<FieldValue::IteratorHandler> getIteratorHandler(Document&) const {
        return std::unique_ptr<FieldValue::IteratorHandler>(
                new RemoveIteratorHandler());
    }
};


} // ns document

