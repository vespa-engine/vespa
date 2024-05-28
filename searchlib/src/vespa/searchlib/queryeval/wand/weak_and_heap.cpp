// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "weak_and_heap.h"

namespace search::queryeval {

WeakAndPriorityQueue::WeakAndPriorityQueue(uint32_t scoresToTrack) :
    WeakAndHeap(scoresToTrack),
    _bestScores()
{ }

WeakAndPriorityQueue::~WeakAndPriorityQueue() = default;

std::unique_ptr<WeakAndPriorityQueue>
WeakAndPriorityQueue::createHeap(uint32_t scoresToTrack, bool thread_safe) {
    if (thread_safe) {
        return std::make_unique<queryeval::SharedWeakAndPriorityQueue>(scoresToTrack);
    }
    return std::make_unique<WeakAndPriorityQueue>(scoresToTrack);
}

void
WeakAndPriorityQueue::adjust(score_t *begin, score_t *end)
{
    if (getScoresToTrack() == 0) {
        return;
    }

    for (score_t *itr = begin; itr != end; ++itr) {
        score_t score = *itr;
        if (!is_full()) {
            _bestScores.push(score);
        } else if (_bestScores.front() < score) {
            _bestScores.push(score);
            _bestScores.pop_front();
        }
    }
    if (is_full()) {
        setMinScore(_bestScores.front());
    }
}

SharedWeakAndPriorityQueue::SharedWeakAndPriorityQueue(uint32_t scoresToTrack)
    : WeakAndPriorityQueue(scoresToTrack),
      _lock()
{ }

SharedWeakAndPriorityQueue::~SharedWeakAndPriorityQueue() = default;

void
SharedWeakAndPriorityQueue::adjust(score_t *begin, score_t *end) {
    std::lock_guard guard(_lock);
    WeakAndPriorityQueue::adjust(begin, end);
}

}
