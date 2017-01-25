// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hitcollector.h"
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/common/sort.h>

namespace search {
namespace queryeval {

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
           radix_sort(IndirectScoreRadix(&_hits[0]), IndirectScoreComparator(&_hits[0]), &_scoreOrder[0], _scoreOrder.size(), 16, topn);
        _scoreOrder.resize(topn);
    }
}

void
HitCollector::sortHitsByDocId()
{
    if (_hitsSortOrder != SortOrder::DOC_ID) {
        ShiftBasedRadixSorter<Hit, DocIdRadix, DocIdComparator, 24>::
           radix_sort(DocIdRadix(), DocIdComparator(), &_hits[0], _hits.size(), 16);
        _hitsSortOrder = SortOrder::DOC_ID;
        _scoreOrder.clear();
    }
}

HitCollector::HitCollector(uint32_t numDocs,
                           uint32_t maxHitsSize,
                           uint32_t maxReRankHitsSize)
    : _numDocs(numDocs),
      _maxHitsSize(maxHitsSize),
      _maxReRankHitsSize(maxReRankHitsSize),
      _maxDocIdVectorSize((numDocs + 31) / 32),
      _hits(),
      _hitsSortOrder(SortOrder::DOC_ID),
      _unordered(false),
      _docIdVector(),
      _bitVector(),
      _reRankedHits(),
      _scale(1.0),
      _adjust(0),
      _hasReRanked(false),
      _needReScore(false)
{
    if (_maxHitsSize > 0) {
        _collector.reset(new RankedHitCollector(*this));
    } else {
        _collector.reset(new DocIdCollector<false>(*this));
    }
    _hits.reserve(maxHitsSize);
}

HitCollector::~HitCollector()
{
}

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
        hc._docIdVector.push_back(docId);
        newCollector.reset(new DocIdCollector<true>(hc));
    } else {
        // start using bit vector
        hc._bitVector = BitVector::create(hc._numDocs);
        hc._bitVector->invalidateCachedCount();
        uint32_t iSize = hc._hits.size();
        for (uint32_t i = 0; i < iSize; ++i) {
            hc._bitVector->setBit(hc._hits[i].first);
        }
        hc._bitVector->setBit(docId);
        newCollector.reset(new BitVectorCollector<true>(hc));
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
    hc._collector.reset(new BitVectorCollector<CollectRankedHit>(hc)); // note - self-destruct.
}

std::vector<feature_t>
HitCollector::getSortedHeapScores()
{
    std::vector<feature_t> scores;
    size_t scoresToReturn = std::min(_hits.size(), static_cast<size_t>(_maxReRankHitsSize));
    scores.reserve(scoresToReturn);
    sortHitsByScore(scoresToReturn);
    for (size_t i = 0; i < scoresToReturn; ++i) {
        scores.push_back(_hits[_scoreOrder[i]].second);
    }
    return scores;
}

size_t
HitCollector::reRank(DocumentScorer &scorer)
{
    return reRank(scorer, _maxReRankHitsSize);
}

size_t
HitCollector::reRank(DocumentScorer &scorer, size_t count)
{
    size_t hitsToReRank = std::min(_hits.size(), count);
    if (_hasReRanked || hitsToReRank == 0) {
        return 0;
    }
    sortHitsByScore(hitsToReRank);
    _reRankedHits.reserve(_reRankedHits.size() + hitsToReRank);
    for (size_t i(0); i < hitsToReRank; i++) {
        _reRankedHits.push_back(_hits[_scoreOrder[i]]);
    }

    Scores &initScores = _ranges.first;
    Scores &finalScores = _ranges.second;
    initScores = Scores(_reRankedHits.back().second,
                        _reRankedHits.front().second);
    finalScores = Scores(std::numeric_limits<feature_t>::max(),
                         -std::numeric_limits<feature_t>::max());

    std::sort(_reRankedHits.begin(), _reRankedHits.end()); // sort on docId
    for (auto &hit : _reRankedHits) {
        hit.second = scorer.score(hit.first);
        finalScores.low = std::min(finalScores.low, hit.second);
        finalScores.high = std::max(finalScores.high, hit.second);
    }
    _hasReRanked = true;
    return hitsToReRank;
}

std::pair<Scores, Scores>
HitCollector::getRanges() const
{
    return _ranges;
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
    RankedHit *rhIter = result.getArray();
    RankedHit *rhEnd = rhIter + result.getArrayUsed();
    for (const auto &hit : hits) {
        while (rhIter != rhEnd && rhIter->_docId != hit.first) {
            // just set the iterators right
            ++rhIter;
        }
        assert(rhIter != rhEnd); // the hits should be a subset of the hits in ranked hit array.
        rhIter->_rankValue = hit.second;
    }
}

}

std::unique_ptr<ResultSet>
HitCollector::getResultSet()
{
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
        _needReScore = true;
    }

    // destroys the heap property or score sort order
    sortHitsByDocId();

    std::unique_ptr<ResultSet> rs(new ResultSet());
    if ( ! _collector->isDocIdCollector() ) {
        unsigned int iSize = _hits.size();
        rs->allocArray(iSize);
        RankedHit * rh = rs->getArray();
        if (_needReScore) {
            for (uint32_t i = 0; i < iSize; ++i) {
                rh[i]._docId = _hits[i].first;
                rh[i]._rankValue = getReScore(_hits[i].second);
            }
        } else {
            for (uint32_t i = 0; i < iSize; ++i) {
                rh[i]._docId = _hits[i].first;
                rh[i]._rankValue = _hits[i].second;
            }
        }
        rs->setArrayUsed(iSize);
    } else {
        if (_unordered) {
            std::sort(_docIdVector.begin(), _docIdVector.end());
        }
        unsigned int iSize = _hits.size();
        unsigned int jSize = _docIdVector.size();
        rs->allocArray(jSize);
        RankedHit * rh = rs->getArray();
        uint32_t i = 0;
        if (_needReScore) {
            for (uint32_t j = 0; j < jSize; ++j) {
                uint32_t docId = _docIdVector[j];
                rh[j]._docId = docId;
                if (i < iSize && docId == _hits[i].first) {
                    rh[j]._rankValue = getReScore(_hits[i].second);
                    ++i;
                } else {
                    rh[j]._rankValue = 0;
                }
            }
        } else {
            for (uint32_t j = 0; j < jSize; ++j) {
                uint32_t docId = _docIdVector[j];
                rh[j]._docId = docId;
                if (i < iSize && docId == _hits[i].first) {
                    rh[j]._rankValue = _hits[i].second;
                    ++i;
                } else {
                    rh[j]._rankValue = 0;
                }
            }
        }
        rs->setArrayUsed(jSize);
    }

    if (_hasReRanked) {
        mergeHitsIntoResultSet(_reRankedHits, *rs.get());
    }

    if (_bitVector != NULL) {
        rs->setBitOverflow(std::move(_bitVector));
    }

    return rs;
}

} // namespace queryeval
} // namespace search
