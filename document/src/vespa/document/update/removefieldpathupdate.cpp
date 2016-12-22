// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "removefieldpathupdate.h"
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/select/parser.h>

namespace document {

IMPLEMENT_IDENTIFIABLE(RemoveFieldPathUpdate, FieldPathUpdate);

RemoveFieldPathUpdate::RemoveFieldPathUpdate()
    : FieldPathUpdate()
{
}

RemoveFieldPathUpdate::RemoveFieldPathUpdate(
        const DocumentTypeRepo& repo,
        const DataType& type,
        stringref fieldPath,
        stringref whereClause)
    : FieldPathUpdate(repo, type, fieldPath, whereClause)
{
}

bool
RemoveFieldPathUpdate::operator==(const FieldPathUpdate& other) const
{
    if (other.getClass().id() != RemoveFieldPathUpdate::classId) return false;
    return FieldPathUpdate::operator==(other);
}

void
RemoveFieldPathUpdate::print(std::ostream& out, bool verbose,
                          const std::string& indent) const
{
    out << "RemoveFieldPathUpdate(\n";
    FieldPathUpdate::print(out, verbose, indent + "  ");
    out << "\n" << indent << ")";
}

void
RemoveFieldPathUpdate::deserialize(
        const DocumentTypeRepo& repo, const DataType& type,
        ByteBuffer& buffer, uint16_t version)
{
    FieldPathUpdate::deserialize(repo, type, buffer, version);
}

} // ns document
