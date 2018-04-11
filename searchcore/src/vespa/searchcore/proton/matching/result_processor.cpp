// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "result_processor.h"
#include "partial_result.h"
#include "sessionmanager.h"
#include <vespa/searchcore/grouping/groupingmanager.h>
#include <vespa/searchcore/grouping/groupingcontext.h>
#include <vespa/searchlib/common/docstamp.h>
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

ResultProcessor::Result::~Result() { }

ResultProcessor::Sort::Sort(uint32_t partitionId, const vespalib::Doom & doom, IAttributeContext &ac, const vespalib::string &ss)
    : sorter(FastS_DefaultResultSorter::instance()),
      _ucaFactory(std::make_unique<search::uca::UcaConverterFactory>()),
      sortSpec(partitionId, doom, *_ucaFactory)
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

ResultProcessor::Context::~Context() { }

void
ResultProcessor::GroupingSource::merge(Source &s) {
    GroupingSource &rhs = static_cast<GroupingSource&>(s);
    assert((ctx == 0) == (rhs.ctx == 0));
    if (ctx != 0) {
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
                                 size_t offset, size_t hits,
                                 bool drop_sort_data)
    : _attrContext(attrContext),
      _metaStore(metaStore),
      _sessionMgr(sessionMgr),
      _groupingContext(groupingContext),
      _groupingSession(),
      _sortSpec(sortSpec),
      _offset(offset),
      _hits(hits),
      _drop_sort_data(drop_sort_data),
      _wasMerged(false)
{
    if (!_groupingContext.empty()) {
        _groupingSession.reset(new GroupingSession(sessionId, _groupingContext, attrContext));
    }
}

ResultProcessor::~ResultProcessor() { }

void
ResultProcessor::prepareThreadContextCreation(size_t num_threads)
{
    if (num_threads > 1) {
        _wasMerged = true;
    }
    if (_groupingSession.get() != 0) {
        _groupingSession->prepareThreadContextCreation(num_threads);
    }
}

ResultProcessor::Context::UP
ResultProcessor::createThreadContext(const vespalib::Doom & hardDoom, size_t thread_id, uint32_t distributionKey)
{
    Sort::UP sort(new Sort(distributionKey, hardDoom, _attrContext, _sortSpec));
    PartialResult::UP result(new PartialResult((_offset + _hits), sort->hasSortData()));
    search::grouping::GroupingContext::UP groupingContext;
    if (_groupingSession.get() != 0) {
        groupingContext = _groupingSession->createThreadContext(thread_id, _attrContext);
    }
    return Context::UP(new Context(std::move(sort), std::move(result), std::move(groupingContext)));
}

ResultProcessor::Result::UP
ResultProcessor::makeReply(PartialResultUP full_result)
{
    search::engine::SearchReply::UP reply(new search::engine::SearchReply());
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
    r.offset           = hitOffset;
    r.totalHitCount    = result.totalHits();
    r.hits.resize(hitcnt);
    document::GlobalId gid;
    for (size_t i = 0; i < hitcnt; ++i) {
        search::engine::SearchReply::Hit &dst = r.hits[i];
        const search::RankedHit &src = result.hit(hitOffset + i);
        uint32_t docId = src._docId;
        if (metaStore.getGidEvenIfMoved(docId, gid)) {
            dst.gid = gid;
        }
        dst.metric = src._rankValue;
        LOG(debug, "convertLidToGid: hit[%zu]: lid(%u) -> gid(%s)", i, docId, dst.gid.toString().c_str());
    }
    if (result.hasSortData() && (hitcnt > 0) && !_drop_sort_data) {
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
    return Result::UP(new Result(std::move(reply), numFs4Hits));
}

}
