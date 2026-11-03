// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchview.h"

#include <vespa/searchcore/proton/docsummary/docsumcontext.h>
#include <vespa/searchlib/engine/searchreply.h>
#include <vespa/searchlib/queryeval/begin_and_end_id.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/issue.h>
#include <vespa/vespalib/util/lambdatask.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.searchview");

using proton::matching::MatchContext;
using search::docsummary::IDocsumStore;
using search::docsummary::ResultConfig;
using search::engine::DocsumReply;
using search::engine::DocsumRequest;
using search::engine::SearchReply;
using vespalib::Issue;
using vespalib::makeLambdaTask;
using vespalib::ThreadBundle;

namespace proton {

using matching::ISearchContext;
using matching::Matcher;

namespace {

/**
 * Maps the gids in the request to lids using the given document meta store.
 * A reader guard must be taken before calling this function.
 **/
void convertGidsToLids(const DocsumRequest& request, const search::IDocumentMetaStore& metaStore,
                       uint32_t docIdLimit) {
    uint32_t lid = 0;
    for (size_t i = 0; i < request.hits.size(); ++i) {
        const DocsumRequest::Hit& h = request.hits[i];
        if (metaStore.getLid(h.gid, lid) && lid < docIdLimit) {
            h.docid = lid;
        } else {
            h.docid = search::endDocId;
            LOG(debug, "Document with global id '%s' is not in the document db, will return empty docsum",
                h.gid.toString().c_str());
        }
        LOG(spam, "convertGidToLid(DocsumRequest): hit[%zu]: gid(%s) -> lid(%u)", i, h.gid.toString().c_str(),
            h.docid);
    }
}

bool requestHasLidAbove(const DocsumRequest& request, uint32_t docIdLimit) {
    for (const DocsumRequest::Hit& h : request.hits) {
        if (h.docid >= docIdLimit) {
            return true;
        }
    }
    return false;
}

bool hasAnyLidsMoved(const DocsumRequest& request, const search::IDocumentMetaStore& metaStore) {
    for (const DocsumRequest::Hit& h : request.hits) {
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
std::unique_ptr<DocsumReply> createEmptyReply(const DocsumRequest&) {
    return std::make_unique<DocsumReply>();
}

/*
 * Schedule deletion of search view on an executor to avoid latency spike for matching or document summary requests in
 * the last thread using a search view, e.g. after dump of memory index to disk.
 */

class DeleteSearchView {
    std::weak_ptr<vespalib::Executor> _delete_search_view_executor;

public:
    explicit DeleteSearchView(std::weak_ptr<vespalib::Executor> delete_search_view_executor) noexcept;
    ~DeleteSearchView();
    void operator()(SearchView* search_view) const noexcept;
};

DeleteSearchView::DeleteSearchView(std::weak_ptr<vespalib::Executor> delete_search_view_executor) noexcept
    : _delete_search_view_executor(std::move(delete_search_view_executor)) {
}

DeleteSearchView::~DeleteSearchView() = default;

void DeleteSearchView::operator()(SearchView* search_view) const noexcept {
    auto executor = _delete_search_view_executor.lock();
    if (executor) {
        auto task = makeLambdaTask([search_view]() { delete search_view; });
        auto rejected_task = executor->execute(std::move(task));
        if (rejected_task) {
            rejected_task->run();
        }
    } else {
        delete search_view;
    }
}

} // namespace

std::shared_ptr<SearchView> SearchView::create(std::shared_ptr<ISummaryManager::ISummarySetup> summarySetup,
                                               std::shared_ptr<MatchView>                      matchView,
                                               std::shared_ptr<vespalib::Executor> delete_search_view_executor) {
    auto view = std::make_unique<SearchView>(std::move(summarySetup), std::move(matchView), ctor_tag{});
    return std::shared_ptr<SearchView>(view.release(), DeleteSearchView(std::move(delete_search_view_executor)));
}

SearchView::SearchView(std::shared_ptr<ISummaryManager::ISummarySetup> summarySetup,
                       std::shared_ptr<MatchView>                      matchView, ctor_tag)
    : ISearchHandler(), _summarySetup(std::move(summarySetup)), _matchView(std::move(matchView)) {
}

SearchView::~SearchView() = default;

std::unique_ptr<DocsumReply> SearchView::getDocsums(const DocsumRequest& req) {
    LOG(spam, "getDocsums(): resultClass(%s), numHits(%zu)", req.resultClassName.c_str(), req.hits.size());
    if (_summarySetup->getResultConfig().lookupResultClass(req.resultClassName) == nullptr) {
        Issue::report("There is no summary class with name '%s' in the summary config. Returning empty document "
                      "summary for %zu hit(s)",
                      req.resultClassName.c_str(), req.hits.size());
        return createEmptyReply(req);
    }
    SearchView::InternalDocsumReply reply = getDocsumsInternal(req);
    while (!reply.second) {
        LOG(debug, "Must refetch docsums since the lids have moved.");
        reply = getDocsumsInternal(req);
    }
    return std::move(reply.first);
}

SearchView::InternalDocsumReply SearchView::getDocsumsInternal(const DocsumRequest& req) {
    auto                              readGuard = _matchView->getDocumentMetaStore()->getReadGuard();
    const search::IDocumentMetaStore& metaStore = readGuard->get();
    uint32_t                          numUsedLids = metaStore.getNumUsedLids();
    auto                              startGeneration = readGuard->get().getCurrentGeneration();

    convertGidsToLids(req, metaStore, _matchView->getDocIdLimit().get());
    auto store(_summarySetup->createDocsumStore());
    auto mctx = _matchView->createContext(req);
    auto ctx = std::make_unique<DocsumContext>(
        req, _summarySetup->getDocsumWriter(), *store, _matchView->getMatcher(req.ranking), mctx.getSearchContext(),
        mctx.getAttributeContext(), *_summarySetup->getAttributeManager(), getSessionManager());
    SearchView::InternalDocsumReply reply(ctx->getDocsums(), true);
    auto                            endGeneration = readGuard->get().getCurrentGeneration();
    if (startGeneration != endGeneration) {
        if (requestHasLidAbove(req, std::min(numUsedLids, metaStore.getNumUsedLids()))) {
            if (hasAnyLidsMoved(req, metaStore)) {
                reply.second = false;
            }
        }
    }
    return reply;
}

std::unique_ptr<SearchReply> SearchView::match(const SearchRequest& req, ThreadBundle& threadBundle) const {
    return _matchView->match(shared_from_this(), req, threadBundle);
}

} // namespace proton
