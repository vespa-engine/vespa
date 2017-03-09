// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "matchview.h"
#include <vespa/searchcore/proton/docsummary/isummarymanager.h>
#include <vespa/searchcore/proton/summaryengine/isearchhandler.h>

namespace proton {

class SearchView : public ISearchHandler
{
public:
    using IndexSearchable = searchcorespi::IndexSearchable;
    using InternalDocsumReply = std::pair<std::unique_ptr<DocsumReply>, bool>;
    typedef std::shared_ptr<SearchView> SP;

    SearchView(const ISummaryManager::ISummarySetup::SP &summarySetup, const MatchView::SP &matchView);
    SearchView(SearchView &&) = default;
    SearchView &operator=(SearchView &&) = default;
    ~SearchView();

    const ISummaryManager::ISummarySetup::SP & getSummarySetup() const { return _summarySetup; }
    const MatchView::SP & getMatchView() const { return _matchView; }
    const Matchers::SP  & getMatchers()  const { return _matchView->getMatchers(); }
    const IndexSearchable::SP   & getIndexSearchable()  const { return _matchView->getIndexSearchable(); }
    const IAttributeManager::SP & getAttributeManager() const { return _matchView->getAttributeManager(); }
    const matching::SessionManager::SP  & getSessionManager()    const { return _matchView->getSessionManager(); }
    const IDocumentMetaStoreContext::SP & getDocumentMetaStore() const { return _matchView->getDocumentMetaStore(); }
    DocIdLimit &getDocIdLimit() const { return _matchView->getDocIdLimit(); }
    matching::MatchingStats getMatcherStats(const vespalib::string &rankProfile) const { return _matchView->getMatcherStats(rankProfile); }

    std::unique_ptr<DocsumReply> getDocsums(const DocsumRequest & req) override;
    std::unique_ptr<SearchReply> match(const ISearchHandler::SP &self, const SearchRequest &req, vespalib::ThreadBundle &threadBundle) const override;
private:
    InternalDocsumReply getDocsumsInternal(const DocsumRequest & req);
    ISummaryManager::ISummarySetup::SP _summarySetup;
    MatchView::SP                      _matchView;
};

} // namespace proton

