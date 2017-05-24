// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "idocumentstore.h"

namespace search {

IDocumentStore::IDocumentStore()
{
}

IDocumentStore::~IDocumentStore()
{
}

void IDocumentStore::visit(const LidVector & lids, const document::DocumentTypeRepo &repo, IDocumentVisitor & visitor) const {
    for (uint32_t lid : lids) {
        visitor.visit(lid, read(lid, repo));
    }
}

} // namespace search

