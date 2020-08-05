// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "i_document_retriever.h"
#include <vespa/persistence/spi/read_consistency.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldset/fieldsets.h>

namespace proton {

document::Document::UP
IDocumentRetriever::getDocument(search::DocumentIdT lid, const document::DocumentId & docId) const {
    return getDocument(lid, docId, document::AllFields());
}

void
DocumentRetrieverBaseForTest::visitDocuments(const LidVector &lids, search::IDocumentVisitor &visitor, ReadConsistency readConsistency) const {
    (void) readConsistency;
    for (uint32_t lid : lids) {
        visitor.visit(lid, getDocumentByLidOnly(lid));
    }
}

document::Document::UP
DocumentRetrieverBaseForTest::getDocument(search::DocumentIdT lid, const document::DocumentId &, const document::FieldSet & fieldSet) const {
    auto doc = getDocumentByLidOnly(lid);
    if (doc) {
        document::FieldSet::stripFields(*doc, fieldSet);
    }
    return doc;
}

}
