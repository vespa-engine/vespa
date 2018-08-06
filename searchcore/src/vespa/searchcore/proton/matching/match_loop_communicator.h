// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_match_loop_communicator.h"
#include <vespa/vespalib/util/rendezvous.h>

namespace proton::matching {

class MatchLoopCommunicator : public IMatchLoopCommunicator
{
private:
    struct EstimateMatchFrequency : vespalib::Rendezvous<Matches, double> {
        EstimateMatchFrequency(size_t n)
            : vespalib::Rendezvous<Matches, double>(n) {}
        void mingle() override;
    };
    struct SelectBest : vespalib::Rendezvous<std::vector<feature_t>, size_t> {
        size_t topN;
        SelectBest(size_t n, size_t topN_in)
            : vespalib::Rendezvous<std::vector<feature_t>, size_t>(n), topN(topN_in) {}
        void mingle() override;
        bool cmp(const uint32_t &a, const uint32_t &b) {
            return (in(a)[out(a)] > in(b)[out(b)]);
        }
    };
    struct SelectCmp {
        SelectBest &sb;
        SelectCmp(SelectBest &sb_in) : sb(sb_in) {}
        bool operator()(const uint32_t &a, const uint32_t &b) const {
            return (sb.cmp(a, b));
        }
    };
    struct RangeCover : vespalib::Rendezvous<RangePair, RangePair> {
        RangeCover(size_t n)
            : vespalib::Rendezvous<RangePair, RangePair>(n) {}void mingle() override;
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
    size_t selectBest(const std::vector<feature_t> &sortedScores) override {
        return _selectBest.rendezvous(sortedScores);
    }
    RangePair rangeCover(const RangePair &ranges) override {
        return _rangeCover.rendezvous(ranges);
    }
};

}
