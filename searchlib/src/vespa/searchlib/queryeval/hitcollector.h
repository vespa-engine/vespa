// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "scores.h"
#include "sorted_hit_sequence.h"
#include <vespa/searchlib/common/hitrank.h>
#include <vespa/searchlib/common/resultset.h>
#include <vespa/vespalib/util/sort.h>
#include <algorithm>
#include <optional>
#include <vector>

namespace search::queryeval {

/**
 * This class is used to store all hits found during parallel query evaluation.
 **/
class HitCollector {
public:
    using Hit = std::pair<uint32_t, feature_t>;

private:
    enum class SortOrder { NONE, DOC_ID, HEAP };

    const uint32_t _numDocs;
    const uint32_t _maxHitsSize;
    const uint32_t _maxDocIdVectorSize;

    std::vector<Hit>            _hits;  // used as a heap when _hits.size == _maxHitsSize
    std::vector<uint32_t>       _scoreOrder; // Holds an indirection to the N best hits
    SortOrder                   _hitsSortOrder;
    bool                        _unordered;
    std::vector<uint32_t>       _docIdVector;
    std::unique_ptr<BitVector>  _bitVector;
    std::vector<Hit>            _reRankedHits;

    std::pair<Scores, Scores> _ranges;

    struct ScoreComparator {
        bool operator() (const Hit & lhs, const Hit & rhs) const noexcept {
            if (lhs.second == rhs.second) {
                return (lhs.first < rhs.first);
            }
            return (lhs.second >= rhs.second); // comparator for min-heap
        }
    };

    struct IndirectScoreComparator {
        explicit IndirectScoreComparator(const Hit * hits) noexcept : _hits(hits) { }
        bool operator() (uint32_t lhs, uint32_t rhs) const {
            if (_hits[lhs].second == _hits[rhs].second) {
                return (_hits[lhs].first < _hits[rhs].first);
            }
            return (_hits[lhs].second >= _hits[rhs].second); // operator for min-heap
        }
        const Hit * _hits;
    };

    struct IndirectScoreRadix {
        explicit IndirectScoreRadix(const Hit * hits) noexcept : _hits(hits) { }
        uint64_t operator () (uint32_t v) const noexcept {
            return vespalib::convertForSort<double, false>::convert(_hits[v].second);
        }
        const Hit * _hits;
    };
    struct DocIdRadix {
        uint32_t operator () (const Hit & v) const noexcept { return v.first; }
    };
    struct DocIdComparator {
        bool operator() (const Hit & lhs, const Hit & rhs) const noexcept {
            return (lhs.first < rhs.first);
        }
    };

    class Collector {
    public:
        using UP = std::unique_ptr<Collector>;
        virtual ~Collector() = default;
        virtual void collect(uint32_t docId, feature_t score) = 0;
        virtual bool isDocIdCollector() const noexcept { return false; }
    };

    Collector::UP _collector;

    class CollectorBase : public Collector {
    public:
        explicit CollectorBase(HitCollector &hc) noexcept : _hc(hc) { }
        void considerForHitVector(uint32_t docId, feature_t score) {
            if (__builtin_expect((score > _hc._hits[0].second), false)) {
                replaceHitInVector(docId, score);
            }
        }
    protected:
        VESPA_DLL_LOCAL void replaceHitInVector(uint32_t docId, feature_t score) noexcept;
        HitCollector &_hc;
    };

    class RankedHitCollector final : public CollectorBase {
    public:
        explicit RankedHitCollector(HitCollector &hc) noexcept : CollectorBase(hc) { }
        void collect(uint32_t docId, feature_t score) override;
        void collectAndChangeCollector(uint32_t docId, feature_t score) __attribute__((noinline));
    };

    template <bool CollectRankedHit>
    class DocIdCollector final : public CollectorBase {
    public:
        explicit DocIdCollector(HitCollector &hc) noexcept : CollectorBase(hc) { }
        void collect(uint32_t docId, feature_t score) override;
        void collectAndChangeCollector(uint32_t docId) __attribute__((noinline));
        bool isDocIdCollector() const noexcept override { return true; }
    };

    template <bool CollectRankedHit>
    class BitVectorCollector final : public CollectorBase {
    public:
        explicit BitVectorCollector(HitCollector &hc) noexcept : CollectorBase(hc) { }
        void collect(uint32_t docId, feature_t score) override;
    };

    VESPA_DLL_LOCAL void sortHitsByScore(size_t topn);
    VESPA_DLL_LOCAL void sortHitsByDocId();

    bool save_rank_scores() const noexcept { return _maxHitsSize != 0; }

public:
    HitCollector(const HitCollector &) = delete;
    HitCollector &operator=(const HitCollector &) = delete;

    /**
     * Creates a hit collector used to store hits for doc ids in the
     * range [0, numDocs>.  Doc id and rank score are stored for the n
     * (=maxHitsSize) best hits.
     *
     * @param numDocs
     * @param maxHitsSize
     **/
    HitCollector(uint32_t numDocs, uint32_t maxHitsSize);
    ~HitCollector();

    /**
     * Adds the given hit to this collector.  Stores doc id and rank
     * score if the given hit is among the n (=maxHitsSize) best hits.
     * Stores only doc id if it is not among the n best hits.
     *
     * @param docId the doc id for the hit
     * @param score the first phase rank score for the hit
     **/
    void addHit(uint32_t docId, feature_t score) {
        _collector->collect(docId, score);
    }

    /**
     * Returns a sorted sequence of hits that reference internal
     * data. The number of hits returned in the sequence is controlled
     * by the parameter and also affects how many hits need to be
     * fully sorted.
     *
     * @param max_hits maximum number of hits returned in the sequence.
     */
    SortedHitSequence getSortedHitSequence(size_t max_hits);

    const std::vector<Hit> & getReRankedHits() const { return _reRankedHits; }
    void setReRankedHits(std::vector<Hit> hits);

    const std::pair<Scores, Scores> &getRanges() const { return _ranges; }
    void setRanges(const std::pair<Scores, Scores> &ranges);

    std::unique_ptr<ResultSet>
    get_result_set(std::optional<double> second_phase_rank_drop_limit, std::vector<uint32_t>* dropped);

    /**
     * Returns a result set based on the content of this collector.
     * Invoking this method will destroy the heap property of the
     * ranked hits and the match data heap.
     *
     * @return unique pointer to the result set
     **/
    std::unique_ptr<ResultSet> getResultSet();
};

}
