// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "weak_and_heap.h"

namespace search::queryeval {

SharedWeakAndPriorityQueue::SharedWeakAndPriorityQueue(uint32_t scoresToTrack) :
    WeakAndHeap(scoresToTrack),
    _bestScores(),
    _lock()
{
    _bestScores.reserve(scoresToTrack);
}

SharedWeakAndPriorityQueue::~SharedWeakAndPriorityQueue() = default;

void
SharedWeakAndPriorityQueue::adjust(score_t *begin, score_t *end)
{
    if (getScoresToTrack() == 0) {
        return;
    }
    std::lock_guard guard(_lock);
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

}
