// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store.h>
#include <vespa/searchcore/proton/persistenceengine/i_document_retriever.h>

namespace proton {

/**
 * The view of a document sub db as seen from the maintenance controller
 * and various maintenance jobs.
 */
class MaintenanceDocumentSubDB
{
public:
    IDocumentMetaStore::SP   _metaStore;
    IDocumentRetriever::SP   _retriever;
    uint32_t                 _subDbId;

    MaintenanceDocumentSubDB();
    ~MaintenanceDocumentSubDB();

    MaintenanceDocumentSubDB(const IDocumentMetaStore::SP & metaStore,
                             const IDocumentRetriever::SP & retriever,
                             uint32_t subDbId);

    bool valid() const { return bool(_metaStore); }

    void clear();
};

}
