// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_type_inspector.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/base/fieldpath.h>

using document::FieldPath;
using document::FieldPathEntry;

namespace proton {

DocumentTypeInspector::DocumentTypeInspector(const document::DocumentType &oldDocType,
                                             const document::DocumentType &newDocType)
    : _oldDocType(oldDocType),
      _newDocType(newDocType)
{
}

bool
DocumentTypeInspector::hasUnchangedField(const vespalib::string &name) const
{
    FieldPath oldPath;
    FieldPath newPath;
    try {
        _oldDocType.buildFieldPath(oldPath, name);
        _newDocType.buildFieldPath(newPath, name);
    } catch (document::FieldNotFoundException &e) {
        return false;
    } catch (vespalib::IllegalArgumentException &e) {
        return false;
    }
    if (oldPath.size() != newPath.size()) {
        return false;
    }
    for (uint32_t i = 0; i < oldPath.size(); ++i) {
        const auto &oldEntry = oldPath[i];
        const auto &newEntry = newPath[i];
        if (oldEntry.getType() != newEntry.getType() ||
            oldEntry.getDataType() != newEntry.getDataType()) {
            return false;
        }
    }
    return true;
}

} // namespace proton
