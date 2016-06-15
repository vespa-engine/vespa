// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "matchview.h"
#include <vespa/searchcore/proton/docsummary/isummarymanager.h>
#include <vespa/searchcore/proton/summaryengine/isearchhandler.h>

namespace proton {

class SearchView : public boost::noncopyable,
                   public ISearchHandler
{
private:
    ISummaryManager::ISummarySetup::SP _summarySetup;
    MatchView::SP                      _matchView;

public:
    typedef std::shared_ptr<SearchView> SP;

    SearchView(const ISummaryManager::ISummarySetup::SP &summarySetup,
               const MatchView::SP &matchView);

    const ISummaryManager::ISummarySetup::SP & getSummarySetup() const
    { return _summarySetup; }

    const MatchView::SP &getMatchView() const { return _matchView; }

    const Matchers::SP &getMatchers() const
    { return _matchView->getMatchers(); }

    const searchcorespi::IndexSearchable::SP &
    getIndexSearchable() const { return _matchView->getIndexSearchable(); }

    const IAttributeManager::SP &getAttributeManager() const
    { return _matchView->getAttributeManager(); }

    const matching::SessionManager::SP &
    getSessionManager() const { return _matchView->getSessionManager(); }

    const IDocumentMetaStoreContext::SP &
    getDocumentMetaStore() const { return _matchView->getDocumentMetaStore(); }

    DocIdLimit &getDocIdLimit() const { return _matchView->getDocIdLimit(); }

    matching::MatchingStats
    getMatcherStats(const vespalib::string &rankProfile) const
    { return _matchView->getMatcherStats(rankProfile); }


    /**
     * Implements ISearchHandler
     */
    virtual search::engine::DocsumReply::UP
    getDocsums(const search::engine::DocsumRequest & req);

    virtual search::engine::SearchReply::UP match(
            const ISearchHandler::SP &self,
            const search::engine::SearchRequest &req,
            vespalib::ThreadBundle &threadBundle) const;
};

} // namespace proton

