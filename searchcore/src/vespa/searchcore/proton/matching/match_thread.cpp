// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "match_thread.h"
#include "document_scorer.h"
#include <vespa/searchlib/common/featureset.h>
#include <vespa/searchlib/query/base.h>
#include <vespa/searchlib/queryeval/multibitvectoriterator.h>
#include <vespa/searchlib/queryeval/andnotsearch.h>
#include <vespa/vespalib/util/closure.h>
#include <vespa/vespalib/util/thread_bundle.h>
#include <vespa/searchcore/grouping/groupingmanager.h>
#include <vespa/searchcore/grouping/groupingcontext.h>
#include <vespa/searchlib/common/bitvector.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.match_thread");

namespace proton::matching {

using search::queryeval::OptimizedAndNotForBlackListing;
using search::queryeval::SearchIterator;
using search::fef::MatchData;
using search::fef::RankProgram;
using search::fef::FeatureResolver;
using search::fef::LazyValue;

namespace {

struct WaitTimer {
    double &wait_time_s;
    fastos::StopWatch wait_time;
    WaitTimer(double &wait_time_s_in)
        : wait_time_s(wait_time_s_in), wait_time()
    {
        wait_time.start();
    }
    void done() {
        wait_time.stop();
        wait_time_s += wait_time.elapsed().sec();
    }
};

// seek_next maps to SearchIterator::seekNext
struct SimpleStrategy {
    static uint32_t seek_next(SearchIterator &search, uint32_t docid) {
        return search.seekNext(docid);
    }
};

// seek_next maps to OptimizedAndNotForBlackListing::seekFast
struct FastBlackListingStrategy {
    static bool can_use(bool do_rank, bool do_limit, SearchIterator &search) {
        return (!do_rank && !do_limit &&
                (dynamic_cast<OptimizedAndNotForBlackListing *>(&search) != nullptr));
    }
    static uint32_t seek_next(SearchIterator &search, uint32_t docid) {
        return static_cast<OptimizedAndNotForBlackListing &>(search).seekFast(docid);
    }
};

LazyValue get_score_feature(const RankProgram &rankProgram) {
    FeatureResolver resolver(rankProgram.get_seeds());
    assert(resolver.num_features() == 1u);
    return resolver.resolve(0);
}

} // namespace proton::matching::<unnamed>

//-----------------------------------------------------------------------------

MatchThread::Context::Context(double rankDropLimit, MatchTools &tools, HitCollector &hits,
                              uint32_t num_threads)
    : matches(0),
      _matches_limit(tools.match_limiter().sample_hits_per_thread(num_threads)),
      _score_feature(get_score_feature(tools.rank_program())),
      _ranking(tools.rank_program()),
      _rankDropLimit(rankDropLimit),
      _hits(hits),
      _softDoom(tools.getSoftDoom())
{
}

void
MatchThread::Context::rankHit(uint32_t docId) {
    double score = _score_feature.as_number(docId);
    // convert NaN and Inf scores to -Inf
    if (__builtin_expect(std::isnan(score) || std::isinf(score), false)) {
        score = -HUGE_VAL;
    }
    // invert test since default drop limit is -NaN (keep all hits)
    if (!(score <= _rankDropLimit)) {
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
    {
        auto search = tools.borrow_search();
        search = tools.match_limiter().maybe_limit(std::move(search), match_freq, matchParams.numDocs);
        tools.give_back_search(std::move(search));
        if (tools.match_limiter().was_limited()) {
            tools.tag_search_as_changed();
        }
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

template <typename Strategy, bool do_rank, bool do_limit, bool do_share_work>
uint32_t
MatchThread::inner_match_loop(Context &context, MatchTools &tools, DocidRange docid_range)
{
    SearchIterator *search = &tools.search();
    search->initRange(docid_range.begin, docid_range.end);
    uint32_t docId = search->seekFirst(docid_range.begin);
    while ((docId < docid_range.end) && !context.atSoftDoom()) {
        if (do_rank) {
            search->unpack(docId);
            context.rankHit(docId);
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

template <typename Strategy, bool do_rank, bool do_limit, bool do_share_work>
void
MatchThread::match_loop(MatchTools &tools, HitCollector &hits)
{
    bool softDoomed = false;
    uint32_t docsCovered = 0;
    Context context(matchParams.rankDropLimit, tools, hits, num_threads);
    for (DocidRange docid_range = scheduler.first_range(thread_id);
         !docid_range.empty() && ! softDoomed;
         docid_range = scheduler.next_range(thread_id))
    {
        uint32_t lastCovered = inner_match_loop<Strategy, do_rank, do_limit, do_share_work>(context, tools, docid_range);
        softDoomed = (lastCovered < docid_range.end);
        docsCovered += std::min(lastCovered, docid_range.end) - docid_range.begin;
    }
    uint32_t matches = context.matches;
    if (do_limit && context.isBelowLimit()) {
        const size_t searchedSoFar = scheduler.total_size(thread_id);
        LOG(debug, "Limit not reached (had %d) at docid=%d which is after %zu docs.",
            matches, scheduler.total_span(thread_id).end, searchedSoFar);
        estimate_match_frequency(matches, searchedSoFar);
        tools.match_limiter().updateDocIdSpaceEstimate(searchedSoFar, 0);
    }
    thread_stats.docsCovered(docsCovered);
    thread_stats.docsMatched(matches);
    thread_stats.softDoomed(softDoomed);
    if (do_rank) {
        thread_stats.docsRanked(matches);
    }
}

//-----------------------------------------------------------------------------

template <bool do_rank, bool do_limit, bool do_share>
void
MatchThread::match_loop_helper_rank_limit_share(MatchTools &tools, HitCollector &hits)
{
    if (FastBlackListingStrategy::can_use(do_rank, do_limit, tools.search())) {
        match_loop<FastBlackListingStrategy, do_rank, do_limit, do_share>(tools, hits);
    } else {
        match_loop<SimpleStrategy, do_rank, do_limit, do_share>(tools, hits);
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

//-----------------------------------------------------------------------------

search::ResultSet::UP
MatchThread::findMatches(MatchTools &tools)
{
    tools.setup_first_phase();
    if (isFirstThread()) {
        LOG(spam, "SearchIterator: %s", tools.search().asString().c_str());
    }
    tools.give_back_search(search::queryeval::MultiBitVectorIteratorBase::optimize(tools.borrow_search()));
    if (isFirstThread()) {
        LOG(debug, "SearchIterator after MultiBitVectorIteratorBase::optimize(): %s", tools.search().asString().c_str());
    }
    HitCollector hits(matchParams.numDocs, matchParams.arraySize, matchParams.heapSize);
    match_loop_helper(tools, hits);
    if (tools.has_second_phase_rank()) {
        { // 2nd phase ranking
            tools.setup_second_phase();
            DocidRange docid_range = scheduler.total_span(thread_id);
            tools.search().initRange(docid_range.begin, docid_range.end);
            auto sorted_scores = hits.getSortedHeapScores();
            WaitTimer select_best_timer(wait_time_s);
            size_t useHits = communicator.selectBest(sorted_scores);
            select_best_timer.done();
            DocumentScorer scorer(tools.rank_program(), tools.search());
            uint32_t reRanked = hits.reRank(scorer, tools.getHardDoom().doom() ? 0 : useHits);
            thread_stats.docsReRanked(reRanked);
        }
        { // rank scaling
            auto my_ranges = hits.getRanges();
            WaitTimer range_cover_timer(wait_time_s);
            auto ranges = communicator.rangeCover(my_ranges);
            range_cover_timer.done();
            hits.setRanges(ranges);
        }
    }
    return hits.getResultSet(fallback_rank_value());
}

void
MatchThread::processResult(const Doom & hardDoom,
                           search::ResultSet::UP result,
                           ResultProcessor::Context &context)
{
    if (hardDoom.doom()) return;
    bool hasGrouping = (context.grouping.get() != 0);
    if (context.sort->hasSortData() || hasGrouping) {
        result->mergeWithBitOverflow(fallback_rank_value());
    }
    if (hardDoom.doom()) return;
    size_t             totalHits = result->getNumHits();
    search::RankedHit *hits      = result->getArray();
    size_t             numHits   = result->getArrayUsed();
    search::BitVector *bits  = result->getBitOverflow();
    if (bits != nullptr && hits != nullptr) {
        bits->andNotWithT(search::RankedHitIterator(hits, numHits));
    }
    if (hardDoom.doom()) return;
    if (hasGrouping) {
        search::grouping::GroupingManager man(*context.grouping);
        man.groupUnordered(hits, numHits, bits);
    }
    if (hardDoom.doom()) return;
    size_t sortLimit = hasGrouping ? numHits : context.result->maxSize();
    context.sort->sorter->sortResults(hits, numHits, sortLimit);
    if (hardDoom.doom()) return;
    if (hasGrouping) {
        search::grouping::GroupingManager man(*context.grouping);
        man.groupInRelevanceOrder(hits, numHits);
    }
    if (hardDoom.doom()) return;
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
                         uint32_t distributionKey) :
    thread_id(thread_id_in),
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
    match_with_ranking(mtf.has_first_phase_rank() && mp.save_rank_scores())
{
}

void
MatchThread::run()
{
    fastos::StopWatch total_time;
    fastos::StopWatch match_time;
    total_time.start();
    match_time.start();
    MatchTools::UP matchTools = matchToolsFactory.createMatchTools();
    search::ResultSet::UP result = findMatches(*matchTools);
    match_time.stop();
    match_time_s = match_time.elapsed().sec();
    resultContext = resultProcessor.createThreadContext(matchTools->getHardDoom(), thread_id, _distributionKey);
    {
        WaitTimer get_token_timer(wait_time_s);
        QueryLimiter::Token::UP processToken(
                matchTools->getQueryLimiter().getToken(matchTools->getHardDoom(),
                        scheduler.total_size(thread_id),
                        result->getNumHits(),
                        resultContext->sort->hasSortData(),
                        resultContext->grouping.get() != 0));
        get_token_timer.done();
        processResult(matchTools->getHardDoom(), std::move(result), *resultContext);
    }
    total_time.stop();
    total_time_s = total_time.elapsed().sec();
    thread_stats.active_time(total_time_s - wait_time_s).wait_time(wait_time_s);
    mergeDirector.dualMerge(thread_id, *resultContext->result, resultContext->groupingSource);
}

}
