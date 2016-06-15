// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.server.searchview");

#include "searchcontext.h"
#include "searchview.h"
#include <vespa/searchcore/proton/docsummary/docsumcontext.h>
#include <vespa/searchcore/proton/matching/match_context.h>

using proton::matching::MatchContext;
using search::AttributeGuard;
using search::AttributeVector;
using search::attribute::IAttributeContext;
using search::docsummary::IDocsumStore;
using search::engine::DocsumReply;
using search::engine::DocsumRequest;

namespace proton
{

using matching::ISearchContext;
using matching::Matcher;

namespace
{

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
            LOG(debug,
                "Document with global id '%s' is not in the document db,"
                " will return empty docsum",
                h.gid.toString().c_str());
        }
        LOG(spam,
            "convertGidToLid(DocsumRequest): hit[%zu]: gid(%s) -> lid(%u)",
            i, h.gid.toString().c_str(), h.docid);
    }
}

/**
 * Maps the lids in the reply to gids using the original request.
 **/
void
convertLidsToGids(DocsumReply &reply, const DocsumRequest &request)
{
    LOG_ASSERT(reply.docsums.size() == request.hits.size());
    for (size_t i = 0; i < reply.docsums.size(); ++i) {
        const DocsumRequest::Hit & h = request.hits[i];
        DocsumReply::Docsum & d = reply.docsums[i];
        d.gid = h.gid;
        LOG(spam,
            "convertLidToGid(DocsumReply): docsum[%zu]: lid(%u) -> gid(%s)",
            i, d.docid, d.gid.toString().c_str());
    }
}

/**
 * Create empty docsum reply
 **/
DocsumReply::UP
createEmptyReply(const DocsumRequest & request)
{
    DocsumReply::UP reply(new DocsumReply());
    for (size_t i = 0; i < request.hits.size(); ++i) {
        reply->docsums.push_back(DocsumReply::Docsum());
        reply->docsums.back().gid = request.hits[i].gid;
    }
    return reply;
}

}


SearchView::SearchView(const ISummaryManager::ISummarySetup::SP &
                       summarySetup,
                       const MatchView::SP & matchView)
    : boost::noncopyable(),
      ISearchHandler(),
      _summarySetup(summarySetup),
      _matchView(matchView)
{
}


DocsumReply::UP
SearchView::getDocsums(const DocsumRequest & req)
{
    LOG(debug, "getDocsums(): resultClass(%s), numHits(%zu)",
        req.resultClassName.c_str(), req.hits.size());
    if (_summarySetup->getResultConfig().
        LookupResultClassId(req.resultClassName.c_str()) ==
        search::docsummary::ResultConfig::NoClassID()) {
        LOG(warning,
            "There is no summary class with name '%s' in the summary config. "
            "Returning empty document summary for %zu hit(s)",
            req.resultClassName.c_str(), req.hits.size());
        return createEmptyReply(req);
    }
    { // convert from gids to lids
        IDocumentMetaStoreContext::IReadGuard::UP readGuard =
            _matchView->getDocumentMetaStore()->getReadGuard();
        convertGidsToLids(req, readGuard->get(), _matchView->getDocIdLimit().get());
    }
    IDocsumStore::UP store(_summarySetup->createDocsumStore(req.resultClassName));
    Matcher::SP matcher = _matchView->getMatcher(req.ranking);
    MatchContext::UP mctx = _matchView->createContext();
    DocsumContext::UP
        ctx(new DocsumContext(req,
                              _summarySetup->getDocsumWriter(),
                              *store,
                              matcher,
                              mctx->getSearchContext(),
                              mctx->getAttributeContext(),
                              *_summarySetup->getAttributeManager(),
                              *getSessionManager()));
    DocsumReply::UP reply = ctx->getDocsums();
    if ( ! req.useRootSlime()) {
        convertLidsToGids(*reply, req);
    }
    return reply;
}

search::engine::SearchReply::UP
SearchView::match(const ISearchHandler::SP &self,
                  const search::engine::SearchRequest &req,
                  vespalib::ThreadBundle &threadBundle) const {
    return _matchView->match(self, req, threadBundle);
}

} // namespace proton
