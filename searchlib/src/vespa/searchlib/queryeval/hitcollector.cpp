// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hitcollector.h"
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/common/sort.h>

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
        _collector = std::make_unique<RankedHitCollector>(*this);
    } else {
        _collector = std::make_unique<DocIdCollector<false>>(*this);
    }
    _hits.reserve(maxHitsSize);
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

std::vector<HitCollector::Hit>
HitCollector::getSortedHeapHits()
{
    std::vector<Hit> scores;
    size_t scoresToReturn = std::min(_hits.size(), static_cast<size_t>(_maxReRankHitsSize));
    scores.reserve(scoresToReturn);
    sortHitsByScore(scoresToReturn);
    for (size_t i = 0; i < scoresToReturn; ++i) {
        scores.push_back(_hits[_scoreOrder[i]]);
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
    std::vector<Hit> hits;
    hits.reserve(hitsToReRank);
    for (size_t i(0); i < hitsToReRank; i++) {
        hits.push_back(_hits[_scoreOrder[i]]);
    }
    return reRank(scorer, std::move(hits));
}

size_t
HitCollector::reRank(DocumentScorer &scorer, std::vector<Hit> hits) {
    size_t hitsToReRank = hits.size();
    Scores &initScores = _ranges.first;
    Scores &finalScores = _ranges.second;
    initScores = Scores(hits.back().second, hits.front().second);
    finalScores = Scores(std::numeric_limits<feature_t>::max(),
                         -std::numeric_limits<feature_t>::max());

    std::sort(hits.begin(), hits.end()); // sort on docId
    for (auto &hit : hits) {
        hit.second = scorer.score(hit.first);
        finalScores.low = std::min(finalScores.low, hit.second);
        finalScores.high = std::max(finalScores.high, hit.second);
    }
    _reRankedHits = std::move(hits);
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
HitCollector::getResultSet(HitRank default_value)
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
                    rh[j]._rankValue = default_value;
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
                    rh[j]._rankValue = default_value;
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

}
