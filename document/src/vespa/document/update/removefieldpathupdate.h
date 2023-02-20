// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fieldpathupdate.h"

namespace document {

class RemoveFieldPathUpdate final : public FieldPathUpdate
{
public:
    /** For deserialization */
    RemoveFieldPathUpdate() noexcept;
    RemoveFieldPathUpdate(RemoveFieldPathUpdate &&) noexcept = default;
    RemoveFieldPathUpdate & operator =(RemoveFieldPathUpdate &&) noexcept = default;
    RemoveFieldPathUpdate(const RemoveFieldPathUpdate &) = delete;
    RemoveFieldPathUpdate & operator =(const RemoveFieldPathUpdate &) = delete;
    RemoveFieldPathUpdate(stringref fieldPath, stringref whereClause = stringref());
    ~RemoveFieldPathUpdate() override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    ACCEPT_UPDATE_VISITOR;
private:
    uint8_t getSerializedType() const override { return RemoveMagic; }
    void deserialize(const DocumentTypeRepo& repo, const DataType& type, nbostream & buffer) override;

    std::unique_ptr<fieldvalue::IteratorHandler> getIteratorHandler(Document &, const DocumentTypeRepo &) const override;
};

}

