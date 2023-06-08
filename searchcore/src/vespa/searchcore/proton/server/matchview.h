// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "matchers.h"
#include <vespa/searchcore/proton/common/docid_limit.h>
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store_context.h>
#include <vespa/searchcore/proton/matching/match_context.h>
#include <vespa/searchcore/proton/summaryengine/isearchhandler.h>

namespace searchcorespi { class IndexSearchable; }

namespace proton::matching {
    class MatchContext;
    class Matcher;
    class SessionManager;
}

namespace proton {
struct IAttributeManager;

class MatchView {
    using SessionManager = matching::SessionManager;
    std::shared_ptr<Matchers>                        _matchers;
    std::shared_ptr<searchcorespi::IndexSearchable>  _indexSearchable;
    std::shared_ptr<IAttributeManager>               _attrMgr;
    SessionManager                                 & _sessionMgr;
    std::shared_ptr<IDocumentMetaStoreContext>       _metaStore;
    DocIdLimit                                      &_docIdLimit;

    size_t getNumDocs() const {
        return _metaStore->get().getNumActiveLids();
    }

public:
    using SP = std::shared_ptr<MatchView>;
    MatchView(const MatchView &) = delete;
    MatchView & operator = (const MatchView &) = delete;

    MatchView(std::shared_ptr<Matchers> matchers,
              std::shared_ptr<searchcorespi::IndexSearchable> indexSearchable,
              std::shared_ptr<IAttributeManager> attrMgr,
              SessionManager & sessionMgr,
              std::shared_ptr<IDocumentMetaStoreContext> metaStore,
              DocIdLimit &docIdLimit);
    ~MatchView();

    const std::shared_ptr<Matchers>& getMatchers() const noexcept { return _matchers; }
    const std::shared_ptr<searchcorespi::IndexSearchable>& getIndexSearchable() const noexcept { return _indexSearchable; }
    const std::shared_ptr<IAttributeManager>& getAttributeManager() const noexcept { return _attrMgr; }
    SessionManager & getSessionManager() const noexcept { return _sessionMgr; }
    const std::shared_ptr<IDocumentMetaStoreContext>& getDocumentMetaStore() const noexcept { return _metaStore; }
    DocIdLimit & getDocIdLimit() const noexcept { return _docIdLimit; }

    // Throws on error.
    std::shared_ptr<matching::Matcher> getMatcher(const vespalib::string & rankProfile) const;

    matching::MatchingStats
    getMatcherStats(const vespalib::string &rankProfile) const {
        return _matchers->getStats(rankProfile);
    }

    matching::MatchContext createContext() const;

    std::unique_ptr<search::engine::SearchReply>
    match(std::shared_ptr<const ISearchHandler> searchHandler,
          const search::engine::SearchRequest &req,
          vespalib::ThreadBundle &threadBundle) const;
};

} // namespace proton
