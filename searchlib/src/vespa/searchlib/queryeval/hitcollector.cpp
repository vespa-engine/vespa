// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hitcollector.h"
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/common/sort.h>
#include <cassert>

namespace search::queryeval {

void
HitCollector::sortHitsByScore(size_t topn)
{
    topn = std::min(topn, _hits.size());
    if (topn > _scoreOrder.size()) {
        _scoreOrder.clear();
        _scoreOrder.reserve(_hits.size());
        for (size_t i(0); i < _hits.size(); i++) {
            _scoreOrder.push_back(i);
        }
        ShiftBasedRadixSorter<uint32_t, IndirectScoreRadix, IndirectScoreComparator, 56, true>::
            radix_sort(IndirectScoreRadix(_hits.data()), IndirectScoreComparator(_hits.data()), _scoreOrder.data(), _scoreOrder.size(), 16, topn);
        _scoreOrder.resize(topn);
    }
}

void
HitCollector::sortHitsByDocId()
{
    if (_hitsSortOrder != SortOrder::DOC_ID) {
        ShiftBasedRadixSorter<Hit, DocIdRadix, DocIdComparator, 24>::
            radix_sort(DocIdRadix(), DocIdComparator(), _hits.data(), _hits.size(), 16);
        _hitsSortOrder = SortOrder::DOC_ID;
        _scoreOrder.clear();
    }
}

HitCollector::HitCollector(uint32_t numDocs,
                           uint32_t maxHitsSize)
    : _numDocs(numDocs),
      _maxHitsSize(std::min(maxHitsSize, numDocs)),
      _maxDocIdVectorSize((numDocs + 31) / 32),
      _hits(),
      _hitsSortOrder(SortOrder::DOC_ID),
      _unordered(false),
      _docIdVector(),
      _bitVector(),
      _reRankedHits(),
      _scale(1.0),
      _adjust(0)
{
    if (_maxHitsSize > 0) {
        _collector = std::make_unique<RankedHitCollector>(*this);
    } else {
        _collector = std::make_unique<DocIdCollector<false>>(*this);
    }
    _hits.reserve(_maxHitsSize);
}

HitCollector::~HitCollector() = default;

void
HitCollector::RankedHitCollector::collect(uint32_t docId, feature_t score)
{
    HitCollector & hc = this->_hc;
    if (hc._hits.size() < hc._maxHitsSize) {
        if (__builtin_expect(((hc._hits.size() > 0) &&
                              (docId < hc._hits.back().first) &&
                              (hc._hitsSortOrder == SortOrder::DOC_ID)), false))
        {
            hc._hitsSortOrder = SortOrder::NONE;
            hc._unordered = true;
        }
        hc._hits.push_back(std::make_pair(docId, score));
    } else {
        collectAndChangeCollector(docId, score);
    }
}

template <bool CollectRankedHit>
void
HitCollector::BitVectorCollector<CollectRankedHit>::collect(uint32_t docId, feature_t score) {
    this->_hc._bitVector->setBit(docId);
    if (CollectRankedHit) {
        this->considerForHitVector(docId, score);
    }
}

void
HitCollector::CollectorBase::replaceHitInVector(uint32_t docId, feature_t score) {
    // replace lowest scored hit in hit vector
    std::pop_heap(_hc._hits.begin(), _hc._hits.end(), ScoreComparator());
    _hc._hits.back().first = docId;
    _hc._hits.back().second = score;
    std::push_heap(_hc._hits.begin(), _hc._hits.end(), ScoreComparator());
}

void
HitCollector::RankedHitCollector::collectAndChangeCollector(uint32_t docId, feature_t score)
{
    HitCollector & hc = this->_hc;
    Collector::UP newCollector;
    if (hc._maxDocIdVectorSize > hc._maxHitsSize) {
        // start using docid vector
        hc._docIdVector.reserve(hc._maxDocIdVectorSize);
        uint32_t iSize = hc._hits.size();
        for (uint32_t i = 0; i < iSize; ++i) {
            hc._docIdVector.push_back(hc._hits[i].first);
        }
        if ((iSize > 0) && (docId < hc._docIdVector.back())) {
            hc._unordered = true;
        }
        hc._docIdVector.push_back(docId);
        newCollector = std::make_unique<DocIdCollector<true>>(hc);
    } else {
        // start using bit vector
        hc._bitVector = BitVector::create(hc._numDocs);
        hc._bitVector->invalidateCachedCount();
        uint32_t iSize = hc._hits.size();
        for (uint32_t i = 0; i < iSize; ++i) {
            hc._bitVector->setBit(hc._hits[i].first);
        }
        hc._bitVector->setBit(docId);
        newCollector = std::make_unique<BitVectorCollector<true>>(hc);
    }
    // treat hit vector as a heap
    std::make_heap(hc._hits.begin(), hc._hits.end(), ScoreComparator());
    hc._hitsSortOrder = SortOrder::HEAP;
    this->considerForHitVector(docId, score);
    hc._collector = std::move(newCollector);
}

template<bool CollectRankedHit>
void
HitCollector::DocIdCollector<CollectRankedHit>::collect(uint32_t docId, feature_t score)
{
    if (CollectRankedHit) {
        this->considerForHitVector(docId, score);
    }
    HitCollector & hc = this->_hc;
    if (hc._docIdVector.size() < hc._maxDocIdVectorSize) {
        if (__builtin_expect(((hc._docIdVector.size() > 0) &&
                              (docId < hc._docIdVector.back()) &&
                              (hc._unordered == false)), false))
        {
            hc._unordered = true;
        }
        hc._docIdVector.push_back(docId);
    } else {
        collectAndChangeCollector(docId);
    }
}

template<bool CollectRankedHit>
void
HitCollector::DocIdCollector<CollectRankedHit>::collectAndChangeCollector(uint32_t docId)
{
    HitCollector & hc = this->_hc;
    // start using bit vector instead of docid array.
    hc._bitVector = BitVector::create(hc._numDocs);
    hc._bitVector->invalidateCachedCount();
    uint32_t iSize = static_cast<uint32_t>(hc._docIdVector.size());
    for (uint32_t i = 0; i < iSize; ++i) {
        hc._bitVector->setBit(hc._docIdVector[i]);
    }
    std::vector<uint32_t> emptyVector;
    emptyVector.swap(hc._docIdVector);
    hc._bitVector->setBit(docId);
    hc._collector = std::make_unique<BitVectorCollector<CollectRankedHit>>(hc); // note - self-destruct.
}

SortedHitSequence
HitCollector::getSortedHitSequence(size_t max_hits)
{
    size_t num_hits = std::min(_hits.size(), max_hits);
    sortHitsByScore(num_hits);
    return SortedHitSequence(_hits.data(), _scoreOrder.data(), num_hits);
}

void
HitCollector::setReRankedHits(std::vector<Hit> hits)
{
    auto sort_on_docid = [](const Hit &a, const Hit &b){ return (a.first < b.first); };
    std::sort(hits.begin(), hits.end(), sort_on_docid);
    _reRankedHits = std::move(hits);
}

void
HitCollector::setRanges(const std::pair<Scores, Scores> &ranges)
{
    _ranges = ranges;
}

namespace {

void
mergeHitsIntoResultSet(const std::vector<HitCollector::Hit> &hits, ResultSet &result)
{
    uint32_t rhCur(0);
    uint32_t rhEnd(result.getArrayUsed());
    for (const auto &hit : hits) {
        while (rhCur != rhEnd && result[rhCur].getDocId() != hit.first) {
            // just set the iterators right
            ++rhCur;
        }
        assert(rhCur != rhEnd); // the hits should be a subset of the hits in ranked hit array.
        result[rhCur]._rankValue = hit.second;
    }
}

}

std::unique_ptr<ResultSet>
HitCollector::getResultSet(HitRank default_value)
{
    bool needReScore = false;
    Scores &initHeapScores = _ranges.first;
    Scores &finalHeapScores = _ranges.second;
    if (initHeapScores.low > finalHeapScores.low) {
        // scale and adjust the score according to the range
        // of the initial and final heap score values to avoid that
        // a score from the first phase is larger than finalHeapScores.low
        feature_t initRange = initHeapScores.high - initHeapScores.low;
        if (initRange < 1.0) initRange = 1.0f;
        feature_t finalRange = finalHeapScores.high - finalHeapScores.low;
        if (finalRange < 1.0) finalRange = 1.0f;
        _scale = finalRange / initRange;
        _adjust = initHeapScores.low * _scale - finalHeapScores.low;
        needReScore = true;
    }

    // destroys the heap property or score sort order
    sortHitsByDocId();

    auto rs = std::make_unique<ResultSet>();
    if ( ! _collector->isDocIdCollector() ) {
        unsigned int iSize = _hits.size();
        rs->allocArray(iSize);
        if (needReScore) {
            for (uint32_t i = 0; i < iSize; ++i) {
                rs->push_back(RankedHit(_hits[i].first, getReScore(_hits[i].second)));
            }
        } else {
            for (uint32_t i = 0; i < iSize; ++i) {
                rs->push_back(RankedHit(_hits[i].first, _hits[i].second));
            }
        }
    } else {
        if (_unordered) {
            std::sort(_docIdVector.begin(), _docIdVector.end());
        }
        unsigned int iSize = _hits.size();
        unsigned int jSize = _docIdVector.size();
        rs->allocArray(jSize);
        uint32_t i = 0;
        if (needReScore) {
            for (uint32_t j = 0; j < jSize; ++j) {
                uint32_t docId = _docIdVector[j];
                if (i < iSize && docId == _hits[i].first) {
                    rs->push_back(RankedHit(docId, getReScore(_hits[i].second)));
                    ++i;
                } else {
                    rs->push_back(RankedHit(docId, default_value));
                }
            }
        } else {
            for (uint32_t j = 0; j < jSize; ++j) {
                uint32_t docId = _docIdVector[j];
                if (i < iSize && docId == _hits[i].first) {
                    rs->push_back(RankedHit(docId, _hits[i].second));
                    ++i;
                } else {
                    rs->push_back(RankedHit(docId, default_value));
                }
            }
        }
    }

    if (!_reRankedHits.empty()) {
        mergeHitsIntoResultSet(_reRankedHits, *rs);
    }

    if (_bitVector) {
        rs->setBitOverflow(std::move(_bitVector));
    }

    return rs;
}

}
