// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fast_access_document_retriever.h"
#include "searchable_feed_view.h"
#include "searchview.h"

namespace proton {

class SearchableDocumentRetriever : public FastAccessDocumentRetriever {
public:
    // Assumes the FeedView also ensures that the MatchView stays alive.
    SearchableDocumentRetriever(const SearchableFeedView::SP &fw, const SearchView::SP &sv) :
        FastAccessDocumentRetriever(fw, sv->getAttributeManager())
    { }
};

} // namespace proton

