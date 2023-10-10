// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_meta_store_read_guards.h"
#include "documentsubdbcollection.h"
#include "idocumentsubdb.h"

namespace proton {

DocumentMetaStoreReadGuards::DocumentMetaStoreReadGuards(const DocumentSubDBCollection &subDBs)
    : readydms(subDBs.getReadySubDB()->getDocumentMetaStoreContext().getReadGuard()),
      notreadydms(subDBs.getNotReadySubDB()->getDocumentMetaStoreContext().getReadGuard()),
      remdms(subDBs.getRemSubDB()->getDocumentMetaStoreContext().getReadGuard())
{ }

DocumentMetaStoreReadGuards::~DocumentMetaStoreReadGuards() = default;

}
