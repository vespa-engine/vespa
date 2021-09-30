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
    struct GetSecondPhaseWork : vespalib::Rendezvous<SortedHitSequence, TaggedHits, true> {
        size_t topN;
        Range &best_scores;
        BestDropped &best_dropped;
        std::unique_ptr<IDiversifier> _diversifier;
        GetSecondPhaseWork(size_t n, size_t topN_in, Range &best_scores_in, BestDropped &best_dropped_in, std::unique_ptr<IDiversifier>);
        ~GetSecondPhaseWork() override;
        void mingle() override;
        template<typename Q, typename F>
        void mingle(Q &queue, F &&accept);
        bool cmp(uint32_t a, uint32_t b) {
            return (in(a).get().second > in(b).get().second);
        }
    };
    struct SelectCmp {
        GetSecondPhaseWork &sb;
        SelectCmp(GetSecondPhaseWork &sb_in) : sb(sb_in) {}
        bool operator()(uint32_t a, uint32_t b) const {
            return (sb.cmp(a, b));
        }
    };
    struct CompleteSecondPhase : vespalib::Rendezvous<TaggedHits, std::pair<Hits,RangePair>, true> {
        size_t topN;
        const Range &best_scores;
        const BestDropped &best_dropped;
        CompleteSecondPhase(size_t n, size_t topN_in, const Range &best_scores_in, const BestDropped &best_dropped_in)
            : vespalib::Rendezvous<TaggedHits, std::pair<Hits,RangePair>, true>(n),
              topN(topN_in), best_scores(best_scores_in), best_dropped(best_dropped_in) {}
        void mingle() override;
    };

    Range                  _best_scores;
    BestDropped            _best_dropped;
    EstimateMatchFrequency _estimate_match_frequency;
    GetSecondPhaseWork     _get_second_phase_work;
    CompleteSecondPhase    _complete_second_phase;

public:
    MatchLoopCommunicator(size_t threads, size_t topN);
    MatchLoopCommunicator(size_t threads, size_t topN, std::unique_ptr<IDiversifier>);
    ~MatchLoopCommunicator();

    double estimate_match_frequency(const Matches &matches) override {
        return _estimate_match_frequency.rendezvous(matches);
    }

    TaggedHits get_second_phase_work(SortedHitSequence sortedHits, size_t thread_id) override {
        return _get_second_phase_work.rendezvous(sortedHits, thread_id);
    }

    std::pair<Hits,RangePair> complete_second_phase(TaggedHits my_results, size_t thread_id) override {
        return _complete_second_phase.rendezvous(std::move(my_results), thread_id);
    }
};

}
