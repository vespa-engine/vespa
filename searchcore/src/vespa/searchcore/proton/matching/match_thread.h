// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "match_tools.h"
#include "i_match_loop_communicator.h"
#include "match_params.h"
#include "matching_stats.h"
#include "partial_result.h"
#include "result_processor.h"
#include "docid_range_scheduler.h"
#include <vespa/vespalib/util/runnable.h>
#include <vespa/vespalib/util/dual_merge_director.h>
#include <vespa/searchlib/common/resultset.h>
#include <vespa/searchlib/common/sortresults.h>
#include <vespa/searchlib/queryeval/hitcollector.h>

namespace proton::matching {

/**
 * Runs a single match thread and keeps track of local state.
 **/
class MatchThread : public vespalib::Runnable
{
public:
    using UP = std::unique_ptr<MatchThread>;
    using SearchIterator = search::queryeval::SearchIterator;
    using MatchData = search::fef::MatchData;
    using HitCollector = search::queryeval::HitCollector;
    using RankProgram = search::fef::RankProgram;
    using LazyValue = search::fef::LazyValue;
    using Doom = vespalib::Doom;

private:
    size_t                        thread_id;
    size_t                        num_threads;
    MatchParams                   matchParams;
    const MatchToolsFactory      &matchToolsFactory;
    IMatchLoopCommunicator       &communicator;
    DocidRangeScheduler          &scheduler;
    IdleObserver                  idle_observer;
    uint32_t                      _distributionKey;
    ResultProcessor              &resultProcessor;
    vespalib::DualMergeDirector  &mergeDirector;
    ResultProcessor::Context::UP  resultContext;
    MatchingStats::Partition      thread_stats;
    double                        total_time_s;
    double                        match_time_s;
    double                        wait_time_s;
    bool                          match_with_ranking;

    class Context {
    public:
        Context(double rankDropLimit, MatchTools &tools, HitCollector &hits,
                uint32_t num_threads) __attribute__((noinline));
        void rankHit(uint32_t docId);
        void addHit(uint32_t docId) { _hits.addHit(docId, search::zero_rank_value); }
        bool isBelowLimit() const { return matches < _matches_limit; }
        bool    isAtLimit() const { return matches == _matches_limit; }
        bool   atSoftDoom() const { return _softDoom.doom(); }
        uint32_t                 matches;
    private:
        uint32_t                 _matches_limit;
        LazyValue                _score_feature;
        RankProgram             &_ranking;
        double                   _rankDropLimit;
        HitCollector            &_hits;
        const Doom              &_softDoom;
    };

    double estimate_match_frequency(uint32_t matches, uint32_t searchedSoFar) __attribute__((noinline));
    SearchIterator *maybe_limit(MatchTools &tools, uint32_t matches, uint32_t docId, uint32_t endId) __attribute__((noinline));

    bool any_idle() const { return (idle_observer.get() > 0); }
    bool try_share(DocidRange &docid_range, uint32_t next_docid) __attribute__((noinline));

    template <typename Strategy, bool do_rank, bool do_limit, bool do_share_work>
    uint32_t inner_match_loop(Context &context, MatchTools &tools, DocidRange &docid_range) __attribute__((noinline));

    template <typename Strategy, bool do_rank, bool do_limit, bool do_share_work>
    void match_loop(MatchTools &tools, HitCollector &hits) __attribute__((noinline));

    template <bool do_rank, bool do_limit, bool do_share> void match_loop_helper_rank_limit_share(MatchTools &tools, HitCollector &hits);
    template <bool do_rank, bool do_limit> void match_loop_helper_rank_limit(MatchTools &tools, HitCollector &hits);
    template <bool do_rank> void match_loop_helper_rank(MatchTools &tools, HitCollector &hits);
    void match_loop_helper(MatchTools &tools, HitCollector &hits);

    search::ResultSet::UP findMatches(MatchTools &tools);

    void processResult(const Doom & hardDoom, search::ResultSet::UP result, ResultProcessor::Context &context);

    bool isFirstThread() const { return thread_id == 0; }

    search::HitRank fallback_rank_value() const { return match_with_ranking ? search::default_rank_value : search::zero_rank_value; }

public:
    MatchThread(size_t thread_id_in,
                size_t num_threads_in,
                const MatchParams &mp,
                const MatchToolsFactory &mtf,
                IMatchLoopCommunicator &com,
                DocidRangeScheduler &sched,
                ResultProcessor &rp,
                vespalib::DualMergeDirector &md,
                uint32_t distributionKey);
    virtual void run() override;
    const MatchingStats::Partition &get_thread_stats() const { return thread_stats; }
    double get_match_time() const { return match_time_s; }
    PartialResult::UP extract_result() { return std::move(resultContext->result); }
};

}
