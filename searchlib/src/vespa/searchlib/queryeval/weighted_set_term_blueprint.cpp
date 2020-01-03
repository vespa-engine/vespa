// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "weighted_set_term_blueprint.h"
#include "weighted_set_term_search.h"
#include <vespa/vespalib/objects/visit.hpp>

namespace search::queryeval {

WeightedSetTermBlueprint::WeightedSetTermBlueprint(const FieldSpec &field)
    : ComplexLeafBlueprint(field),
      _estimate(),
      _layout(),
      _children_field(field.getName(), field.getFieldId(), _layout.allocTermField(field.getFieldId()), false),
      _weights(),
      _terms()
{
    set_allow_termwise_eval(true);
}

WeightedSetTermBlueprint::~WeightedSetTermBlueprint()
{
    while (!_terms.empty()) {
        delete _terms.back();
        _terms.pop_back();
    }
}

void
WeightedSetTermBlueprint::addTerm(Blueprint::UP term, int32_t weight)
{
    HitEstimate childEst = term->getState().estimate();
    if (! childEst.empty) {
        if (_estimate.empty) {
            _estimate = childEst;
        } else {
            _estimate.estHits += childEst.estHits;
        }
        setEstimate(_estimate);
    }
    _weights.push_back(weight);
    _terms.push_back(term.get());
    term.release();
}


SearchIterator::UP
WeightedSetTermBlueprint::createLeafSearch(const fef::TermFieldMatchDataArray &tfmda, bool) const
{
    assert(tfmda.size() == 1);
    fef::MatchData::UP md = _layout.createMatchData();
    std::vector<SearchIterator*> children(_terms.size());
    for (size_t i = 0; i < _terms.size(); ++i) {
        children[i] = _terms[i]->createSearch(*md, true).release();
    }
    return SearchIterator::UP(WeightedSetTermSearch::create(children, *tfmda[0], _weights, std::move(md)));
}

void
WeightedSetTermBlueprint::fetchPostings(const ExecuteInfo &execInfo)
{
    ExecuteInfo childInfo = ExecuteInfo::create(true, execInfo.hitRate());
    for (size_t i = 0; i < _terms.size(); ++i) {
        _terms[i]->fetchPostings(childInfo);
    }
}

void
WeightedSetTermBlueprint::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    LeafBlueprint::visitMembers(visitor);
    visit(visitor, "_weights", _weights);
    visit(visitor, "_terms", _terms);
}

}
