// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "result_processor.h"
#include "partial_result.h"
#include "sessionmanager.h"
#include <vespa/searchcore/proton/documentmetastore/documentmetastoreattribute.h>
#include <vespa/searchcore/grouping/groupingmanager.h>
#include <vespa/searchcore/grouping/groupingcontext.h>
#include <vespa/searchlib/uca/ucaconverter.h>
#include <vespa/searchlib/engine/searchreply.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.result_processor");

using search::attribute::IAttributeContext;
using search::grouping::GroupingSession;
using search::grouping::GroupingContext;
using search::grouping::SessionId;

namespace proton::matching {

ResultProcessor::Result::Result(std::unique_ptr<search::engine::SearchReply> reply, size_t numFs4Hits)
    : _reply(std::move(reply)),
      _numFs4Hits(numFs4Hits)
{ }

ResultProcessor::Result::~Result() = default;

ResultProcessor::Sort::Sort(uint32_t partitionId, const vespalib::Doom & doom, IAttributeContext &ac, const vespalib::string &ss)
    : sorter(FastS_DefaultResultSorter::instance()),
      _ucaFactory(std::make_unique<search::uca::UcaConverterFactory>()),
      sortSpec(DocumentMetaStoreAttribute::getFixedName(), partitionId, doom, *_ucaFactory)
{
    if (!ss.empty() && sortSpec.Init(ss.c_str(), ac)) {
        sorter = &sortSpec;
    }
}

ResultProcessor::Context::Context(Sort::UP s, PartialResult::UP r, GroupingContext::UP g)
    : sort(std::move(s)),
      result(std::move(r)),
      grouping(std::move(g)),
      groupingSource(grouping.get())
{ }

ResultProcessor::Context::~Context() = default;

void
ResultProcessor::GroupingSource::merge(Source &s) {
    auto &rhs = dynamic_cast<GroupingSource&>(s);
    assert((ctx == nullptr) == (rhs.ctx == nullptr));
    if (ctx != nullptr) {
        search::grouping::GroupingManager man(*ctx);
        man.merge(*rhs.ctx);
    }
}

ResultProcessor::ResultProcessor(IAttributeContext &attrContext,
                                 const search::IDocumentMetaStore &metaStore,
                                 SessionManager &sessionMgr,
                                 GroupingContext &groupingContext,
                                 const vespalib::string &sessionId,
                                 const vespalib::string &sortSpec,
                                 size_t offset, size_t hits)
    : _attrContext(attrContext),
      _metaStore(metaStore),
      _sessionMgr(sessionMgr),
      _groupingContext(groupingContext),
      _groupingSession(),
      _sortSpec(sortSpec),
      _offset(offset),
      _hits(hits),
      _wasMerged(false)
{
    if (!_groupingContext.empty()) {
        _groupingSession = std::make_unique<GroupingSession>(sessionId, _groupingContext, attrContext);
    }
}

ResultProcessor::~ResultProcessor() = default;

void
ResultProcessor::prepareThreadContextCreation(size_t num_threads)
{
    if (num_threads > 1) {
        _wasMerged = true;
    }
    if (_groupingSession) {
        _groupingSession->prepareThreadContextCreation(num_threads);
    }
}

ResultProcessor::Context::UP
ResultProcessor::createThreadContext(const vespalib::Doom & hardDoom, size_t thread_id, uint32_t distributionKey)
{
    auto sort = std::make_unique<Sort>(distributionKey, hardDoom, _attrContext, _sortSpec);
    auto result = std::make_unique<PartialResult>((_offset + _hits), sort->hasSortData());
    search::grouping::GroupingContext::UP groupingContext;
    if (_groupingSession) {
        groupingContext = _groupingSession->createThreadContext(thread_id, _attrContext);
    }
    return std::make_unique<Context>(std::move(sort), std::move(result), std::move(groupingContext));
}

std::vector<std::pair<uint32_t,uint32_t>>
ResultProcessor::extract_docid_ordering(const PartialResult &result) const
{
    size_t est_size = result.size() - std::min(result.size(), _offset);
    std::vector<std::pair<uint32_t,uint32_t>> list;
    list.reserve(est_size);
    for (size_t i = _offset; i < result.size(); ++i) {
        list.emplace_back(result.hit(i).getDocId(), list.size());
    }
    std::sort(list.begin(), list.end(), [](const auto &a, const auto &b){ return (a.first < b.first); });
    return list;
}

ResultProcessor::Result::UP
ResultProcessor::makeReply(PartialResultUP full_result)
{
    auto reply = std::make_unique<search::engine::SearchReply>();
    const search::IDocumentMetaStore &metaStore = _metaStore;
    search::engine::SearchReply &r = *reply;
    PartialResult &result = *full_result;
    size_t numFs4Hits(0);
    if (_groupingSession) {
        if (_wasMerged) {
            _groupingSession->getGroupingManager().prune();
        }
        _groupingSession->getGroupingManager().convertToGlobalId(metaStore);
        _groupingSession->continueExecution(_groupingContext);
        numFs4Hits = _groupingContext.countFS4Hits();
        _groupingContext.getResult().swap(r.groupResult);
        if (!_groupingSession->getSessionId().empty() && !_groupingSession->finished()) {
            _sessionMgr.insert(std::move(_groupingSession));
        }
    }
    uint32_t hitOffset = _offset;
    uint32_t hitcnt    = (result.size() > hitOffset) ? (result.size() - hitOffset) : 0;
    r.totalHitCount    = result.totalHits();
    r.hits.resize(hitcnt);
    document::GlobalId gid;
    for (size_t i = 0; i < hitcnt; ++i) {
        search::engine::SearchReply::Hit &dst = r.hits[i];
        const search::RankedHit &src = result.hit(hitOffset + i);
        uint32_t docId = src.getDocId();
        if (metaStore.getGidEvenIfMoved(docId, gid)) {
            dst.gid = gid;
        }
        dst.metric = src.getRank();
        LOG(debug, "convertLidToGid: hit[%zu]: lid(%u) -> gid(%s)", i, docId, dst.gid.toString().c_str());
    }
    if (result.hasSortData() && (hitcnt > 0)) {
        size_t sortDataSize = result.sortDataSize();
        for (size_t i = 0; i < hitOffset; ++i) {
            sortDataSize -= result.sortData(i).second;
        }
        r.sortIndex.resize(hitcnt + 1);
        r.sortData.resize(sortDataSize);
        uint32_t sortOffset = 0;
        for (size_t i = 0; i < hitcnt; ++i) {
            const PartialResult::SortRef &sr = result.sortData(hitOffset + i);
            r.sortIndex[i] = sortOffset;
            memcpy(&r.sortData[0] + sortOffset, sr.first, sr.second);
            sortOffset += sr.second;
        }
        r.sortIndex[hitcnt] = sortOffset;
        assert(sortOffset == sortDataSize);
    }
    numFs4Hits += reply->hits.size();
    return std::make_unique<Result>(std::move(reply), numFs4Hits);
}

}
