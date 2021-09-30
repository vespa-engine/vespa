// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "scores.h"
#include <vespa/searchlib/common/hitrank.h>
#include <vespa/searchlib/common/resultset.h>
#include <algorithm>
#include <vector>
#include <vespa/vespalib/util/sort.h>
#include <vespa/fastos/dynamiclibrary.h>
#include "sorted_hit_sequence.h"

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
    feature_t _scale;
    feature_t _adjust;

    struct ScoreComparator {
        bool operator() (const Hit & lhs, const Hit & rhs) const {
            if (lhs.second == rhs.second) {
                return (lhs.first < rhs.first);
            }
            return (lhs.second >= rhs.second); // comparator for min-heap
        }
    };

    struct IndirectScoreComparator {
        IndirectScoreComparator(const Hit * hits) : _hits(hits) { }
        bool operator() (uint32_t lhs, uint32_t rhs) const {
            if (_hits[lhs].second == _hits[rhs].second) {
                return (_hits[lhs].first < _hits[rhs].first);
            }
            return (_hits[lhs].second >= _hits[rhs].second); // operator for min-heap
        }
        const Hit * _hits;
    };

    struct IndirectScoreRadix {
        IndirectScoreRadix(const Hit * hits) : _hits(hits) { }
        uint64_t operator () (uint32_t v) {
            return vespalib::convertForSort<double, false>::convert(_hits[v].second);
        }
        const Hit * _hits;
    };
    struct DocIdRadix {
        uint32_t operator () (const Hit & v) { return v.first; }
    };
    struct DocIdComparator {
        bool operator() (const Hit & lhs, const Hit & rhs) const {
            return (lhs.first < rhs.first);
        }
    };

    class Collector {
    public:
        typedef std::unique_ptr<Collector> UP;
        virtual ~Collector() {}
        virtual void collect(uint32_t docId, feature_t score) = 0;
        virtual bool isRankedHitCollector() const { return false; }
        virtual bool isDocIdCollector() const { return false; }
    };

    Collector::UP _collector;

    class CollectorBase : public Collector {
    public:
        CollectorBase(HitCollector &hc) : _hc(hc) { }
        void considerForHitVector(uint32_t docId, feature_t score) {
            if (__builtin_expect((score > _hc._hits[0].second), false)) {
                replaceHitInVector(docId, score);
            }
        }
    protected:
        void replaceHitInVector(uint32_t docId, feature_t score);
        HitCollector &_hc;
    };

    class RankedHitCollector : public CollectorBase {
    public:
        RankedHitCollector(HitCollector &hc) : CollectorBase(hc) { }
        void collect(uint32_t docId, feature_t score) override;
        void collectAndChangeCollector(uint32_t docId, feature_t score) __attribute__((noinline));
        bool isRankedHitCollector() const override { return true; }
    };

    template <bool CollectRankedHit>
    class DocIdCollector : public CollectorBase {
    public:
        DocIdCollector(HitCollector &hc) : CollectorBase(hc) { }
        void collect(uint32_t docId, feature_t score) override;
        void collectAndChangeCollector(uint32_t docId) __attribute__((noinline));
        bool isDocIdCollector() const override { return true; }
    };

    template <bool CollectRankedHit>
    class BitVectorCollector : public CollectorBase {
    public:
        BitVectorCollector(HitCollector &hc) : CollectorBase(hc) { }
        virtual void collect(uint32_t docId, feature_t score) override;
    };
    
    HitRank getReScore(feature_t score) const {
        return ((score * _scale) - _adjust);
    }
    VESPA_DLL_LOCAL void sortHitsByScore(size_t topn);
    VESPA_DLL_LOCAL void sortHitsByDocId();

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

    /**
     * Returns a result set based on the content of this collector.
     * Invoking this method will destroy the heap property of the
     * ranked hits and the match data heap.
     *
     * @param auto pointer to the result set
     * @param default_value rank value to be used for results without rank value
     **/
    std::unique_ptr<ResultSet> getResultSet(HitRank default_value = default_rank_value);
};

}
