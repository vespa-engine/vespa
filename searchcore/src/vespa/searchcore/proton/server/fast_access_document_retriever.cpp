// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fast_access_document_retriever.h"

namespace proton {

FastAccessDocumentRetriever::FastAccessDocumentRetriever(FastAccessFeedView::SP feedView, IAttributeManager::SP attrMgr)
    : DocumentRetriever(feedView->getPersistentParams()._docTypeName,
                        *feedView->getDocumentTypeRepo(),
                        *feedView->getSchema(),
                        *feedView->getDocumentMetaStore(),
                        *attrMgr,
                        feedView->getDocumentStore()),
      _feedView(std::move(feedView)),
      _attrMgr(std::move(attrMgr))
{ }

FastAccessDocumentRetriever::~FastAccessDocumentRetriever() = default;

}
