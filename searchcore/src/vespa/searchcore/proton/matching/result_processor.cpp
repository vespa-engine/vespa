// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.result_processor");
#include "result_processor.h"
#include <vespa/searchlib/common/sortresults.h>
#include <vespa/searchlib/common/docstamp.h>
#include <vespa/searchlib/uca/ucaconverter.h>

namespace proton {
namespace matching {

ResultProcessor::Sort::Sort(const vespalib::Doom & doom, search::attribute::IAttributeContext &ac, const vespalib::string &ss)
    : sorter(FastS_DefaultResultSorter::instance()),
      _ucaFactory(std::make_unique<search::uca::UcaConverterFactory>()),
      sortSpec(doom, *_ucaFactory)
{
    if (!ss.empty() && sortSpec.Init(ss.c_str(), ac)) {
        sorter = &sortSpec;
    }
}

ResultProcessor::ResultProcessor(search::attribute::IAttributeContext &attrContext,
                                 const search::IDocumentMetaStore &metaStore,
                                 SessionManager &sessionMgr,
                                 search::grouping::GroupingContext &groupingContext,
                                 const search::grouping::SessionId &sessionId,
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
      _result(),
      _wasMerged(false)
{
    if (!_groupingContext.empty()) {
        _groupingSession.reset(new search::grouping::GroupingSession(sessionId,
                                       _groupingContext, attrContext));
    }
}

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
ResultProcessor::createThreadContext(const vespalib::Doom & doom, size_t thread_id)
{
    Sort::UP sort(new Sort(doom, _attrContext, _sortSpec));
    PartialResult::LP result(new PartialResult((_offset + _hits), sort->hasSortData()));
    if (thread_id == 0) {
        _result = result;
    }
    search::grouping::GroupingContext::UP groupingContext;
    if (_groupingSession.get() != 0) {
        groupingContext = _groupingSession->createThreadContext(thread_id, _attrContext);
    }
    return Context::UP(new Context(std::move(sort), result, std::move(groupingContext)));
}

ResultProcessor::Result::UP
ResultProcessor::makeReply()
{
    search::engine::SearchReply::UP reply(new search::engine::SearchReply());
    const search::IDocumentMetaStore &metaStore = _metaStore;
    search::engine::SearchReply &r = *reply;
    PartialResult &result = *_result;
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
        if (metaStore.getGid(docId, gid)) {
            dst.gid = gid;
        }
        dst.metric = src._rankValue;
        LOG(debug, "convertLidToGid: hit[%zu]: lid(%u) -> gid(%s)",
            i, docId, dst.gid.toString().c_str());
    }
    if (result.hasSortData() && hitcnt > 0) {
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

} // namespace proton::matching
} // namespace proton
