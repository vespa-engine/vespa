// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matchview.h"
#include "searchcontext.h"
#include <vespa/searchcore/proton/matching/matcher.h>
#include <vespa/searchlib/engine/searchrequest.h>
#include <vespa/searchlib/engine/searchreply.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.matchview");

using proton::matching::MatchContext;
using proton::matching::SearchSession;
using search::AttributeGuard;
using search::AttributeVector;
using search::attribute::IAttributeContext;
using search::engine::SearchReply;
using search::engine::SearchRequest;
using search::queryeval::Blueprint;
using search::queryeval::FieldSpec;
using search::queryeval::FieldSpecList;
using search::queryeval::Searchable;
using searchcorespi::IndexSearchable;
using vespalib::make_string;
using vespalib::ThreadBundle;

namespace proton {

using matching::ISearchContext;
using matching::Matcher;
using matching::SessionManager;

MatchView::MatchView(const Matchers::SP &matchers,
                     const IndexSearchable::SP &indexSearchable,
                     const IAttributeManager::SP &attrMgr,
                     const SessionManagerSP &sessionMgr,
                     const IDocumentMetaStoreContext::SP &metaStore,
                     DocIdLimit &docIdLimit)
    : _matchers(matchers),
      _indexSearchable(indexSearchable),
      _attrMgr(attrMgr),
      _sessionMgr(sessionMgr),
      _metaStore(metaStore),
      _docIdLimit(docIdLimit)
{ }

MatchView::~MatchView() = default;

Matcher::SP
MatchView::getMatcher(const vespalib::string & rankProfile) const
{
    Matcher::SP retval = _matchers->lookup(rankProfile);
    if ( ! retval) {
        throw std::runtime_error(make_string("Failed locating Matcher for rank profile '%s'", rankProfile.c_str()));
    }
    LOG(debug, "Rankprofile = %s has termwise_limit=%f", rankProfile.c_str(), retval->get_termwise_limit());
    return retval;
}


MatchContext::UP
MatchView::createContext() const {
    IAttributeContext::UP attrCtx = _attrMgr->createContext();
    auto searchCtx = std::make_unique<SearchContext>(_indexSearchable, _docIdLimit.get());
    return std::make_unique<MatchContext>(std::move(attrCtx), std::move(searchCtx));
}


std::unique_ptr<SearchReply>
MatchView::match(const ISearchHandler::SP &searchHandler, const SearchRequest &req,
                 vespalib::ThreadBundle &threadBundle) const
{
    Matcher::SP matcher = getMatcher(req.ranking);
    SearchSession::OwnershipBundle owned_objects;
    owned_objects.search_handler = searchHandler;
    owned_objects.context = createContext();
    owned_objects.readGuard = _metaStore->getReadGuard();;
    MatchContext *ctx = owned_objects.context.get();
    const search::IDocumentMetaStore & dms = owned_objects.readGuard->get();
    return matcher->match(req, threadBundle, ctx->getSearchContext(), ctx->getAttributeContext(),
                          *_sessionMgr, dms, std::move(owned_objects));
}


} // namespace proton
