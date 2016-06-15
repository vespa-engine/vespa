// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_document_type_inspector.h"
#include <vespa/document/datatype/documenttype.h>

namespace proton {

/**
 * Inspector of a concrete document type.
 */
class DocumentTypeInspector : public IDocumentTypeInspector
{
private:
    const document::DocumentType &_docType;

public:
    DocumentTypeInspector(const document::DocumentType &docType);

    virtual bool hasField(const vespalib::string &name) const;
};

} // namespace proton

