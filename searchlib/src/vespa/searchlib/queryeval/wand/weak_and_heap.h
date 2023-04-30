// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "wand_parts.h"
#include <vespa/vespalib/util/priority_queue.h>
#include <atomic>
#include <mutex>

namespace search::queryeval {

/**
 * An interface used to insert scores into an underlying heap (or similar data structure)
 * that can be shared between multiple search iterators.
 * An implementation of this interface must keep the best N scores and
 * provide the threshold score (lowest score among the best N).
 */
class WeakAndHeap {
public:
    using score_t = wand::score_t;
    WeakAndHeap(uint32_t scoresToTrack) :
       _minScore((scoresToTrack == 0)
                    ? std::numeric_limits<score_t>::max()
                    : 0),
       _scoresToTrack(scoresToTrack)
    { }
    virtual ~WeakAndHeap() {}
    /**
     * Consider the given scores for insertion into the underlying structure.
     * The implementation may change the given score array to speed up execution.
     */
    virtual void adjust(score_t *begin, score_t *end) = 0;

    /**
     * The number of scores this heap is tracking.
     **/
    uint32_t getScoresToTrack() const { return _scoresToTrack; }

    score_t getMinScore() const { return _minScore.load(std::memory_order_relaxed); }
protected:
    void setMinScore(score_t minScore) { _minScore.store(minScore, std::memory_order_relaxed); }
private:
    std::atomic<score_t> _minScore;
    const uint32_t _scoresToTrack;
};

/**
 * An implementation using an underlying priority queue to keep track of the N
 * best hits that can be shared among multiple search iterators.
 */
class SharedWeakAndPriorityQueue : public WeakAndHeap
{
private:
    using Scores = vespalib::PriorityQueue<score_t>;
    Scores         _bestScores;
    std::mutex     _lock;

    bool is_full() const { return (_bestScores.size() >= getScoresToTrack()); }

public:
    SharedWeakAndPriorityQueue(uint32_t scoresToTrack);
    ~SharedWeakAndPriorityQueue();
    Scores &getScores() { return _bestScores; }
    void adjust(score_t *begin, score_t *end) override;
};

}
