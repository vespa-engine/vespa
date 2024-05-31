// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hitcollector.h"
#include "first_phase_rescorer.h"
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

HitCollector::HitCollector(uint32_t numDocs, uint32_t maxHitsSize)
    : _numDocs(numDocs),
      _maxHitsSize(std::min(maxHitsSize, numDocs)),
      _maxDocIdVectorSize((numDocs + 31) / 32),
      _hits(),
      _hitsSortOrder(SortOrder::DOC_ID),
      _unordered(false),
      _docIdVector(),
      _bitVector(),
      _reRankedHits()
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
        if (__builtin_expect(((!hc._hits.empty()) &&
                              (docId < hc._hits.back().first) &&
                              (hc._hitsSortOrder == SortOrder::DOC_ID)), false))
        {
            hc._hitsSortOrder = SortOrder::NONE;
            hc._unordered = true;
        }
        hc._hits.emplace_back(docId, score);
    } else {
        collectAndChangeCollector(docId, score); // note - self-destruct.
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
HitCollector::CollectorBase::replaceHitInVector(uint32_t docId, feature_t score) noexcept {
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
        for (const auto& hit : hc._hits) {
            hc._docIdVector.push_back(hit.first);
        }
        if (!hc._docIdVector.empty() && (docId < hc._docIdVector.back())) {
            hc._unordered = true;
        }
        hc._docIdVector.push_back(docId);
        newCollector = std::make_unique<DocIdCollector<true>>(hc);
    } else {
        // start using bit vector
        hc._bitVector = BitVector::create(hc._numDocs);
        hc._bitVector->invalidateCachedCount();
        for (const auto& hit : _hc._hits) {
            hc._bitVector->setBit(hit.first);
        }
        hc._bitVector->setBit(docId);
        newCollector = std::make_unique<BitVectorCollector<true>>(hc);
    }
    // treat hit vector as a heap
    std::make_heap(hc._hits.begin(), hc._hits.end(), ScoreComparator());
    hc._hitsSortOrder = SortOrder::HEAP;
    this->considerForHitVector(docId, score);
    hc._collector = std::move(newCollector); // note - self-destruct.
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
        if (__builtin_expect(((!hc._docIdVector.empty()) &&
                              (docId < hc._docIdVector.back()) &&
                              (hc._unordered == false)), false))
        {
            hc._unordered = true;
        }
        hc._docIdVector.push_back(docId);
    } else {
        collectAndChangeCollector(docId); // note - self-destruct.
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
    for (auto docid : hc._docIdVector) {
        hc._bitVector->setBit(docid);
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
    return {_hits.data(), _scoreOrder.data(), num_hits};
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

struct NoRescorer
{
    static double rescore(uint32_t, double score) noexcept { return score; }
};

template <typename Rescorer>
class RerankRescorer {
    Rescorer _rescorer;
    using HitVector = std::vector<HitCollector::Hit>;
    using Iterator = typename HitVector::const_iterator;
    Iterator _reranked_cur;
    Iterator _reranked_end;
public:
    RerankRescorer(const Rescorer& rescorer,
                   const HitVector& reranked_hits)
        : _rescorer(rescorer),
          _reranked_cur(reranked_hits.begin()),
          _reranked_end(reranked_hits.end())
    {
    }

    double rescore(uint32_t docid, double score) noexcept {
        if (_reranked_cur != _reranked_end && _reranked_cur->first == docid) {
            double result = _reranked_cur->second;
            ++_reranked_cur;
            return result;
        } else {
            return _rescorer.rescore(docid, score);
        }
    }
};

class SimpleHitAdder {
protected:
    ResultSet& _rs;
public:
    SimpleHitAdder(ResultSet& rs)
        : _rs(rs)
    {
    }
    void add(uint32_t docid, double rank_value) {
        _rs.push_back({docid, rank_value});
    }
};

class ConditionalHitAdder : public SimpleHitAdder {
protected:
    double _second_phase_rank_drop_limit;
public:
    ConditionalHitAdder(ResultSet& rs, double second_phase_rank_drop_limit)
        : SimpleHitAdder(rs),
          _second_phase_rank_drop_limit(second_phase_rank_drop_limit)
    {
    }
    void add(uint32_t docid, double rank_value) {
        if (rank_value > _second_phase_rank_drop_limit) {
            _rs.push_back({docid, rank_value});
        }
    }
};

class TrackingConditionalHitAdder : public ConditionalHitAdder {
    std::vector<uint32_t>& _dropped;
public:
    TrackingConditionalHitAdder(ResultSet& rs, double second_phase_rank_drop_limit, std::vector<uint32_t>& dropped)
        : ConditionalHitAdder(rs, second_phase_rank_drop_limit),
          _dropped(dropped)
    {
    }
    void add(uint32_t docid, double rank_value) {
        if (rank_value > _second_phase_rank_drop_limit) {
            _rs.push_back({docid, rank_value});
        } else {
            _dropped.emplace_back(docid);
        }
    }
};

template <typename HitAdder, typename Rescorer>
void
add_rescored_hits(HitAdder hit_adder, const std::vector<HitCollector::Hit>& hits, Rescorer rescorer)
{
    for (auto& hit : hits) {
        hit_adder.add(hit.first, rescorer.rescore(hit.first, hit.second));
    }
}

template <typename HitAdder, typename Rescorer>
void
add_rescored_hits(HitAdder hit_adder, const std::vector<HitCollector::Hit>& hits, const std::vector<HitCollector::Hit>& reranked_hits, Rescorer rescorer)
{
    if (reranked_hits.empty()) {
        add_rescored_hits(hit_adder, hits, rescorer);
    } else {
        add_rescored_hits(hit_adder, hits, RerankRescorer(rescorer, reranked_hits));
    }
}

template <typename Rescorer>
void
add_rescored_hits(ResultSet& rs, const std::vector<HitCollector::Hit>& hits, const std::vector<HitCollector::Hit>& reranked_hits, std::optional<double> second_phase_rank_drop_limit, std::vector<uint32_t>* dropped, Rescorer rescorer)
{
    if (second_phase_rank_drop_limit.has_value()) {
        if (dropped != nullptr) {
            add_rescored_hits(TrackingConditionalHitAdder(rs, second_phase_rank_drop_limit.value(), *dropped), hits, reranked_hits, rescorer);
        } else {
            add_rescored_hits(ConditionalHitAdder(rs, second_phase_rank_drop_limit.value()), hits, reranked_hits, rescorer);
        }
    } else {
        add_rescored_hits(SimpleHitAdder(rs), hits, reranked_hits, rescorer);
    }
}

template <typename HitAdder, typename Rescorer>
void
mixin_rescored_hits(HitAdder hit_adder, const std::vector<HitCollector::Hit>& hits, const std::vector<uint32_t>& docids, double default_value, Rescorer rescorer)
{
    auto hits_cur = hits.begin();
    auto hits_end = hits.end();
    for (auto docid : docids) {
        if (hits_cur != hits_end && docid == hits_cur->first) {
            hit_adder.add(docid, rescorer.rescore(docid, hits_cur->second));
            ++hits_cur;
        } else {
            hit_adder.add(docid, default_value);
        }
    }
}

template <typename HitAdder, typename Rescorer>
void
mixin_rescored_hits(HitAdder hit_adder, const std::vector<HitCollector::Hit>& hits, const std::vector<uint32_t>& docids, double default_value, const std::vector<HitCollector::Hit>& reranked_hits, Rescorer rescorer)
{
    if (reranked_hits.empty()) {
        mixin_rescored_hits(hit_adder, hits, docids, default_value, rescorer);
    } else {
        mixin_rescored_hits(hit_adder, hits, docids, default_value, RerankRescorer(rescorer, reranked_hits));
    }
}

template <typename Rescorer>
void
mixin_rescored_hits(ResultSet& rs, const std::vector<HitCollector::Hit>& hits, const std::vector<uint32_t>& docids, double default_value, const std::vector<HitCollector::Hit>& reranked_hits, std::optional<double> second_phase_rank_drop_limit, std::vector<uint32_t>* dropped, Rescorer rescorer)
{
    if (second_phase_rank_drop_limit.has_value()) {
        if (dropped != nullptr) {
            mixin_rescored_hits(TrackingConditionalHitAdder(rs, second_phase_rank_drop_limit.value(), *dropped), hits, docids, default_value, reranked_hits, rescorer);
        } else {
            mixin_rescored_hits(ConditionalHitAdder(rs, second_phase_rank_drop_limit.value()), hits, docids, default_value, reranked_hits, rescorer);
        }
    } else {
        mixin_rescored_hits(SimpleHitAdder(rs), hits, docids, default_value, reranked_hits, rescorer);
    }
}

void
add_bitvector_to_dropped(std::vector<uint32_t>& dropped, vespalib::ConstArrayRef<RankedHit> hits, const BitVector& bv)
{
    auto hits_cur = hits.begin();
    auto hits_end = hits.end();
    auto docid = bv.getFirstTrueBit();
    auto docid_limit = bv.size();
    while (docid < docid_limit) {
        if (hits_cur != hits_end && hits_cur->getDocId() == docid) {
            ++hits_cur;
        } else {
            dropped.emplace_back(docid);
        }
        docid = bv.getNextTrueBit(docid + 1);
    }
}

void
clear_dropped_from_bitvector(BitVector& bv, vespalib::ConstArrayRef<uint32_t> dropped)
{
    for (auto docid : dropped) {
        bv.clearBit(docid);
    }
}

}

std::unique_ptr<ResultSet>
HitCollector::getResultSet(HitRank default_value, std::optional<double> second_phase_rank_drop_limit, std::vector<uint32_t>* dropped)
{
    bool needReScore = FirstPhaseRescorer::need_rescore(_ranges);
    FirstPhaseRescorer rescorer(_ranges);

    bool drop_default_value = (second_phase_rank_drop_limit.has_value() &&
                               !(default_value > second_phase_rank_drop_limit.value()));

    std::vector<uint32_t> fallback_dropped;
    if (dropped == nullptr) {
        if (second_phase_rank_drop_limit.has_value() && !drop_default_value && _bitVector) {
            dropped = &fallback_dropped;
        }
    } else {
        dropped->clear();
    }

    // destroys the heap property or score sort order
    sortHitsByDocId();

    auto rs = std::make_unique<ResultSet>();
    if ( ! _collector->isDocIdCollector() ||
        (drop_default_value && (_bitVector || dropped == nullptr))) {
        rs->allocArray(_hits.size());
        auto* dropped_or_null = dropped;
        if (drop_default_value && _bitVector) {
            dropped_or_null = nullptr;
        }
        if (needReScore) {
            add_rescored_hits(*rs, _hits, _reRankedHits, second_phase_rank_drop_limit, dropped_or_null, rescorer);
        } else {
            add_rescored_hits(*rs, _hits, _reRankedHits, second_phase_rank_drop_limit, dropped_or_null, NoRescorer());
        }
    } else {
        if (_unordered) {
            std::sort(_docIdVector.begin(), _docIdVector.end());
        }
        rs->allocArray(_docIdVector.size());
        if (needReScore) {
            mixin_rescored_hits(*rs, _hits, _docIdVector, default_value, _reRankedHits, second_phase_rank_drop_limit, dropped, rescorer);
        } else {
            mixin_rescored_hits(*rs, _hits, _docIdVector, default_value, _reRankedHits, second_phase_rank_drop_limit, dropped, NoRescorer());
        }
    }

    if (drop_default_value && _bitVector) {
        if (dropped != nullptr) {
            assert(dropped->empty());
            add_bitvector_to_dropped(*dropped, {rs->getArray(), rs->getArrayUsed()}, *_bitVector);
        }
        _bitVector.reset();
    }

    if (_bitVector) {
        assert(!drop_default_value);
        if (second_phase_rank_drop_limit.has_value() && dropped != nullptr && !dropped->empty()) {
            clear_dropped_from_bitvector(*_bitVector, *dropped);
        }
        rs->setBitOverflow(std::move(_bitVector));
    }

    return rs;
}

std::unique_ptr<ResultSet>
HitCollector::getResultSet(HitRank default_value)
{
    return getResultSet(default_value, std::nullopt, nullptr);
}

}
