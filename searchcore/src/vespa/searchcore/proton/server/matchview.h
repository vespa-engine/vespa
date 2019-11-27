// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "matchers.h"
#include <vespa/searchcore/proton/attribute/attributemanager.h>
#include <vespa/searchcore/proton/common/docid_limit.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastorecontext.h>
#include <vespa/searchcore/proton/matching/match_context.h>
#include <vespa/searchcore/proton/summaryengine/isearchhandler.h>
#include <vespa/searchcorespi/index/indexsearchable.h>
#include <vespa/searchlib/attribute/attributevector.h>

namespace proton {

namespace matching {
    class SessionManager;
    class Matcher;
}

class MatchView {
    using SessionManagerSP = std::shared_ptr<matching::SessionManager>;
    Matchers::SP                         _matchers;
    searchcorespi::IndexSearchable::SP   _indexSearchable;
    IAttributeManager::SP                _attrMgr;
    SessionManagerSP                     _sessionMgr;
    IDocumentMetaStoreContext::SP        _metaStore;
    DocIdLimit                          &_docIdLimit;

    size_t getNumDocs() const {
        return _metaStore->get().getNumActiveLids();
    }

public:
    typedef std::shared_ptr<MatchView> SP;
    MatchView(const MatchView &) = delete;
    MatchView & operator = (const MatchView &) = delete;

    MatchView(const Matchers::SP &matchers,
              const searchcorespi::IndexSearchable::SP &indexSearchable,
              const IAttributeManager::SP &attrMgr,
              const SessionManagerSP &sessionMgr,
              const IDocumentMetaStoreContext::SP &metaStore,
              DocIdLimit &docIdLimit);
    ~MatchView();

    const Matchers::SP & getMatchers() const { return _matchers; }
    const searchcorespi::IndexSearchable::SP & getIndexSearchable() const { return _indexSearchable; }
    const IAttributeManager::SP & getAttributeManager() const { return _attrMgr; }
    const SessionManagerSP & getSessionManager() const { return _sessionMgr; }
    const IDocumentMetaStoreContext::SP & getDocumentMetaStore() const { return _metaStore; }
    DocIdLimit & getDocIdLimit() const { return _docIdLimit; }

    // Throws on error.
    std::shared_ptr<matching::Matcher> getMatcher(const vespalib::string & rankProfile) const;

    matching::MatchingStats
    getMatcherStats(const vespalib::string &rankProfile) const {
        return _matchers->getStats(rankProfile);
    }

    matching::MatchContext::UP createContext() const;

    std::unique_ptr<search::engine::SearchReply>
    match(const std::shared_ptr<ISearchHandler> &searchHandler,
          const search::engine::SearchRequest &req,
          vespalib::ThreadBundle &threadBundle) const;
};

} // namespace proton
