// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/persistence/spi/read_consistency.h>
#include <vespa/fastos/fastos.h>
#include "i_document_retriever.h"

namespace proton {

void DocumentRetrieverBaseForTest::visitDocuments(const LidVector &lids, search::IDocumentVisitor &visitor, ReadConsistency readConsistency) const {
    (void) readConsistency;
    for (uint32_t lid : lids) {
        visitor.visit(lid, getDocument(lid));
    }
}

}
