// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.common.document_type_inspector");

#include "document_type_inspector.h"

namespace proton {

DocumentTypeInspector::DocumentTypeInspector(const document::DocumentType &docType)
    : _docType(docType)
{
}

bool
DocumentTypeInspector::hasField(const vespalib::string &name) const
{
    return _docType.hasField(name);
}

} // namespace proton
