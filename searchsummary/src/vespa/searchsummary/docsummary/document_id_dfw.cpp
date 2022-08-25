// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_id_dfw.h"
#include "i_docsum_store_document.h"

namespace search::docsummary {

DocumentIdDFW::DocumentIdDFW()
{
}

DocumentIdDFW::~DocumentIdDFW() = default;

bool
DocumentIdDFW::IsGenerated() const
{
    return false;
}

void
DocumentIdDFW::insertField(uint32_t, const IDocsumStoreDocument* doc, GetDocsumsState *, ResType,
                           vespalib::slime::Inserter &target)
{
    if (doc != nullptr) {
        doc->insert_document_id(target);
    }
}

}
