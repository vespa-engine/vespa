// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hitcollector.h"
#include <vespa/searchlib/fef/feature_resolver.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <algorithm>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/vespalib/objects/nbostream.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchvisitor.hitcollector");

using search::FeatureSet;
using search::fef::MatchData;
using vdslib::SearchResult;

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
HitCollector::fillSearchResult(vdslib::SearchResult & searchResult)
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
}

FeatureSet::SP
HitCollector::getFeatureSet(IRankProgram &rankProgram,
                            const search::fef::FeatureResolver &resolver)
{
    if (resolver.num_features() == 0 || _hits.empty()) {
        return FeatureSet::SP(new FeatureSet());
    }
    sortByDocId();
    std::vector<vespalib::string> names;
    names.reserve(resolver.num_features());
    for (size_t i = 0; i < resolver.num_features(); ++i) {
        names.emplace_back(resolver.name_of(i));
    }
    FeatureSet::SP retval = FeatureSet::SP(new FeatureSet(names, _hits.size()));
    for (const Hit & hit : _hits) {
        rankProgram.run(hit.getDocId(), hit.getMatchData());
        uint32_t docId = hit.getDocId();
        auto * f = retval->getFeaturesByIndex(retval->addDocId(docId));
        for (uint32_t j = 0; j < names.size(); ++j) {
            if (resolver.is_object(j)) {
                auto obj = resolver.resolve(j).as_object(docId);
                if (! obj.get().type().is_double()) {
                    vespalib::nbostream buf;
                    encode_value(obj.get(), buf);
                    f[j].set_data(vespalib::Memory(buf.peek(), buf.size()));
                } else {
                    f[j].set_double(obj.get().as_double());
                }
            } else {
                f[j].set_double(resolver.resolve(j).as_number(docId));
            }
            LOG(debug, "getFeatureSet: lDocId(%u), '%s': %f %s", docId, names[j].c_str(), f[j].as_double(),
                f[j].is_data() ? "[tensor]" : "");
        }
    }
    return retval;
}

} // namespace streaming

