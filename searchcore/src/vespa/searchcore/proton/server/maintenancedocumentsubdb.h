// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "ifeedview.h"
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store.h>
#include <vespa/searchcore/proton/persistenceengine/i_document_retriever.h>

namespace proton {

class ILidCommitState;

/**
 * The view of a document sub db as seen from the maintenance controller
 * and various maintenance jobs.
 */
class MaintenanceDocumentSubDB
{
private:
    vespalib::string       _name;
    uint32_t               _sub_db_id;
    IDocumentMetaStore::SP _meta_store;
    IDocumentRetriever::SP _retriever;
    IFeedView::SP          _feed_view;
    const ILidCommitState *_pendingLidsForCommit;

public:
    MaintenanceDocumentSubDB();
    ~MaintenanceDocumentSubDB();

    MaintenanceDocumentSubDB(const vespalib::string& name,
                             uint32_t sub_db_id,
                             IDocumentMetaStore::SP meta_store,
                             IDocumentRetriever::SP retriever,
                             IFeedView::SP feed_view,
                             const ILidCommitState *);

    const vespalib::string& name() const { return _name; }
    uint32_t sub_db_id() const { return _sub_db_id; }
    const IDocumentMetaStore::SP& meta_store() const { return _meta_store; }
    const IDocumentRetriever::SP& retriever() const { return _retriever; }
    const IFeedView::SP& feed_view() const { return _feed_view; }

    bool valid() const { return _meta_store.get() != nullptr; }
    bool lidNeedsCommit(search::DocumentIdT lid) const;

    void clear();
};

}
