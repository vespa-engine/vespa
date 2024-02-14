// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hitcollector.h"
#include <vespa/searchlib/fef/feature_resolver.h>
#include <vespa/searchlib/fef/utils.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".searchvisitor.hitcollector");

using vespalib::FeatureSet;
using vespalib::FeatureValues;
using vdslib::SearchResult;

using FefUtils = search::fef::Utils;

namespace streaming {

HitCollector::Hit::Hit(vsm::StorageDocument::SP doc, uint32_t docId, const MatchData & matchData,
                       double score, const void * sortData, size_t sortDataLen)
   : _docid(docId),
     _score(score),
     _document(std::move(doc)),
     _matchData(),
     _sortBlob(sortData, sortDataLen)
{
    _matchData.reserve(matchData.getNumTermFields());
    for (search::fef::TermFieldHandle handle = 0; handle < matchData.getNumTermFields(); ++handle) {
        _matchData.emplace_back(*matchData.resolveTermField(handle));
    }
}

HitCollector::Hit::~Hit() = default;

HitCollector::HitCollector(size_t wantedHits, bool use_sort_blob)
    : _hits(),
      _heap(),
      _use_sort_blob(use_sort_blob)
{
    _hits.reserve(16);
    _heap.reserve(wantedHits);
}

HitCollector::~HitCollector() = default;

const vsm::Document &
HitCollector::getDocSum(const search::DocumentIdT & docId) const
{
    for (const Hit & hit : _hits) {
        if (docId == hit.getDocId()) {
            return hit.getDocument();
        }
    }
    throw std::runtime_error(vespalib::make_string("Could not look up document id %d", docId));
}

bool
HitCollector::addHit(vsm::StorageDocument::SP doc, uint32_t docId, const MatchData & data, double score)
{
    return addHit(Hit(std::move(doc), docId, data, score));
}

bool
HitCollector::addHit(vsm::StorageDocument::SP doc, uint32_t docId, const MatchData & data,
                     double score, const void * sortData, size_t sortDataLen)
{
    return addHit(Hit(std::move(doc), docId, data, score, sortData, sortDataLen));
}

bool
HitCollector::addHitToHeap(uint32_t index) const
{
    if (_heap.capacity() == 0) return false;
    // return true if the given hit is better than the current worst one.
    const Hit & hit = _hits[index];
    return _use_sort_blob
        ? (hit.cmpSort(_hits[_heap[0]]) < 0)
        : (hit.cmpRank(_hits[_heap[0]]) < 0);
}

void
HitCollector::make_heap() {
    if (_use_sort_blob) {
        std::make_heap(_heap.begin(), _heap.end(), SortComparator(_hits));
    } else {
        std::make_heap(_heap.begin(), _heap.end(), RankComparator(_hits));
    }
}

void
HitCollector::pop_heap() {
    if (_use_sort_blob) {
        std::pop_heap(_heap.begin(), _heap.end(), SortComparator(_hits));
    } else {
        std::pop_heap(_heap.begin(), _heap.end(), RankComparator(_hits));
    }
}

void
HitCollector::push_heap() {
    if (_use_sort_blob) {
        std::push_heap(_heap.begin(), _heap.end(), SortComparator(_hits));
    } else {
        std::push_heap(_heap.begin(), _heap.end(), RankComparator(_hits));
    }
}

bool
HitCollector::addHit(Hit && hit)
{
    size_t avail = (_heap.capacity() - _heap.size());
    assert(_use_sort_blob != hit.getSortBlob().empty() );
    assert(_hits.size() <= hit.getDocId());
    _hits.emplace_back(std::move(hit));
    uint32_t index = _hits.size() - 1;
    if (avail > 1) {
        // No heap yet.
        _heap.emplace_back(index);
    } else if ((avail == 0) && addHitToHeap(index)) { // already a heap
        pop_heap();
        uint32_t toDrop = _heap.back();
        _heap.back() = index;
        push_heap();
        // Signal that it is not among the best and should hence never be accessed.
        // Drop early to catch logic flaws.
        _hits[toDrop].dropDocument();
    } else if (avail == 1) { // make a heap of the hit vector
        _heap.emplace_back(index);
        make_heap();
    } else {
        // The document might be invalid if it did not make the cut,
        // so clear the reference here to catch logic flaws early.
        _hits.back().dropDocument();
        return false;
    }
    return true;
}

std::vector<uint32_t>
HitCollector::bestLids() const {
    std::vector<uint32_t> hitsOnHeap = _heap;
    std::sort(hitsOnHeap.begin(), hitsOnHeap.end());
    return hitsOnHeap;
}

void
HitCollector::fillSearchResult(vdslib::SearchResult & searchResult, FeatureValues&& match_features) const
{
    for (uint32_t lid : bestLids()) {
        const Hit & hit = _hits[lid];
        vespalib::string documentId(hit.getDocument().docDoc().getId().toString());
        search::DocumentIdT docId = hit.getDocId();
        SearchResult::RankType rank = hit.getRankScore();

        LOG(debug, "fillSearchResult: gDocId(%s), lDocId(%u), rank(%f)", documentId.c_str(), docId, (float)rank);

        if (hit.getSortBlob().empty()) {
            searchResult.addHit(docId, documentId.c_str(), rank);
        } else {
            searchResult.addHit(docId, documentId.c_str(), rank, hit.getSortBlob().c_str(), hit.getSortBlob().size());
        }
    }
    searchResult.set_match_features(std::move(match_features));
}

void
HitCollector::fillSearchResult(vdslib::SearchResult & searchResult) const
{
    fillSearchResult(searchResult, FeatureValues());
}

FeatureSet::SP
HitCollector::getFeatureSet(IRankProgram &rankProgram,
                            const FeatureResolver &resolver,
                            const search::StringStringMap &feature_rename_map) const
{
    if (resolver.num_features() == 0 || _heap.empty()) {
        return std::make_shared<FeatureSet>();
    }
    auto names = FefUtils::extract_feature_names(resolver, feature_rename_map);
    FeatureSet::SP retval = std::make_shared<FeatureSet>(names, _heap.size());
    for (uint32_t lid : bestLids()) {
        const Hit & hit = _hits[lid];
        uint32_t docId = hit.getDocId();
        rankProgram.run(docId, hit.getMatchData());
        auto * f = retval->getFeaturesByIndex(retval->addDocId(docId));
        FefUtils::extract_feature_values(resolver, docId, f);
    }
    return retval;
}

FeatureSet::SP
HitCollector::getFeatureSet(IRankProgram &rankProgram,
                            search::DocumentIdT docId,
                            const FeatureResolver &resolver,
                            const search::StringStringMap &feature_rename_map)
{
    LOG(debug, "docId = %d, _hits.size = %zu", docId, _hits.size());
    if (resolver.num_features() == 0 || _hits.empty()) {
        return std::make_shared<FeatureSet>();
    }
    auto names = FefUtils::extract_feature_names(resolver, feature_rename_map);
    FeatureSet::SP retval = std::make_shared<FeatureSet>(names, _hits.size());
    for (const Hit & hit : _hits) {
        LOG(debug, "Checking docId=%d", hit.getDocId());
        if (docId == hit.getDocId()) {
            rankProgram.run(docId, hit.getMatchData());
            auto *f = retval->getFeaturesByIndex(retval->addDocId(docId));
            FefUtils::extract_feature_values(resolver, docId, f);
            return retval;
        }
    }
    return retval;
}

FeatureValues
HitCollector::get_match_features(IRankProgram& rank_program,
                                 const FeatureResolver& resolver,
                                 const search::StringStringMap& feature_rename_map) const
{
    FeatureValues match_features;
    if (resolver.num_features() == 0 || _heap.empty()) {
        return match_features;
    }
    match_features.names = FefUtils::extract_feature_names(resolver, feature_rename_map);
    match_features.values.resize(resolver.num_features() * _heap.size());
    auto f = match_features.values.data();
    for (uint32_t lid : bestLids()) {
        const Hit & hit = _hits[lid];
        auto docid = hit.getDocId();
        rank_program.run(docid, hit.getMatchData());
        FefUtils::extract_feature_values(resolver, docid, f);
        f += resolver.num_features();
    }
    assert(f == match_features.values.data() + match_features.values.size());
    return match_features;
}

} // namespace streaming

