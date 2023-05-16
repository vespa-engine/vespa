// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "parallel_weak_and_blueprint.h"
#include "wand_parts.h"
#include "parallel_weak_and_search.h"
#include <vespa/searchlib/queryeval/field_spec.hpp>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/vespalib/objects/visit.hpp>
#include <algorithm>

namespace search::queryeval {

ParallelWeakAndBlueprint::ParallelWeakAndBlueprint(const FieldSpec &field,
                                                   uint32_t scoresToTrack,
                                                   score_t scoreThreshold,
                                                   double thresholdBoostFactor)
    : ComplexLeafBlueprint(field),
      _field(field),
      _scores(scoresToTrack),
      _scoreThreshold(scoreThreshold),
      _thresholdBoostFactor(thresholdBoostFactor),
      _scoresAdjustFrequency(DEFAULT_PARALLEL_WAND_SCORES_ADJUST_FREQUENCY),
      _estimate(),
      _layout(),
      _weights(),
      _terms()
{
}

ParallelWeakAndBlueprint::ParallelWeakAndBlueprint(const FieldSpec &field,
                                                   uint32_t scoresToTrack,
                                                   score_t scoreThreshold,
                                                   double thresholdBoostFactor,
                                                   uint32_t scoresAdjustFrequency)
    : ComplexLeafBlueprint(field),
      _field(field),
      _scores(scoresToTrack),
      _scoreThreshold(scoreThreshold),
      _thresholdBoostFactor(thresholdBoostFactor),
      _scoresAdjustFrequency(scoresAdjustFrequency),
      _estimate(),
      _layout(),
      _weights(),
      _terms()
{
}

ParallelWeakAndBlueprint::~ParallelWeakAndBlueprint() = default;

FieldSpec
ParallelWeakAndBlueprint::getNextChildField(const FieldSpec &outer)
{
    return FieldSpec(outer.getName(), outer.getFieldId(), _layout.allocTermField(outer.getFieldId()), false);
}

void
ParallelWeakAndBlueprint::reserve(size_t num_children) {
    _weights.reserve(num_children);
    _terms.reserve(num_children);
}

void
ParallelWeakAndBlueprint::addTerm(Blueprint::UP term, int32_t weight)
{
    HitEstimate childEst = term->getState().estimate();
    if (!childEst.empty) {
        if (_estimate.empty) {
            _estimate = childEst;
        } else {
            _estimate.estHits += childEst.estHits;
        }
        setEstimate(_estimate);
    }
    _weights.push_back(weight);
    _terms.push_back(std::move(term));
    set_tree_size(_terms.size() + 1);
}

SearchIterator::UP
ParallelWeakAndBlueprint::createLeafSearch(const search::fef::TermFieldMatchDataArray &tfmda, bool strict) const
{
    assert(tfmda.size() == 1);
    fef::MatchData::UP childrenMatchData = _layout.createMatchData();
    wand::Terms terms;
    terms.reserve(_terms.size());
    for (size_t i = 0; i < _terms.size(); ++i) {
        const State &childState = _terms[i]->getState();
        assert(childState.numFields() == 1);
        // TODO: pass ownership with unique_ptr
        terms.push_back(wand::Term(_terms[i]->createSearch(*childrenMatchData, true).release(),
                                   _weights[i],
                                   childState.estimate().estHits,
                                   childState.field(0).resolve(*childrenMatchData)));
    }
    return SearchIterator::UP
        (ParallelWeakAndSearch::create(terms,
                                       ParallelWeakAndSearch::MatchParams(_scores,
                                               _scoreThreshold,
                                               _thresholdBoostFactor,
                                               _scoresAdjustFrequency).setDocIdLimit(get_docid_limit()),
                                       ParallelWeakAndSearch::RankParams(*tfmda[0],
                                               std::move(childrenMatchData)), strict));
}

std::unique_ptr<SearchIterator>
ParallelWeakAndBlueprint::createFilterSearch(bool strict, FilterConstraint constraint) const
{
    return create_atmost_or_filter(_terms, strict, constraint);
}

void
ParallelWeakAndBlueprint::fetchPostings(const ExecuteInfo & execInfo)
{
    ExecuteInfo childInfo = ExecuteInfo::create(true, execInfo.hitRate());
    for (size_t i = 0; i < _terms.size(); ++i) {
        _terms[i]->fetchPostings(childInfo);
    }
}

bool
ParallelWeakAndBlueprint::always_needs_unpack() const
{
    return true;
}

void
ParallelWeakAndBlueprint::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    LeafBlueprint::visitMembers(visitor);
    visit(visitor, "_weights", _weights);
    visit(visitor, "_terms", _terms);
}

}
