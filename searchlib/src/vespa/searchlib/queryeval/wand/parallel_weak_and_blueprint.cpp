// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "parallel_weak_and_blueprint.h"
#include "parallel_weak_and_search.h"
#include <vespa/searchlib/queryeval/field_spec.hpp>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/queryeval/flow_tuning.h>
#include <vespa/vespalib/objects/visit.hpp>
#include <algorithm>

namespace search::queryeval {

ParallelWeakAndBlueprint::ParallelWeakAndBlueprint(FieldSpecBase field, uint32_t scoresToTrack,
                                                   score_t scoreThreshold, double thresholdBoostFactor,
                                                   bool thread_safe)
    : ComplexLeafBlueprint(field),
      _scores(WeakAndPriorityQueue::createHeap(scoresToTrack, thread_safe)),
      _scoreThreshold(scoreThreshold),
      _thresholdBoostFactor(thresholdBoostFactor),
      _scoresAdjustFrequency(wand::DEFAULT_PARALLEL_WAND_SCORES_ADJUST_FREQUENCY),
      _layout(),
      _weights(),
      _terms(),
      _matching_phase(MatchingPhase::FIRST_PHASE)
{
}

ParallelWeakAndBlueprint::~ParallelWeakAndBlueprint() = default;

void
ParallelWeakAndBlueprint::reserve(size_t num_children) {
    _weights.reserve(num_children);
    _terms.reserve(num_children);
}

void
ParallelWeakAndBlueprint::addTerm(Blueprint::UP term, int32_t weight, HitEstimate & estimate)
{
    HitEstimate childEst = term->getState().estimate();
    if (!childEst.empty) {
        if (estimate.empty) {
            estimate = childEst;
        } else {
            estimate.estHits += childEst.estHits;
        }
    }
    _weights.push_back(weight);
    _terms.push_back(std::move(term));
}

void
ParallelWeakAndBlueprint::sort(InFlow in_flow)
{
    resolve_strict(in_flow);
    auto flow = OrFlow(in_flow);
    for (auto &term: _terms) {
        term->sort(InFlow(flow.strict(), flow.flow()));
        flow.add(term->estimate());
    }
}

FlowStats
ParallelWeakAndBlueprint::calculate_flow_stats(uint32_t docid_limit) const
{
    for (auto &term: _terms) {
        term->update_flow_stats(docid_limit);
    }
    double child_est = OrFlow::estimate_of(_terms);
    double my_est = abs_to_rel_est(_scores->getScoresToTrack(), docid_limit);
    double est = (child_est + my_est) / 2.0;
    return {est, OrFlow::cost_of(_terms, false),
            OrFlow::cost_of(_terms, true) + flow::heap_cost(est, _terms.size())};
}

SearchIterator::UP
ParallelWeakAndBlueprint::createLeafSearch(const search::fef::TermFieldMatchDataArray &tfmda) const
{
    assert(tfmda.size() == 1);
    fef::MatchData::UP childrenMatchData = _layout.createMatchData();
    wand::Terms terms;
    terms.reserve(_terms.size());
    for (size_t i = 0; i < _terms.size(); ++i) {
        const State &childState = _terms[i]->getState();
        assert(childState.numFields() == 1);
        // TODO: pass ownership with unique_ptr
        terms.emplace_back(_terms[i]->createSearch(*childrenMatchData).release(),
                           _weights[i],
                           childState.estimate().estHits,
                           childState.field(0).resolve(*childrenMatchData));
    }
    bool readonly_scores_heap = (_matching_phase != MatchingPhase::FIRST_PHASE);
    return ParallelWeakAndSearch::create(terms,
                                         ParallelWeakAndSearch::MatchParams(*_scores, _scoreThreshold, _thresholdBoostFactor,
                                                                            _scoresAdjustFrequency, get_docid_limit()),
                                         ParallelWeakAndSearch::RankParams(*tfmda[0],std::move(childrenMatchData)),
                                         strict(), readonly_scores_heap);
}

std::unique_ptr<SearchIterator>
ParallelWeakAndBlueprint::createFilterSearchImpl(FilterConstraint constraint) const
{
    return create_atmost_or_filter(_terms, constraint);
}

void
ParallelWeakAndBlueprint::fetchPostings(const ExecuteInfo & execInfo)
{
    for (const auto & _term : _terms) {
        _term->fetchPostings(execInfo);
    }
}

bool
ParallelWeakAndBlueprint::always_needs_unpack() const
{
    return true;
}

void
ParallelWeakAndBlueprint::set_matching_phase(MatchingPhase matching_phase) noexcept
{
    _matching_phase = matching_phase;
    if (matching_phase != MatchingPhase::FIRST_PHASE) {
        /*
         * During first phase matching, the scores heap is adjusted by
         * the iterators. The minimum score is increased when the
         * scores heap is full while handling a matching document with
         * a higher score than the worst existing one.
         *
         * During later matching phases, only the original minimum
         * score is used, and the heap is not updated by the
         * iterators. This ensures that all documents considered a hit
         * by the first phase matching will also be considered as hits
         * by the later matching phases.
         */
        _scores->set_min_score(_scoreThreshold);
    }
}

void
ParallelWeakAndBlueprint::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    LeafBlueprint::visitMembers(visitor);
    visit(visitor, "_weights", _weights);
    visit(visitor, "_terms", _terms);
}

}
