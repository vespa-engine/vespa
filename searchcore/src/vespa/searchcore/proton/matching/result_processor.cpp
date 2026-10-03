// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "result_processor.h"

#include "partial_result.h"
#include "sessionmanager.h"

#include <vespa/searchcore/grouping/groupingcontext.h>
#include <vespa/searchcore/grouping/groupingmanager.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastoreattribute.h>
#include <vespa/searchlib/engine/searchreply.h>
#include <vespa/searchlib/uca/ucaconverter.h>
#include <vespa/vespalib/util/time.h>

#include <optional>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.result_processor");

using search::attribute::IAttributeContext;
using search::grouping::GroupingContext;
using search::grouping::GroupingSession;
using search::grouping::SessionId;

namespace proton::matching {

ResultProcessor::Result::Result(std::unique_ptr<search::engine::SearchReply> reply, size_t numFs4Hits)
    : _reply(std::move(reply)), _numFs4Hits(numFs4Hits) {
}

ResultProcessor::Result::~Result() = default;

ResultProcessor::Sort::Sort(uint32_t partitionId, const vespalib::Doom& doom, IAttributeContext& ac,
                            const std::string& ss, INumericSortValueProvider* sort_value_provider)
    : sorter(FastS_DefaultResultSorter::instance()),
      _ucaFactory(std::make_unique<search::uca::UcaConverterFactory>()),
      sortSpec(DocumentMetaStoreAttribute::getFixedName(), partitionId, doom, *_ucaFactory),
      _feature_binding_failed(false) {
    if (!ss.empty() && sortSpec.Init(ss.c_str(), ac)) {
        if (sortSpec.bind_numeric_provider(sort_value_provider)) {
            sorter = &sortSpec;
        } else {
            _feature_binding_failed = true;
        }
    }
}

ResultProcessor::Sort::~Sort() = default;

ResultProcessor::Context::Context(const search::BitVector& validLids, Sort::UP s, PartialResultUP r,
                                  GroupingContext::UP g)
    : _validLids(validLids),
      sort(std::move(s)),
      result(std::move(r)),
      grouping(std::move(g)),
      groupingSource(grouping.get()) {
}

ResultProcessor::Context::~Context() = default;

namespace {

double elapsed_ms(vespalib::steady_time start) {
    return vespalib::count_ns(vespalib::steady_clock::now() - start) / 1000000.0;
}

} // namespace

void ResultProcessor::GroupingSource::merge(Source& s) {
    auto& rhs = dynamic_cast<GroupingSource&>(s);
    assert((ctx == nullptr) == (rhs.ctx == nullptr));
    if (ctx != nullptr) {
        std::optional<vespalib::steady_time> start;
        if (timed) {
            start.emplace(vespalib::steady_clock::now());
        }
        search::grouping::GroupingManager man(*ctx);
        man.merge(*rhs.ctx);
        if (start) {
            // Not a data race although the merging thread may not be the thread owning this
            // source: the merge director hands each pair of sources to exactly one thread and
            // synchronizes (rendezvous) between merge steps, so updates of a source never overlap,
            // and the totals in the source of thread 0 are only read by makeReply(), after the
            // thread bundle has joined all match threads.
            merge_time_s += rhs.merge_time_s + vespalib::to_s(vespalib::steady_clock::now() - *start);
            merge_count += rhs.merge_count + 1;
        }
    }
}

ResultProcessor::ResultProcessor(IAttributeContext& attrContext, const search::IDocumentMetaStore& metaStore,
                                 SessionManager& sessionMgr, GroupingContext& groupingContext,
                                 const std::string& sessionId, const std::string& sortSpec, size_t offset,
                                 size_t hits)
    : _attrContext(attrContext),
      _metaStore(metaStore),
      _sessionMgr(sessionMgr),
      _groupingContext(groupingContext),
      _groupingSession(),
      _sortSpec(sortSpec),
      _offset(offset),
      _hits(hits),
      _wasMerged(false),
      _sort_feature_failed(false),
      _collect_grouping_details(false),
      _grouping_session_cached(false),
      _grouping_trace(),
      _first_grouping_source(nullptr),
      _grouping_merge_ms(0.0),
      _grouping_merge_count(0),
      _grouping_prune_ms(0.0),
      _grouping_continue_ms(0.0) {
    if (!_groupingContext.empty()) {
        _groupingSession = std::make_unique<GroupingSession>(sessionId, _groupingContext, attrContext, nullptr);
    }
}

ResultProcessor::~ResultProcessor() = default;

void ResultProcessor::prepareThreadContextCreation(size_t num_threads) {
    if (num_threads > 1) {
        _wasMerged = true;
    }
    if (_groupingSession) {
        _groupingSession->prepareThreadContextCreation(num_threads);
    }
}

std::unique_ptr<ResultProcessor::Context>
ResultProcessor::createThreadContext(const vespalib::Doom& hardDoom, size_t thread_id, uint32_t distributionKey,
                                     INumericSortValueProvider* sort_value_provider) {
    auto sort = std::make_unique<Sort>(distributionKey, hardDoom, _attrContext, _sortSpec, sort_value_provider);
    if (sort->feature_binding_failed()) {
        note_sort_feature_failure();
    }
    auto result = std::make_unique<PartialResult>((_offset + _hits), sort->hasSortData());
    search::grouping::GroupingContext::UP groupingContext;
    if (_groupingSession) {
        groupingContext = _groupingSession->createThreadContext(thread_id, _attrContext, nullptr);
    }
    auto context = std::make_unique<Context>(_metaStore.getValidLids(), std::move(sort), std::move(result),
                                             std::move(groupingContext));
    context->groupingSource.timed = _collect_grouping_details;
    if (thread_id == 0) {
        // Only read by makeReply(), after all threads are done; the context outlives that call.
        _first_grouping_source = &context->groupingSource;
    }
    return context;
}

std::vector<std::pair<uint32_t, uint32_t>>
ResultProcessor::extract_docid_ordering(const PartialResult& result) const {
    size_t                                     est_size = result.size() - std::min(result.size(), _offset);
    std::vector<std::pair<uint32_t, uint32_t>> list;
    list.reserve(est_size);
    for (size_t i = _offset; i < result.size(); ++i) {
        list.emplace_back(result.hit(i).getDocId(), list.size());
    }
    std::sort(list.begin(), list.end(), [](const auto& a, const auto& b) { return (a.first < b.first); });
    return list;
}

ResultProcessor::Result::UP ResultProcessor::makeReply(PartialResultUP full_result) {
    auto                         reply = std::make_unique<search::engine::SearchReply>();
    search::engine::SearchReply& r = *reply;
    PartialResult&               result = *full_result;
    size_t                       numFs4Hits(0);
    if (_groupingSession) {
        if (_collect_grouping_details && (_first_grouping_source != nullptr)) {
            _grouping_merge_ms = _first_grouping_source->merge_time_s * 1000.0;
            _grouping_merge_count = _first_grouping_source->merge_count;
        }
        std::optional<vespalib::steady_time> start;
        if (_wasMerged) {
            if (_collect_grouping_details) {
                start.emplace(vespalib::steady_clock::now());
            }
            _groupingSession->getGroupingManager().prune();
            if (start) {
                _grouping_prune_ms = elapsed_ms(*start);
            }
        }
        if (_collect_grouping_details) {
            start.emplace(vespalib::steady_clock::now());
        }
        _groupingSession->continueExecution(_groupingContext, _collect_grouping_details ? &_grouping_trace : nullptr);
        if (start) {
            _grouping_continue_ms = elapsed_ms(*start);
        }
        numFs4Hits = _groupingContext.countFS4Hits();
        _groupingContext.getResult().swap(r.groupResult);
        if (!_groupingSession->getSessionId().empty() && !_groupingSession->finished()) {
            _grouping_session_cached = true;
            _sessionMgr.insert(std::move(_groupingSession));
        }
    }
    uint32_t hitOffset = _offset;
    uint32_t hitcnt = (result.size() > hitOffset) ? (result.size() - hitOffset) : 0;
    r.totalHitCount = result.totalHits();
    r.hits.resize(hitcnt);
    document::GlobalId gid;
    for (size_t i = 0; i < hitcnt; ++i) {
        search::engine::SearchReply::Hit& dst = r.hits[i];
        const search::RankedHit&          src = result.hit(hitOffset + i);
        uint32_t                          docId = src.getDocId();
        if (_metaStore.getGidEvenIfMoved(docId, gid)) {
            dst.gid = gid;
        } else {
            LOG(warning, "Missing globalid for hit %u", docId);
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
            const PartialResult::SortRef& sr = result.sortData(hitOffset + i);
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

} // namespace proton::matching
