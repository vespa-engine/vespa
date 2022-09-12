// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matchview.h"
#include "searchcontext.h"
#include <vespa/searchcore/proton/matching/matcher.h>
#include <vespa/searchlib/engine/searchrequest.h>
#include <vespa/searchlib/engine/searchreply.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exceptions.h>

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
using vespalib::ThreadBundle;
using vespalib::IllegalArgumentException;
using namespace vespalib::make_string_short;

namespace proton {

using matching::ISearchContext;
using matching::Matcher;
using matching::SessionManager;

MatchView::MatchView(Matchers::SP matchers,
                     IndexSearchable::SP indexSearchable,
                     IAttributeManager::SP attrMgr,
                     SessionManagerSP sessionMgr,
                     IDocumentMetaStoreContext::SP metaStore,
                     DocIdLimit &docIdLimit)
    : _matchers(std::move(matchers)),
      _indexSearchable(std::move(indexSearchable)),
      _attrMgr(std::move(attrMgr)),
      _sessionMgr(std::move(sessionMgr)),
      _metaStore(std::move(metaStore)),
      _docIdLimit(docIdLimit)
{ }

MatchView::~MatchView() = default;

Matcher::SP
MatchView::getMatcher(const vespalib::string & rankProfile) const
{
    return _matchers->lookup(rankProfile);
}

MatchContext::UP
MatchView::createContext() const {
    IAttributeContext::UP attrCtx = _attrMgr->createContext();
    auto searchCtx = std::make_unique<SearchContext>(_indexSearchable, _docIdLimit.get());
    return std::make_unique<MatchContext>(std::move(attrCtx), std::move(searchCtx));
}

std::unique_ptr<SearchReply>
MatchView::match(std::shared_ptr<const ISearchHandler> searchHandler, const SearchRequest &req,
                 vespalib::ThreadBundle &threadBundle) const
{
    Matcher::SP matcher = getMatcher(req.ranking);
    SearchSession::OwnershipBundle owned_objects;
    owned_objects.search_handler = std::move(searchHandler);
    owned_objects.readGuard = _metaStore->getReadGuard();
    owned_objects.context = createContext();
    MatchContext *ctx = owned_objects.context.get();
    const search::IDocumentMetaStore & dms = owned_objects.readGuard->get();
    const bucketdb::BucketDBOwner & bucketDB = _metaStore->get().getBucketDB();
    return matcher->match(req, threadBundle, ctx->getSearchContext(), ctx->getAttributeContext(),
                          *_sessionMgr, dms, bucketDB, std::move(owned_objects));
}

} // namespace proton
