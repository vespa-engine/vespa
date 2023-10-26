// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    const document::DocumentType &_oldDocType;
    const document::DocumentType &_newDocType;

public:
    DocumentTypeInspector(const document::DocumentType &oldDocType,
                          const document::DocumentType &newDocType);

    virtual bool hasUnchangedField(const vespalib::string &name) const override;
};

} // namespace proton

