// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "wand_parts.h"

#include <vespa/vespalib/util/priority_queue.h>

#include <atomic>
#include <limits>
#include <mutex>
#include <span>

namespace search::queryeval {

/**
 * An interface used to insert scores into an underlying heap (or similar data structure)
 * that can be shared between multiple search iterators.
 * An implementation of this interface must keep the best N scores and
 * provide the threshold score (lowest score among the best N).
 */
template <typename ScoreT>
class WeakAndHeapT {
public:
    using score_t = ScoreT;
    explicit WeakAndHeapT(uint32_t scoresToTrack) noexcept
        : _minScore((scoresToTrack == 0) ? std::numeric_limits<score_t>::max() : 0), _scoresToTrack(scoresToTrack) {}
    virtual ~WeakAndHeapT() = default;
    /**
     * Consider the given scores for insertion into the underlying structure.
     * The implementation may change the given score array to speed up execution.
     */
    virtual void adjust(std::span<score_t> newScores) = 0;

    /**
     * The number of scores this heap is tracking.
     **/
    uint32_t getScoresToTrack() const noexcept { return _scoresToTrack; }

    score_t getMinScore() const noexcept { return _minScore.load(std::memory_order_relaxed); }

protected:
    void setMinScore(score_t minScore) noexcept { _minScore.store(minScore, std::memory_order_relaxed); }

private:
    std::atomic<score_t> _minScore;
    const uint32_t       _scoresToTrack;
};

/**
 * An implementation using an underlying priority queue to keep track of the N
 * best hits that can be shared among multiple search iterators.
 */
template <typename ScoreT>
class WeakAndPriorityQueueT : public WeakAndHeapT<ScoreT> {
public:
    using score_t = ScoreT;
private:
    using Scores = vespalib::PriorityQueue<score_t>;
    Scores _bestScores;

    bool is_full() const noexcept { return (_bestScores.size() >= this->getScoresToTrack()); }

public:
    explicit WeakAndPriorityQueueT(uint32_t scoresToTrack);
    ~WeakAndPriorityQueueT() override;
    Scores& getScores() noexcept { return _bestScores; }
    void adjust(std::span<score_t> newScores) override;
    static std::unique_ptr<WeakAndPriorityQueueT> createHeap(uint32_t scoresToTrack, bool thread_safe);
    void set_min_score(score_t min_score) noexcept { this->setMinScore(min_score); }
};

template <typename ScoreT>
class SharedWeakAndPriorityQueueT final : public WeakAndPriorityQueueT<ScoreT> {
public:
    using score_t = ScoreT;
private:
    std::mutex _lock;

public:
    explicit SharedWeakAndPriorityQueueT(uint32_t scoresToTrack);
    ~SharedWeakAndPriorityQueueT() override;
    void adjust(std::span<score_t> newScores) override;
};

extern template class WeakAndHeapT<int64_t>;
extern template class WeakAndHeapT<double>;
extern template class WeakAndPriorityQueueT<int64_t>;
extern template class WeakAndPriorityQueueT<double>;
extern template class SharedWeakAndPriorityQueueT<int64_t>;
extern template class SharedWeakAndPriorityQueueT<double>;

using WeakAndPriorityQueue = WeakAndPriorityQueueT<wand::score_t>;
using SharedWeakAndPriorityQueue = SharedWeakAndPriorityQueueT<wand::score_t>;

} // namespace search::queryeval
