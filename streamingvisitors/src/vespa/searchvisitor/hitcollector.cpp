// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hitcollector.h"
#include <vespa/searchlib/fef/feature_resolver.h>
#include <vespa/searchlib/fef/utils.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <algorithm>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/vespalib/objects/nbostream.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchvisitor.hitcollector");

using search::fef::MatchData;
using vespalib::FeatureSet;
using vespalib::FeatureValues;
using vdslib::SearchResult;

using FefUtils = search::fef::Utils;

namespace streaming {

HitCollector::Hit::Hit(const vsm::StorageDocument *  doc, uint32_t docId, const search::fef::MatchData & matchData,
                       double score, const void * sortData, size_t sortDataLen) :
    _docid(docId),
    _score(score),
    _document(doc),
    _matchData(),
    _sortBlob(sortData, sortDataLen)
{
    _matchData.reserve(matchData.getNumTermFields());
    for (search::fef::TermFieldHandle handle = 0; handle < matchData.getNumTermFields(); ++handle) {
        _matchData.emplace_back(*matchData.resolveTermField(handle));
    }
}

HitCollector::Hit::~Hit() { }

HitCollector::HitCollector(size_t wantedHits) :
    _hits(),
    _sortedByDocId(true)
{
    _hits.reserve(wantedHits);
}

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
HitCollector::addHit(const vsm::StorageDocument * doc, uint32_t docId, const search::fef::MatchData & data, double score)
{
    return addHit(Hit(doc, docId, data, score));
}

bool
HitCollector::addHit(const vsm::StorageDocument * doc, uint32_t docId, const search::fef::MatchData & data,
                     double score, const void * sortData, size_t sortDataLen)
{
    return addHit(Hit(doc, docId, data, score, sortData, sortDataLen));
}

void
HitCollector::sortByDocId()
{
    if (!_sortedByDocId) {
        std::sort(_hits.begin(), _hits.end()); // sort on docId
        _sortedByDocId = true;
    }
}

bool
HitCollector::addHitToHeap(const Hit & hit) const
{
    // return true if the given hit is better than the current worst one.
    return (hit.getSortBlob().empty())
        ? (hit.cmpRank(_hits[0]) < 0)
        : (hit.cmpSort(_hits[0]) < 0);
}

bool
HitCollector::addHit(Hit && hit)
{
    bool amongTheBest(false);
    ssize_t avail = (_hits.capacity() - _hits.size());
    bool useSortBlob( ! hit.getSortBlob().empty() );
    if (avail > 1) {
        // No heap yet.
        _hits.emplace_back(std::move(hit));
        amongTheBest = true;
    } else if (_hits.capacity() == 0) {
        // this happens when wantedHitCount = 0
        // in this case we shall not put anything on the heap (which is empty)
    } else if ( avail == 0 && addHitToHeap(hit)) { // already a heap
        if (useSortBlob) {
            std::pop_heap(_hits.begin(), _hits.end(), Hit::SortComparator());
        } else {
            std::pop_heap(_hits.begin(), _hits.end(), Hit::RankComparator());
        }

        _hits.back() = std::move(hit);
        amongTheBest = true;

        if (useSortBlob) {
            std::push_heap(_hits.begin(), _hits.end(), Hit::SortComparator());
        } else {
            std::push_heap(_hits.begin(), _hits.end(), Hit::RankComparator());
        }
    } else if (avail == 1) { // make a heap of the hit vector
        _hits.emplace_back(std::move(hit));
        amongTheBest = true;
        if (useSortBlob) {
            std::make_heap(_hits.begin(), _hits.end(), Hit::SortComparator());
        } else {
            std::make_heap(_hits.begin(), _hits.end(), Hit::RankComparator());
        }
        _sortedByDocId = false; // the hit vector is no longer sorted by docId
    }
    return amongTheBest;
}

void
HitCollector::fillSearchResult(vdslib::SearchResult & searchResult, FeatureValues&& match_features)
{
    sortByDocId();
    for (const Hit & hit : _hits) {
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
HitCollector::fillSearchResult(vdslib::SearchResult & searchResult)
{
    fillSearchResult(searchResult, FeatureValues());
}

FeatureSet::SP
HitCollector::getFeatureSet(IRankProgram &rankProgram,
                            const search::fef::FeatureResolver &resolver,
                            const search::StringStringMap &feature_rename_map)
{
    if (resolver.num_features() == 0 || _hits.empty()) {
        return std::make_shared<FeatureSet>();
    }
    sortByDocId();
    auto names = FefUtils::extract_feature_names(resolver, feature_rename_map);
    FeatureSet::SP retval = std::make_shared<FeatureSet>(names, _hits.size());
    for (const Hit & hit : _hits) {
        rankProgram.run(hit.getDocId(), hit.getMatchData());
        uint32_t docId = hit.getDocId();
        auto * f = retval->getFeaturesByIndex(retval->addDocId(docId));
        FefUtils::extract_feature_values(resolver, docId, f);
    }
    return retval;
}

FeatureValues
HitCollector::get_match_features(IRankProgram& rank_program,
                                 const search::fef::FeatureResolver& resolver,
                                 const search::StringStringMap& feature_rename_map)
{
    FeatureValues match_features;
    if (resolver.num_features() == 0 || _hits.empty()) {
        return match_features;
    }
    sortByDocId();
    match_features.names = FefUtils::extract_feature_names(resolver, feature_rename_map);
    match_features.values.resize(resolver.num_features() * _hits.size());
    auto f = match_features.values.data();
    for (const Hit & hit : _hits) {
        auto docid = hit.getDocId();
        rank_program.run(docid, hit.getMatchData());
        FefUtils::extract_feature_values(resolver, docid, f);
        f += resolver.num_features();
    }
    assert(f == match_features.values.data() + match_features.values.size());
    return match_features;
}

} // namespace streaming

