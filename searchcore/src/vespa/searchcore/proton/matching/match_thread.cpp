// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "match_thread.h"
#include "document_scorer.h"
#include "match_tools.h"
#include <vespa/searchcore/grouping/groupingmanager.h>
#include <vespa/searchcore/grouping/groupingcontext.h>
#include <vespa/searchlib/engine/trace.h>
#include <vespa/searchlib/attribute/attribute_operation.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/fef/rank_program.h>
#include <vespa/searchlib/queryeval/multibitvectoriterator.h>
#include <vespa/searchlib/queryeval/andnotsearch.h>
#include <vespa/searchlib/queryeval/profiled_iterator.h>
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/data/slime/inserter.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.match_thread");

namespace proton::matching {

using search::attribute::AttributeOperation;
using search::fef::BlueprintResolver;
using search::fef::FeatureResolver;
using search::fef::LazyValue;
using search::fef::MatchData;
using search::fef::RankProgram;
using search::queryeval::HitCollector;
using search::queryeval::ProfiledIterator;
using search::queryeval::SearchIterator;
using search::queryeval::SortedHitSequence;

namespace {

struct WaitTimer {
    double &wait_time_s;
    vespalib::Timer wait_time;
    explicit WaitTimer(double &wait_time_s_in)
        : wait_time_s(wait_time_s_in), wait_time()
    { }
    void done() {
        wait_time_s += vespalib::to_s(wait_time.elapsed());
    }
};

// seek_next maps to SearchIterator::seekNext
struct SimpleStrategy {
    static uint32_t seek_next(SearchIterator &search, uint32_t docid) {
        return search.seekNext(docid);
    }
};

LazyValue get_score_feature(const RankProgram &rankProgram) {
    FeatureResolver resolver(rankProgram.get_seeds());
    assert(resolver.num_features() == 1u);
    return resolver.resolve(0);
}

} // namespace proton::matching::<unnamed>

//-----------------------------------------------------------------------------

MatchThread::Context::Context(double rankDropLimit, MatchTools &tools, HitCollector &hits, uint32_t num_threads)
    : matches(0),
      _matches_limit(tools.match_limiter().sample_hits_per_thread(num_threads)),
      _score_feature(get_score_feature(tools.rank_program())),
      _rankDropLimit(rankDropLimit),
      _hits(hits),
      _doom(tools.getDoom()),
      dropped()
{
}

template <MatchThread::RankDropLimitE use_rank_drop_limit>
void
MatchThread::Context::rankHit(uint32_t docId) {
    double score = _score_feature.as_number(docId);
    // convert NaN and Inf scores to -Inf
    if (__builtin_expect(std::isnan(score) || std::isinf(score), false)) {
        score = -HUGE_VAL;
    }
    if (use_rank_drop_limit != RankDropLimitE::no) {
        if (__builtin_expect(score > _rankDropLimit, true)) {
            _hits.addHit(docId, score);
        } else if (use_rank_drop_limit == RankDropLimitE::track) {
            dropped.template emplace_back(docId);
        }
    } else {
        _hits.addHit(docId, score);
    }
}

//-----------------------------------------------------------------------------

double
MatchThread::estimate_match_frequency(uint32_t matches, uint32_t searchedSoFar)
{
    IMatchLoopCommunicator::Matches my_matches(matches, searchedSoFar);
    WaitTimer count_matches_timer(wait_time_s);
    double match_freq = communicator.estimate_match_frequency(my_matches);
    count_matches_timer.done();
    return match_freq;
}

SearchIterator *
MatchThread::maybe_limit(MatchTools &tools, uint32_t matches, uint32_t docId, uint32_t endId)
{
    const uint32_t local_todo = (endId - docId - 1);
    const size_t searchedSoFar = (scheduler.total_size(thread_id) - local_todo);
    double match_freq = estimate_match_frequency(matches, searchedSoFar);
    const size_t global_todo = scheduler.unassigned_size();
    vespalib::slime::Cursor * traceCursor = trace->maybeCreateCursor(5, "maybe_limit");
    {
        auto search = tools.borrow_search();
        search = tools.match_limiter().maybe_limit(std::move(search), match_freq, matchParams.numDocs, traceCursor);
        tools.give_back_search(std::move(search));
        if (tools.match_limiter().was_limited()) {
            tools.tag_search_as_changed();
        }
    }
    if (isFirstThread() && trace->shouldTrace(6) && tools.match_limiter().was_limited()) {
        vespalib::slime::ObjectInserter inserter(trace->createCursor("limited"), "query");
        tools.search().asSlime(inserter);
    }
    size_t left = local_todo + (global_todo / num_threads);
    tools.match_limiter().updateDocIdSpaceEstimate(searchedSoFar, left);
    LOG(debug, "Limit=%d has been reached at docid=%d which is after %zu docs.",
               matches, docId, (scheduler.total_size(thread_id) - local_todo));
    LOG(debug, "SearchIterator after limiter: %s", tools.search().asString().c_str());
    return &tools.search();
}

bool
MatchThread::try_share(DocidRange &docid_range, uint32_t next_docid) {
    DocidRange todo(next_docid, docid_range.end);
    DocidRange my_work = scheduler.share_range(thread_id, todo);
    if (my_work.end < todo.end) {
        docid_range = my_work;
        return true;
    }
    return false;
}

template <typename Strategy, bool do_rank, bool do_limit, bool do_share_work,
          MatchThread::RankDropLimitE use_rank_drop_limit>
uint32_t
MatchThread::inner_match_loop(Context &context, MatchTools &tools, DocidRange &docid_range)
{
    SearchIterator *search = &tools.search();
    search->initRange(docid_range.begin, docid_range.end);
    uint32_t docId = search->seekFirst(docid_range.begin);
    while ((docId < docid_range.end) && !context.atSoftDoom()) {
        if (do_rank) {
            search->unpack(docId);
            context.rankHit<use_rank_drop_limit>(docId);
        } else {
            context.addHit(docId);
        }
        context.matches++;
        if (do_limit && context.isAtLimit()) {
            search = maybe_limit(tools, context.matches, docId, docid_range.end);
            docId = search->seekFirst(docId + 1);
        } else if (do_share_work && any_idle() && try_share(docid_range, docId + 1)) {
            search->initRange(docid_range.begin, docid_range.end);
            docId = search->seekFirst(docid_range.begin);
        } else {
            docId = Strategy::seek_next(*search, docId + 1);
        }
    }
    return docId;
}

template <typename Strategy, bool do_rank, bool do_limit, bool do_share_work,
          MatchThread::RankDropLimitE use_rank_drop_limit>
void
MatchThread::match_loop(MatchTools &tools, HitCollector &hits)
{
    bool softDoomed = false;
    uint32_t docsCovered = 0;
    vespalib::duration overtime(vespalib::duration::zero());
    Context context(matchParams.rankDropLimit, tools, hits, num_threads);
    for (DocidRange docid_range = scheduler.first_range(thread_id);
         !docid_range.empty();
         docid_range = scheduler.next_range(thread_id))
    {
        if (!softDoomed) {
            uint32_t lastCovered = inner_match_loop<Strategy, do_rank, do_limit, do_share_work, use_rank_drop_limit>(context, tools, docid_range);
            softDoomed = (lastCovered < docid_range.end);
            if (softDoomed) {
                overtime = - context.timeLeft();
            }
            docsCovered += std::min(lastCovered, docid_range.end) - docid_range.begin;
        }
    }
    uint32_t matches = context.matches;
    if (do_limit && context.isBelowLimit()) {
        const size_t searchedSoFar = scheduler.total_size(thread_id);
        LOG(debug, "Limit not reached (had %d) after %zu docs.",
            matches, searchedSoFar);
        estimate_match_frequency(matches, searchedSoFar);
        tools.match_limiter().updateDocIdSpaceEstimate(searchedSoFar, 0);
    }
    thread_stats.docsCovered(docsCovered);
    thread_stats.docsMatched(matches);
    thread_stats.softDoomed(softDoomed);
    if (softDoomed) {
        thread_stats.doomOvertime(overtime);
    }
    if (do_rank) {
        thread_stats.docsRanked(matches);
    }
    if (use_rank_drop_limit == RankDropLimitE::track) {
        if (auto task = matchToolsFactory.createOnMatchTask()) {
            task->run(std::move(context.dropped));
        }
    }
}

//-----------------------------------------------------------------------------

template <bool do_rank, bool do_limit, bool do_share, MatchThread::RankDropLimitE use_rank_drop_limit>
void
MatchThread::match_loop_helper_rank_limit_share_drop(MatchTools &tools, HitCollector &hits)
{
    match_loop<SimpleStrategy, do_rank, do_limit, do_share, use_rank_drop_limit>(tools, hits);
}

template <bool do_rank, bool do_limit, bool do_share>
void
MatchThread::match_loop_helper_rank_limit_share(MatchTools &tools, HitCollector &hits)
{
    if (matchParams.has_rank_drop_limit()) {
        if (matchToolsFactory.hasOnMatchTask()) {
            match_loop_helper_rank_limit_share_drop<do_rank, do_limit, do_share, RankDropLimitE::track>(tools, hits);
        } else {
            match_loop_helper_rank_limit_share_drop<do_rank, do_limit, do_share, RankDropLimitE::yes>(tools, hits);
        }
    } else {
        match_loop_helper_rank_limit_share_drop<do_rank, do_limit, do_share, RankDropLimitE::no>(tools, hits);
    }
}

template <bool do_rank, bool do_limit>
void
MatchThread::match_loop_helper_rank_limit(MatchTools &tools, HitCollector &hits)
{
    if (idle_observer.is_always_zero()) {
        match_loop_helper_rank_limit_share<do_rank, do_limit, false>(tools, hits);
    } else {
        match_loop_helper_rank_limit_share<do_rank, do_limit, true>(tools, hits);
    }
}

template <bool do_rank>
void
MatchThread::match_loop_helper_rank(MatchTools &tools, HitCollector &hits)
{
    if (tools.match_limiter().is_enabled()) {
        match_loop_helper_rank_limit<do_rank, true>(tools, hits);
    } else {
        match_loop_helper_rank_limit<do_rank, false>(tools, hits);
    }
}

void
MatchThread::match_loop_helper(MatchTools &tools, HitCollector &hits)
{
    if (match_with_ranking) {
        match_loop_helper_rank<true>(tools, hits);
    } else {
        match_loop_helper_rank<false>(tools, hits);
    }
}

search::ResultSet::UP
MatchThread::findMatches(MatchTools &tools)
{
    tools.setup_first_phase(first_phase_profiler.get());
    if (isFirstThread()) {
        LOG(spam, "SearchIterator: %s", tools.search().asString().c_str());
    }
    tools.give_back_search(search::queryeval::MultiBitVectorIteratorBase::optimize(tools.borrow_search()));
    if (isFirstThread()) {
        LOG(debug, "SearchIterator after MultiBitVectorIteratorBase::optimize(): %s", tools.search().asString().c_str());
        if (trace->shouldTrace(7)) {
            vespalib::slime::ObjectInserter inserter(trace->createCursor("iterator"), "optimized");
            tools.search().asSlime(inserter);
        }
    }
    if (match_profiler) {
        tools.give_back_search(ProfiledIterator::profile(*match_profiler, tools.borrow_search()));
        tools.tag_search_as_changed();
    }
    HitCollector hits(matchParams.numDocs, matchParams.arraySize);
    trace->addEvent(4, "Start match and first phase rank");
    match_loop_helper(tools, hits);
    if (tools.has_second_phase_rank()) {
        trace->addEvent(4, "Start second phase rerank");
        auto sorted_hit_seq = matchToolsFactory.should_diversify()
                              ? hits.getSortedHitSequence(matchParams.arraySize)
                              : hits.getSortedHitSequence(matchParams.heapSize);
        trace->addEvent(5, "Synchronize before second phase rerank");
        WaitTimer get_second_phase_work_timer(wait_time_s);
        auto my_work = communicator.get_second_phase_work(sorted_hit_seq, thread_id);
        get_second_phase_work_timer.done();
        if (tools.getDoom().hard_doom()) {
            my_work.clear();
        }
        if (!my_work.empty()) {
            tools.setup_second_phase(second_phase_profiler.get());
            DocumentScorer scorer(tools.rank_program(), tools.search());
            scorer.score(my_work);
        }
        thread_stats.docsReRanked(my_work.size());
        trace->addEvent(5, "Synchronize before rank scaling");
        WaitTimer complete_second_phase_timer(wait_time_s);
        auto [kept_hits, ranges] = communicator.complete_second_phase(my_work, thread_id);
        complete_second_phase_timer.done();
        hits.setReRankedHits(std::move(kept_hits));
        hits.setRanges(ranges);
        if (auto onReRankTask = matchToolsFactory.createOnSecondPhaseTask()) {
            onReRankTask->run(hits.getReRankedHits());
        }
    }
    trace->addEvent(4, "Create result set");
    return hits.getResultSet(fallback_rank_value());
}

void
MatchThread::processResult(const Doom & doom,
                           search::ResultSet::UP result,
                           ResultProcessor::Context &context)
{
    if (doom.hard_doom()) return;
    bool hasGrouping = bool(context.grouping);
    if (context.sort->hasSortData() || hasGrouping) {
        result->mergeWithBitOverflow(fallback_rank_value());
    }
    if (doom.hard_doom()) return;
    size_t             totalHits = result->getNumHits();
    const search::RankedHit *hits = result->getArray();
    size_t             numHits   = result->getArrayUsed();
    search::BitVector *bits  = result->getBitOverflow();
    if (bits != nullptr && hits != nullptr) {
        bits->andNotWithT(search::RankedHitIterator(hits, numHits));
    }
    if (doom.hard_doom()) return;
    if (hasGrouping) {
        search::grouping::GroupingManager man(*context.grouping);
        man.groupUnordered(hits, numHits, bits);
    }
    if (doom.hard_doom()) return;
    size_t sortLimit = hasGrouping ? numHits : context.result->maxSize();
    result->sort(*context.sort->sorter, sortLimit);
    if (doom.hard_doom()) return;
    if (hasGrouping) {
        search::grouping::GroupingManager man(*context.grouping);
        man.groupInRelevanceOrder(hits, numHits);
    }
    if (doom.hard_doom()) return;
    PartialResult &pr = *context.result;
    pr.totalHits(totalHits);
    size_t maxHits = std::min(numHits, pr.maxSize());
    if (pr.hasSortData()) {
        FastS_SortSpec &spec = context.sort->sortSpec;
        for (size_t i = 0; i < maxHits; ++i) {
            pr.add(hits[i], spec.getSortRef(i));
        }
    } else {
        for (size_t i = 0; i < maxHits; ++i) {
            pr.add(hits[i]);
        }
        if ((bits != nullptr) && (pr.size() < pr.maxSize())) {
            for (unsigned int bitId = bits->getFirstTrueBit();
                 (bitId < bits->size()) && (pr.size() < pr.maxSize());
                 bitId = bits->getNextTrueBit(bitId + 1))
            {
                pr.add(search::RankedHit(bitId));
            }
        }
    }

    if (auto task = matchToolsFactory.createOnMatchTask()) {
        task->run(result->copyResult());
    }
    if (auto task = matchToolsFactory.createOnFirstPhaseTask()) {
        task->run(search::ResultSet::stealResult(std::move(*result)));
    }
    if (hasGrouping) {
        context.grouping->setDistributionKey(_distributionKey);
    }
}

//-----------------------------------------------------------------------------

MatchThread::MatchThread(size_t thread_id_in,
                         size_t num_threads_in,
                         const MatchParams &mp,
                         const MatchToolsFactory &mtf,
                         IMatchLoopCommunicator &com,
                         DocidRangeScheduler &sched,
                         ResultProcessor &rp,
                         vespalib::DualMergeDirector &md,
                         uint32_t distributionKey,
                         const Trace &parent_trace)
  : thread_id(thread_id_in),
    num_threads(num_threads_in),
    matchParams(mp),
    matchToolsFactory(mtf),
    communicator(com),
    scheduler(sched),
    idle_observer(scheduler.make_idle_observer()),
    _distributionKey(distributionKey),
    resultProcessor(rp),
    mergeDirector(md),
    resultContext(),
    thread_stats(),
    total_time_s(0.0),
    match_time_s(0.0),
    wait_time_s(0.0),
    match_with_ranking(mtf.has_first_phase_rank() && mp.save_rank_scores()),
    trace(parent_trace.make_trace_up()),
    match_profiler(),
    first_phase_profiler(),
    second_phase_profiler(),
    my_issues()
{
    if (trace->getLevel() > 0) {
        if (int32_t depth = trace->match_profile_depth(); depth != 0) {
            match_profiler = std::make_unique<vespalib::ExecutionProfiler>(depth);
        }
        if (int32_t depth = trace->first_phase_profile_depth(); depth != 0) {
            first_phase_profiler = std::make_unique<vespalib::ExecutionProfiler>(depth);
        }
        if (int32_t depth = trace->second_phase_profile_depth(); depth != 0) {
            second_phase_profiler = std::make_unique<vespalib::ExecutionProfiler>(depth);
        }
    }
}

void
MatchThread::run()
{
    vespalib::Timer total_time;
    vespalib::Timer match_time(total_time);
    auto capture_issues = vespalib::Issue::listen(my_issues);
    trace->addEvent(4, "Start MatchThread::run");
    MatchTools::UP matchTools = matchToolsFactory.createMatchTools();
    search::ResultSet::UP result = findMatches(*matchTools);
    match_time_s = vespalib::to_s(match_time.elapsed());
    resultContext = resultProcessor.createThreadContext(matchTools->getDoom(), thread_id, _distributionKey);
    {
        trace->addEvent(5, "Wait for result processing token");
        WaitTimer get_token_timer(wait_time_s);
        QueryLimiter::Token::UP processToken(
                matchTools->getQueryLimiter().getToken(matchTools->getDoom(),
                        scheduler.total_size(thread_id),
                        result->getNumHits(),
                        resultContext->sort->hasSortData(),
                        bool(resultContext->grouping)));
        get_token_timer.done();
        trace->addEvent(5, "Start result processing");
        processResult(matchTools->getDoom(), std::move(result), *resultContext);
    }
    total_time_s = vespalib::to_s(total_time.elapsed());
    thread_stats.active_time(total_time_s - wait_time_s).wait_time(wait_time_s);
    trace->addEvent(4, "Start thread merge");
    mergeDirector.dualMerge(thread_id, *resultContext->result, resultContext->groupingSource);
    trace->addEvent(4, "MatchThread::run Done");
    if (match_profiler) {
        match_profiler->report(trace->createCursor("match_profiling"));
    }
    if (first_phase_profiler) {
        first_phase_profiler->report(trace->createCursor("first_phase_profiling"),
                                     [](const vespalib::string &name){ return BlueprintResolver::describe_feature(name); });
    }
    if (second_phase_profiler) {
        second_phase_profiler->report(trace->createCursor("second_phase_profiling"),
                                      [](const vespalib::string &name){ return BlueprintResolver::describe_feature(name); });
    }
}

}
