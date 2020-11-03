// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "match_loop_communicator.h"
#include <vespa/vespalib/util/priority_queue.h>

namespace proton:: matching {

MatchLoopCommunicator::MatchLoopCommunicator(size_t threads, size_t topN)
    : MatchLoopCommunicator(threads, topN, std::unique_ptr<IDiversifier>())
{}
MatchLoopCommunicator::MatchLoopCommunicator(size_t threads, size_t topN, std::unique_ptr<IDiversifier> diversifier)
    : _best_scores(),
      _best_dropped(),
      _estimate_match_frequency(threads),
      _get_second_phase_work(threads, topN, _best_scores, _best_dropped, std::move(diversifier)),
      _complete_second_phase(threads, topN, _best_scores, _best_dropped)
{}
MatchLoopCommunicator::~MatchLoopCommunicator() = default;

void
MatchLoopCommunicator::EstimateMatchFrequency::mingle()
{
    double freqSum = 0.0;
    for (size_t i = 0; i < size(); ++i) {
        if (in(i).docs > 0) {
            double h = in(i).hits;
            double d = in(i).docs;
            freqSum += h/d;
        }
    }
    double freq = freqSum / size();
    for (size_t i = 0; i < size(); ++i) {
        out(i) = freq;
    }
}

MatchLoopCommunicator::GetSecondPhaseWork::GetSecondPhaseWork(size_t n, size_t topN_in, Range &best_scores_in, BestDropped &best_dropped_in, std::unique_ptr<IDiversifier> diversifier)
    : vespalib::Rendezvous<SortedHitSequence, TaggedHits, true>(n),
      topN(topN_in),
      best_scores(best_scores_in),
      best_dropped(best_dropped_in),
      _diversifier(std::move(diversifier))
{}
MatchLoopCommunicator::GetSecondPhaseWork::~GetSecondPhaseWork() = default;

template<typename Q, typename F>
void
MatchLoopCommunicator::GetSecondPhaseWork::mingle(Q &queue, F &&accept)
{
    size_t picked = 0;
    search::feature_t last_score = 0.0;
    while ((picked < topN) && !queue.empty()) {
        uint32_t i = queue.front();
        const Hit & hit = in(i).get();
        if (accept(hit.first)) {
            out(picked % size()).emplace_back(hit, i);
            last_score = hit.second;
            if (++picked == 1) {
                best_scores.high = hit.second;
            }
        } else if (!best_dropped.valid) {
            best_dropped.valid = true;
            best_dropped.score = hit.second;
        }
        in(i).next();
        if (in(i).valid()) {
            queue.adjust();
        } else {
            queue.pop_front();
        }
    }
    if (picked > 0) {
        best_scores.low = last_score;
    }
}

void
MatchLoopCommunicator::GetSecondPhaseWork::mingle()
{
    best_scores = Range();
    best_dropped.valid = false;
    size_t est_out = (topN / size()) + 1;
    vespalib::PriorityQueue<uint32_t, SelectCmp> queue(SelectCmp(*this));
    for (size_t i = 0; i < size(); ++i) {
        out(i).reserve(est_out);
        if (in(i).valid()) {
            queue.push(i);
        }
    }
    if (_diversifier) {
        mingle(queue, [diversifier=_diversifier.get()](uint32_t docId) { return diversifier->accepted(docId);});
    } else {
        mingle(queue, [](uint32_t) { return true;});
    }
}

void
MatchLoopCommunicator::CompleteSecondPhase::mingle()
{
    RangePair score_ranges(best_scores, Range());
    Range &new_scores = score_ranges.second;
    size_t est_out = (topN / size()) + 16;
    for (size_t i = 0; i < size(); ++i) {
        out(i).first.reserve(est_out);
    }
    for (size_t i = 0; i < size(); ++i) {
        for (const auto &[hit, tag]: in(i)) {
            out(tag).first.push_back(hit);
            new_scores.update(hit.second);
        }
    }
    if (score_ranges.first.isValid() && score_ranges.second.isValid()) {
        if (best_dropped.valid) {
            score_ranges.first.low = std::max(score_ranges.first.low, best_dropped.score);
        }
        for (size_t i = 0; i < size(); ++i) {
            out(i).second = score_ranges;
        }
    }
}

}
