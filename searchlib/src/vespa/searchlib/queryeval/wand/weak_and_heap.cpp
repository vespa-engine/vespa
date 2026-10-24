// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "weak_and_heap.h"

namespace search::queryeval {

template <typename ScoreT>
WeakAndPriorityQueueT<ScoreT>::WeakAndPriorityQueueT(uint32_t scoresToTrack)
    : WeakAndHeapT<ScoreT>(scoresToTrack), _bestScores() {
}

template <typename ScoreT>
WeakAndPriorityQueueT<ScoreT>::~WeakAndPriorityQueueT() = default;

template <typename ScoreT>
std::unique_ptr<WeakAndPriorityQueueT<ScoreT>>
WeakAndPriorityQueueT<ScoreT>::createHeap(uint32_t scoresToTrack, bool thread_safe) {
    if (thread_safe) {
        return std::make_unique<SharedWeakAndPriorityQueueT<ScoreT>>(scoresToTrack);
    }
    return std::make_unique<WeakAndPriorityQueueT<ScoreT>>(scoresToTrack);
}

template <typename ScoreT>
void WeakAndPriorityQueueT<ScoreT>::adjust(std::span<score_t> newScores) {
    if (this->getScoresToTrack() == 0) {
        return;
    }

    for (score_t score : newScores) {
        if (!is_full()) {
            _bestScores.push(score);
        } else if (_bestScores.front() < score) {
            _bestScores.push(score);
            _bestScores.pop_front();
        }
    }
    if (is_full()) {
        this->setMinScore(_bestScores.front());
    }
}

template <typename ScoreT>
SharedWeakAndPriorityQueueT<ScoreT>::SharedWeakAndPriorityQueueT(uint32_t scoresToTrack)
    : WeakAndPriorityQueueT<ScoreT>(scoresToTrack), _lock() {
}

template <typename ScoreT>
SharedWeakAndPriorityQueueT<ScoreT>::~SharedWeakAndPriorityQueueT() = default;

template <typename ScoreT>
void SharedWeakAndPriorityQueueT<ScoreT>::adjust(std::span<score_t> newScores) {
    std::lock_guard guard(_lock);
    WeakAndPriorityQueueT<ScoreT>::adjust(newScores);
}

template class WeakAndHeapT<int64_t>;
template class WeakAndHeapT<double>;
template class WeakAndPriorityQueueT<int64_t>;
template class WeakAndPriorityQueueT<double>;
template class SharedWeakAndPriorityQueueT<int64_t>;
template class SharedWeakAndPriorityQueueT<double>;

} // namespace search::queryeval
