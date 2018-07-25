// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_match_loop_communicator.h"
#include <vespa/vespalib/util/rendezvous.h>

namespace proton::matching {

class MatchLoopCommunicator final : public IMatchLoopCommunicator
{
private:
    struct EstimateMatchFrequency : vespalib::Rendezvous<Matches, double> {
        EstimateMatchFrequency(size_t n) : vespalib::Rendezvous<Matches, double>(n) {}
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
    struct SelectDiversifiedBest : vespalib::Rendezvous<std::vector<Hit>, IndexesToKeep> {
        search::queryeval::IDiversifier & _diversifier;
        std::vector<size_t> _indexes;
        SelectDiversifiedBest(size_t n, search::queryeval::IDiversifier & diversifier)
            : vespalib::Rendezvous<std::vector<Hit>, IndexesToKeep>(n),
              _diversifier(diversifier),
              _indexes(n, 0)
        {}
        ~SelectDiversifiedBest() override;
        void mingle() override;

        bool cmp(const uint32_t &a, const uint32_t &b) {
            return (in(a)[_indexes[a]].second > in(b)[_indexes[b]].second);
        }
    };
    template <typename Best>
    struct SelectCmp {
        Best &sb;
        SelectCmp(Best &sb_in) : sb(sb_in) {}
        bool operator()(const uint32_t &a, const uint32_t &b) const {
            return (sb.cmp(a, b));
        }
    };

    struct RangeCover : vespalib::Rendezvous<RangePair, RangePair> {
        RangeCover(size_t n) : vespalib::Rendezvous<RangePair, RangePair>(n) {}
        void mingle() override;
    };
    EstimateMatchFrequency _estimate_match_frequency;
    SelectBest             _selectBest;
    SelectDiversifiedBest  _selectDiversifiedBest;
    RangeCover             _rangeCover;

public:
    MatchLoopCommunicator(size_t threads, size_t topN, search::queryeval::IDiversifier & diversifier);
    MatchLoopCommunicator(size_t threads, size_t topN);
    ~MatchLoopCommunicator();

    double estimate_match_frequency(const Matches &matches) override {
        return _estimate_match_frequency.rendezvous(matches);
    }
    size_t selectBest(const std::vector<feature_t> &sortedScores) override {
        return _selectBest.rendezvous(sortedScores);
    }
    IndexesToKeep selectDiversifiedBest(const std::vector<Hit> &sortedScores) override {
        return _selectDiversifiedBest.rendezvous(sortedScores);
    }
    RangePair rangeCover(const RangePair &ranges) override {
        return _rangeCover.rendezvous(ranges);
    }
};

}
