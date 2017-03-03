// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "documentsubdbcollection.h"
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store_context.h>

namespace proton {

/**
 * Class that takes and owns read guards of the document meta stores of the 3 sub databases.
 * Provides stats regarding the number of documents in the sub databases.
 */
struct DocumentMetaStoreReadGuards
{
    IDocumentMetaStoreContext::IReadGuard::UP readydms;
    IDocumentMetaStoreContext::IReadGuard::UP notreadydms;
    IDocumentMetaStoreContext::IReadGuard::UP remdms;
    DocumentMetaStoreReadGuards(DocumentSubDBCollection &subDBs)
        : readydms(subDBs.getReadySubDB()->getDocumentMetaStoreContext().getReadGuard()),
          notreadydms(subDBs.getNotReadySubDB()->getDocumentMetaStoreContext().getReadGuard()),
          remdms(subDBs.getRemSubDB()->getDocumentMetaStoreContext().getReadGuard())
    {
    }
    ~DocumentMetaStoreReadGuards();
    uint32_t numActiveDocs() const {
        return readydms->get().getNumActiveLids();
    }
    uint32_t numIndexedDocs() const {
        return readydms->get().getNumUsedLids();
    }
    uint32_t numStoredDocs() const {
        return numIndexedDocs() + notreadydms->get().getNumUsedLids();
    }
    uint32_t numRemovedDocs() const {
        return remdms->get().getNumUsedLids();
    }
};

} // namespace proton
