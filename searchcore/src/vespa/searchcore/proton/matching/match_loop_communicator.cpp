// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "match_loop_communicator.h"
#include <vespa/vespalib/util/priority_queue.h>

namespace proton:: matching {

MatchLoopCommunicator::MatchLoopCommunicator(size_t threads, size_t topN)
    : MatchLoopCommunicator(threads, topN, std::unique_ptr<IDiversifier>())
{}
MatchLoopCommunicator::MatchLoopCommunicator(size_t threads, size_t topN, std::unique_ptr<IDiversifier> diversifier)
    : _estimate_match_frequency(threads),
      _selectBest(threads, topN, std::move(diversifier)),
      _rangeCover(threads)
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

MatchLoopCommunicator::SelectBest::SelectBest(size_t n, size_t topN_in, std::unique_ptr<IDiversifier> diversifier)
    : vespalib::Rendezvous<Hits, Hits>(n),
      topN(topN_in),
      _indexes(n, 0),
      _diversifier(std::move(diversifier))
{}
MatchLoopCommunicator::SelectBest::~SelectBest() = default;

template<typename Q, typename F>
void
MatchLoopCommunicator::SelectBest::mingle(Q & queue, F && accept) {
    for (size_t picked = 0; picked < topN && !queue.empty(); ) {
        uint32_t i = queue.front();
        const Hit & hit = in(i)[_indexes[i]];
        if (accept(hit.first)) {
            out(i).emplace_back(hit);
            ++picked;
        }
        if (in(i).size() > ++_indexes[i]) {
            queue.adjust();
        } else {
            queue.pop_front();
        }
    }
}

void
MatchLoopCommunicator::SelectBest::mingle()
{
    vespalib::PriorityQueue<uint32_t, SelectCmp> queue(SelectCmp(*this));
    for (size_t i = 0; i < size(); ++i) {
        if (!in(i).empty()) {
            queue.push(i);
            out(i).reserve(in(i).size());
            _indexes[i] = 0;
        }
    }
    if (_diversifier) {
        mingle(queue, [diversifier=_diversifier.get()](uint32_t docId) { return diversifier->accepted(docId);});
    } else {
        mingle(queue, [](uint32_t) { return true;});
    }
}

void
MatchLoopCommunicator::RangeCover::mingle()
{
    size_t i = 0;
    while (i < size() && (!in(i).first.isValid() || !in(i).second.isValid())) {
        ++i;
    }
    if (i < size()) {
        RangePair result = in(i++);
        for (; i < size(); ++i) {
            if (in(i).first.isValid() && in(i).second.isValid()) {
                result.first.low = std::min(result.first.low, in(i).first.low);
                result.first.high = std::max(result.first.high, in(i).first.high);
                result.second.low = std::min(result.second.low, in(i).second.low);
                result.second.high = std::max(result.second.high, in(i).second.high);
            }
        }
        for (size_t j = 0; j < size(); ++j) {
            out(j) = result;
        }
    }
}

}
