// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "matchview.h"
#include <vespa/searchcore/proton/docsummary/isummarymanager.h>
#include <vespa/searchcore/proton/summaryengine/isearchhandler.h>

namespace proton {

class SearchView : public ISearchHandler, public std::enable_shared_from_this<SearchView>
{
public:
    using SessionManager = matching::SessionManager;
    using IndexSearchable = searchcorespi::IndexSearchable;
    using InternalDocsumReply = std::pair<std::unique_ptr<DocsumReply>, bool>;
    using SP = std::shared_ptr<SearchView>;

    static std::shared_ptr<SearchView> create(std::shared_ptr<ISummaryManager::ISummarySetup> summarySetup, std::shared_ptr<MatchView> matchView);
    SearchView(const SearchView &) = delete;
    SearchView(SearchView &&) = delete;
    SearchView &operator=(const SearchView &) = delete;
    SearchView &operator=(SearchView &&) = delete;
    ~SearchView() override;

    const std::shared_ptr<ISummaryManager::ISummarySetup>& getSummarySetup() const noexcept { return _summarySetup; }
    const std::shared_ptr<MatchView>& getMatchView() const noexcept { return _matchView; }
    const std::shared_ptr<Matchers>& getMatchers() const noexcept { return _matchView->getMatchers(); }
    const std::shared_ptr<IndexSearchable>& getIndexSearchable() const noexcept { return _matchView->getIndexSearchable(); }
    const std::shared_ptr<IAttributeManager>& getAttributeManager() const noexcept { return _matchView->getAttributeManager(); }
    SessionManager & getSessionManager() const noexcept { return _matchView->getSessionManager(); }
    const std::shared_ptr<IDocumentMetaStoreContext>& getDocumentMetaStore() const noexcept { return _matchView->getDocumentMetaStore(); }
    DocIdLimit &getDocIdLimit() const noexcept { return _matchView->getDocIdLimit(); }
    matching::MatchingStats getMatcherStats(const vespalib::string &rankProfile) const { return _matchView->getMatcherStats(rankProfile); }

    std::unique_ptr<DocsumReply> getDocsums(const DocsumRequest & req) override;
    std::unique_ptr<SearchReply> match(const SearchRequest &req, vespalib::ThreadBundle &threadBundle) const override;
private:
    SearchView(std::shared_ptr<ISummaryManager::ISummarySetup> summarySetup, std::shared_ptr<MatchView> matchView);
    InternalDocsumReply getDocsumsInternal(const DocsumRequest & req);
    std::shared_ptr<ISummaryManager::ISummarySetup> _summarySetup;
    std::shared_ptr<MatchView>                      _matchView;
};

} // namespace proton

