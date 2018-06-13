// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "removefieldpathupdate.h"
#include <vespa/document/fieldvalue/iteratorhandler.h>
#include <ostream>

namespace document {

using namespace fieldvalue;

IMPLEMENT_IDENTIFIABLE(RemoveFieldPathUpdate, FieldPathUpdate);

RemoveFieldPathUpdate::RemoveFieldPathUpdate()
    : FieldPathUpdate()
{
}

RemoveFieldPathUpdate::RemoveFieldPathUpdate(stringref fieldPath, stringref whereClause)
    : FieldPathUpdate(fieldPath, whereClause)
{
}

bool
RemoveFieldPathUpdate::operator==(const FieldPathUpdate& other) const
{
    if (other.getClass().id() != RemoveFieldPathUpdate::classId) return false;
    return FieldPathUpdate::operator==(other);
}

void
RemoveFieldPathUpdate::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    out << "RemoveFieldPathUpdate(\n";
    FieldPathUpdate::print(out, verbose, indent + "  ");
    out << "\n" << indent << ")";
}

void
RemoveFieldPathUpdate::deserialize(const DocumentTypeRepo& repo, const DataType& type, ByteBuffer& buffer)
{
    FieldPathUpdate::deserialize(repo, type, buffer);
}

namespace {

class RemoveIteratorHandler : public IteratorHandler {
public:
    RemoveIteratorHandler() {}

    ModificationStatus doModify(FieldValue &) override {
        return ModificationStatus::REMOVED;
    }
};

}

std::unique_ptr<IteratorHandler>
RemoveFieldPathUpdate::getIteratorHandler(Document&, const DocumentTypeRepo &) const {
    return std::make_unique<RemoveIteratorHandler>();
}

} // ns document
