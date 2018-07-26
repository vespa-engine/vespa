// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_match_loop_communicator.h"
#include <vespa/vespalib/util/rendezvous.h>

namespace proton::matching {

class MatchLoopCommunicator : public IMatchLoopCommunicator
{
private:
    struct EstimateMatchFrequency : vespalib::Rendezvous<Matches, double> {
        EstimateMatchFrequency(size_t n) : vespalib::Rendezvous<Matches, double>(n) {}
        void mingle() override;
    };
    struct SelectBest : vespalib::Rendezvous<Hits, Hits> {
        size_t topN;
        std::vector<uint32_t> _indexes;
        SelectBest(size_t n, size_t topN_in) : vespalib::Rendezvous<Hits, Hits>(n), topN(topN_in), _indexes(n, 0) {}
        ~SelectBest() override;
        void mingle() override;
        bool cmp(uint32_t a, uint32_t b) {
            return (in(a)[_indexes[a]].second > in(b)[_indexes[b]].second);
        }
    };
    struct SelectCmp {
        SelectBest &sb;
        SelectCmp(SelectBest &sb_in) : sb(sb_in) {}
        bool operator()(uint32_t a, uint32_t b) const {
            return (sb.cmp(a, b));
        }
    };
    struct RangeCover : vespalib::Rendezvous<RangePair, RangePair> {
        RangeCover(size_t n) : vespalib::Rendezvous<RangePair, RangePair>(n) {}
        void mingle() override;
    };
    EstimateMatchFrequency _estimate_match_frequency;
    SelectBest             _selectBest;
    RangeCover             _rangeCover;

public:
    MatchLoopCommunicator(size_t threads, size_t topN);
    ~MatchLoopCommunicator();

    double estimate_match_frequency(const Matches &matches) override {
        return _estimate_match_frequency.rendezvous(matches);
    }
    Hits selectBest(Hits sortedHits) override {
        return _selectBest.rendezvous(sortedHits);
    }
    RangePair rangeCover(const RangePair &ranges) override {
        return _rangeCover.rendezvous(ranges);
    }
};

}
