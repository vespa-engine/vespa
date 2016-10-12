// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/runnable.h>
#include <vespa/vespalib/util/dual_merge_director.h>
#include <vespa/searchlib/common/resultset.h>
#include <vespa/searchlib/common/sortresults.h>
#include <vespa/searchlib/queryeval/hitcollector.h>
#include "match_tools.h"
#include "i_match_loop_communicator.h"
#include "match_params.h"
#include "matching_stats.h"
#include "partial_result.h"
#include "result_processor.h"
#include "docid_range_scheduler.h"

namespace proton {
namespace matching {

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

private:
    size_t                        thread_id;
    size_t                        num_threads;
    MatchParams                   matchParams;
    const MatchToolsFactory      &matchToolsFactory;
    IMatchLoopCommunicator       &communicator;
    DocidRangeScheduler          &scheduler;
    uint32_t                      _distributionKey;
    ResultProcessor              &resultProcessor;
    vespalib::DualMergeDirector  &mergeDirector;
    ResultProcessor::Context::UP  resultContext;
    MatchingStats::Partition      thread_stats;
    double                        total_time_s;
    double                        match_time_s;
    double                        wait_time_s;

    search::ResultSet::UP findMatches(MatchTools &matchTools);

    using Doom = vespalib::Doom;
    void processResult(const Doom & doom,
                       search::ResultSet::UP result,
                       ResultProcessor::Context &context);

    template <typename IteratorT, bool do_rank, bool do_limit, bool do_share_work>
    void match_loop(MatchTools &matchTools, IteratorT search, RankProgram &ranking, HitCollector &hits) __attribute__((noinline));

    template <typename IteratorT, bool do_rank, bool do_limit>
    void match_loop_helper_2(MatchTools &matchTools, IteratorT search, RankProgram &ranking, HitCollector &hits);

    template <typename IteratorT, bool do_rank>
    void match_loop_helper(MatchTools &matchTools, IteratorT search, RankProgram &ranking, HitCollector &hits);

    template <bool do_rank, bool do_limit>
    class InnerMatchParams {
    public:
        InnerMatchParams(MatchTools &matchTools, RankProgram & ranking, DocidRangeScheduler & scheduler, uint32_t num_threads);
        const double            * score_feature;
        RankProgram             & ranking;
        uint32_t                  matches_limit;
        const Doom              & doom;
        MaybeMatchPhaseLimiter  & limiter;
        IdleObserver              idle_observer;
    private:
    };

    template <typename IteratorT, bool do_rank, bool do_limit, bool do_share_work>
    void inner_match_loop(const InnerMatchParams<do_rank, do_limit> & params, IteratorT & search,
                         HitCollector &hits, uint32_t & matches, uint32_t & docId, DocidRange docid_range) __attribute__((noinline));

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
    virtual void run();
    const MatchingStats::Partition &get_thread_stats() const { return thread_stats; }
    double get_match_time() const { return match_time_s; }
};

} // namespace proton::matching
} // namespace proton

