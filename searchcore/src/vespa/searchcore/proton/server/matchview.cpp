// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matchview.h"
#include "searchcontext.h"
#include <vespa/searchcore/proton/attribute/i_attribute_manager.h>
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
                     std::shared_ptr<IndexSearchable> indexSearchable,
                     std::shared_ptr<IAttributeManager> attrMgr,
                     SessionManager & sessionMgr,
                     IDocumentMetaStoreContext::SP metaStore,
                     DocIdLimit &docIdLimit)
    : _matchers(std::move(matchers)),
      _indexSearchable(std::move(indexSearchable)),
      _attrMgr(std::move(attrMgr)),
      _sessionMgr(sessionMgr),
      _metaStore(std::move(metaStore)),
      _docIdLimit(docIdLimit)
{ }

MatchView::~MatchView() = default;

std::shared_ptr<Matcher>
MatchView::getMatcher(const vespalib::string & rankProfile) const
{
    return _matchers->lookup(rankProfile);
}

MatchContext
MatchView::createContext() const {
    auto searchCtx = std::make_unique<SearchContext>(_indexSearchable, _docIdLimit.get());
    return {_attrMgr->createContext(), std::move(searchCtx)};
}

std::unique_ptr<SearchReply>
MatchView::match(std::shared_ptr<const ISearchHandler> searchHandler, const SearchRequest &req,
                 vespalib::ThreadBundle &threadBundle) const
{
    Matcher::SP matcher = getMatcher(req.ranking);
    SearchSession::OwnershipBundle owned_objects(createContext(), std::move(searchHandler));
    owned_objects.readGuard = _metaStore->getReadGuard();
    ISearchContext & search_ctx = owned_objects.context.getSearchContext();
    IAttributeContext & attribute_ctx = owned_objects.context.getAttributeContext();
    const search::IDocumentMetaStore & dms = owned_objects.readGuard->get();
    const bucketdb::BucketDBOwner & bucketDB = _metaStore->get().getBucketDB();
    return matcher->match(req, threadBundle, search_ctx, attribute_ctx,
                          _sessionMgr, dms, bucketDB, std::move(owned_objects));
}

} // namespace proton
