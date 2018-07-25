// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "match_loop_communicator.h"
#include <vespa/vespalib/util/priority_queue.h>

namespace proton:: matching {

namespace {

class AllowAll final : public IMatchLoopCommunicator::IDiversifier {
public:
    bool keep(uint32_t docId) override {
        (void) docId;
        return true;
    }
};

AllowAll _G_allowAll;
}

MatchLoopCommunicator::MatchLoopCommunicator(size_t threads, size_t topN)
    : MatchLoopCommunicator(threads, topN, _G_allowAll)
{}

MatchLoopCommunicator::MatchLoopCommunicator(size_t threads, size_t topN, IDiversifier & diversifier)
    : _estimate_match_frequency(threads),
      _selectBest(threads, topN),
      _selectDiversifiedBest(threads, diversifier),
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

void
MatchLoopCommunicator::SelectBest::mingle()
{
    vespalib::PriorityQueue<uint32_t, SelectCmp<SelectBest>> queue(SelectCmp(*this));
    for (size_t i = 0; i < size(); ++i) {
        if (!in(i).empty()) {
            queue.push(i);
        }
    }
    for (size_t picked = 0; picked < topN && !queue.empty(); ++picked) {
        uint32_t i = queue.front();
        if (in(i).size() > ++out(i)) {
            queue.adjust();
        } else {
            queue.pop_front();
        }
    }
}

MatchLoopCommunicator::SelectDiversifiedBest::~SelectDiversifiedBest() = default;

void
MatchLoopCommunicator::SelectDiversifiedBest::mingle()
{
    vespalib::PriorityQueue<uint32_t, SelectCmp<SelectDiversifiedBest>> queue(SelectCmp(*this));
    for (size_t i = 0; i < size(); ++i) {
        if (!in(i).empty()) {
            queue.push(i);
        }
    }
    while (!queue.empty()) {
        uint32_t i = queue.front();
        uint32_t docId = in(i)[_indexes[i]].first;
        if (_diversifier.keep(docId)) {
            out(i).push_back(_indexes[i]);
        }
        if (in(i).size() > ++_indexes[i]) {
            queue.adjust();
        } else {
            queue.pop_front();
        }
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
