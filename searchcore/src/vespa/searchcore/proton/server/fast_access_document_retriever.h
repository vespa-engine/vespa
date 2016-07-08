// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentretriever.h"
#include "fast_access_feed_view.h"
#include <vespa/searchcore/proton/attribute/i_attribute_manager.h>
#include <vespa/searchcore/proton/docsummary/isummarymanager.h>

namespace proton {

/**
 * The document retriever used by the fast-access sub database.
 *
 * Handles retrieving of documents by combining from the underlying attribute manager
 * and document store.
 */
class FastAccessDocumentRetriever : public DocumentRetriever
{
private:
    FastAccessFeedView::SP   _feedView;
    IAttributeManager::SP    _attrMgr;

public:
    FastAccessDocumentRetriever(const FastAccessFeedView::SP &feedView, const IAttributeManager::SP &attrMgr)
        : DocumentRetriever(feedView->getPersistentParams()._docTypeName,
                            *feedView->getDocumentTypeRepo(),
                            *feedView->getSchema(),
                            *feedView->getDocumentMetaStore(),
                            *attrMgr,
                            feedView->getDocumentStore()),
          _feedView(feedView),
          _attrMgr(attrMgr)
    { }
    uint32_t getDocIdLimit() const override { return _feedView->getDocIdLimit().get(); }
};

} // namespace proton

