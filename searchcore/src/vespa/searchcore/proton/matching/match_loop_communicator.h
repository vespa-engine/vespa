// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_match_loop_communicator.h"
#include <vespa/searchlib/queryeval/idiversifier.h>
#include <vespa/vespalib/util/rendezvous.h>

namespace proton::matching {

class MatchLoopCommunicator : public IMatchLoopCommunicator
{
private:
    using IDiversifier = search::queryeval::IDiversifier;
    struct BestDropped {
        bool valid = false;
        search::feature_t score = 0.0;
    };
    struct EstimateMatchFrequency : vespalib::Rendezvous<Matches, double> {
        EstimateMatchFrequency(size_t n) : vespalib::Rendezvous<Matches, double>(n) {}
        void mingle() override;
    };
    struct SelectBest : vespalib::Rendezvous<SortedHitSequence, Hits> {
        size_t topN;
        BestDropped &best_dropped;
        std::unique_ptr<IDiversifier> _diversifier;
        SelectBest(size_t n, size_t topN_in, BestDropped &best_dropped_in, std::unique_ptr<IDiversifier>);
        ~SelectBest() override;
        void mingle() override;
        template<typename Q, typename F>
        void mingle(Q &queue, F &&accept);
        bool cmp(uint32_t a, uint32_t b) {
            return (in(a).get().second > in(b).get().second);
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
        BestDropped &best_dropped;
        RangeCover(size_t n, BestDropped &best_dropped_in)
            : vespalib::Rendezvous<RangePair, RangePair>(n), best_dropped(best_dropped_in) {}
        void mingle() override;
    };

    BestDropped                   _best_dropped;
    EstimateMatchFrequency        _estimate_match_frequency;
    SelectBest                    _selectBest;
    RangeCover                    _rangeCover;

public:
    MatchLoopCommunicator(size_t threads, size_t topN);
    MatchLoopCommunicator(size_t threads, size_t topN, std::unique_ptr<IDiversifier>);
    ~MatchLoopCommunicator();

    double estimate_match_frequency(const Matches &matches) override {
        return _estimate_match_frequency.rendezvous(matches);
    }
    Hits selectBest(SortedHitSequence sortedHits) override {
        return _selectBest.rendezvous(sortedHits);
    }
    RangePair rangeCover(const RangePair &ranges) override {
        return _rangeCover.rendezvous(ranges);
    }
};

}
