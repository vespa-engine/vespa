// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchview.h"
#include <vespa/searchcore/proton/docsummary/docsumcontext.h>
#include <vespa/searchlib/engine/searchreply.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/issue.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.searchview");

using proton::matching::MatchContext;
using search::docsummary::IDocsumStore;
using search::docsummary::ResultConfig;
using search::engine::DocsumReply;
using search::engine::DocsumRequest;
using search::engine::SearchReply;
using vespalib::ThreadBundle;
using vespalib::Issue;

namespace proton {

using matching::ISearchContext;
using matching::Matcher;

namespace {

/**
 * Maps the gids in the request to lids using the given document meta store.
 * A reader guard must be taken before calling this function.
 **/
void
convertGidsToLids(const DocsumRequest & request,
                  const search::IDocumentMetaStore &metaStore,
                  uint32_t docIdLimit)
{
    document::GlobalId empty;
    uint32_t lid = 0;
    for (size_t i = 0; i < request.hits.size(); ++i) {
        const DocsumRequest::Hit & h = request.hits[i];
        if (metaStore.getLid(h.gid, lid) && lid < docIdLimit) {
            h.docid = lid;
        } else {
            h.docid = search::endDocId;
            LOG(debug, "Document with global id '%s' is not in the document db, will return empty docsum", h.gid.toString().c_str());
        }
        LOG(spam, "convertGidToLid(DocsumRequest): hit[%zu]: gid(%s) -> lid(%u)", i, h.gid.toString().c_str(), h.docid);
    }
}

bool
requestHasLidAbove(const DocsumRequest & request, uint32_t docIdLimit)
{
    for (const DocsumRequest::Hit & h : request.hits) {
        if (h.docid >= docIdLimit) {
            return true;
        }
    }
    return false;
}

bool
hasAnyLidsMoved(const DocsumRequest & request,
                const search::IDocumentMetaStore &metaStore)
{
    for (const DocsumRequest::Hit & h : request.hits) {
        uint32_t lid = 0;
        if (h.docid != search::endDocId) {
            if (!metaStore.getLid(h.gid, lid) || (lid != h.docid)) {
                LOG(debug, "lid = %d moved to %d", h.docid, lid);
                return true;
            }
        }
    }
    return false;
}

/**
 * Create empty docsum reply
 **/
DocsumReply::UP
createEmptyReply(const DocsumRequest &)
{
    return std::make_unique<DocsumReply>();
}

}

std::shared_ptr<SearchView>
SearchView::create(ISummaryManager::ISummarySetup::SP summarySetup, MatchView::SP matchView) {
    return std::shared_ptr<SearchView>( new SearchView(std::move(summarySetup), std::move(matchView)));
}
SearchView::SearchView(ISummaryManager::ISummarySetup::SP summarySetup, MatchView::SP matchView)
    : ISearchHandler(),
      _summarySetup(std::move(summarySetup)),
      _matchView(std::move(matchView))
{ }

SearchView::~SearchView() = default;

DocsumReply::UP
SearchView::getDocsums(const DocsumRequest & req)
{
    LOG(spam, "getDocsums(): resultClass(%s), numHits(%zu)", req.resultClassName.c_str(), req.hits.size());
    if (_summarySetup->getResultConfig().lookupResultClassId(req.resultClassName.c_str()) == ResultConfig::noClassID()) {
        Issue::report("There is no summary class with name '%s' in the summary config. Returning empty document summary for %zu hit(s)",
                     req.resultClassName.c_str(), req.hits.size());
        return createEmptyReply(req);
    }
    SearchView::InternalDocsumReply reply = getDocsumsInternal(req);
    while ( ! reply.second ) {
        LOG(debug, "Must refetch docsums since the lids have moved.");
        reply = getDocsumsInternal(req);
    }
    return std::move(reply.first);
}

SearchView::InternalDocsumReply
SearchView::getDocsumsInternal(const DocsumRequest & req)
{
    IDocumentMetaStoreContext::IReadGuard::UP readGuard = _matchView->getDocumentMetaStore()->getReadGuard();
    const search::IDocumentMetaStore & metaStore = readGuard->get();
    uint32_t numUsedLids = metaStore.getNumUsedLids();
    uint64_t startGeneration = readGuard->get().getCurrentGeneration();

    convertGidsToLids(req, metaStore, _matchView->getDocIdLimit().get());
    IDocsumStore::UP store(_summarySetup->createDocsumStore());
    MatchContext::UP mctx = _matchView->createContext();
    auto ctx = std::make_unique<DocsumContext>(req, _summarySetup->getDocsumWriter(), *store, _matchView->getMatcher(req.ranking),
                                               mctx->getSearchContext(), mctx->getAttributeContext(),
                                               *_summarySetup->getAttributeManager(), *getSessionManager());
    SearchView::InternalDocsumReply reply(ctx->getDocsums(), true);
    uint64_t endGeneration = readGuard->get().getCurrentGeneration();
    if (startGeneration != endGeneration) {
        if (requestHasLidAbove(req, std::min(numUsedLids, metaStore.getNumUsedLids()))) {
            if (hasAnyLidsMoved(req, metaStore)) {
                reply.second = false;
            }
        }
    }
    return reply;
}

std::unique_ptr<SearchReply>
SearchView::match(const SearchRequest &req, ThreadBundle &threadBundle) const {
    return _matchView->match(shared_from_this(), req, threadBundle);
}

} // namespace proton
