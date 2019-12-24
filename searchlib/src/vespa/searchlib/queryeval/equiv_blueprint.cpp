// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "equiv_blueprint.h"
#include "equivsearch.h"
#include <vespa/vespalib/objects/visit.hpp>

namespace search {
namespace queryeval {

EquivBlueprint::EquivBlueprint(const FieldSpecBaseList &fields,
                               fef::MatchDataLayout subtree_mdl)
    : ComplexLeafBlueprint(fields),
      _fields(fields),
      _estimate(),
      _layout(subtree_mdl),
      _terms(),
      _exactness()
{
}

EquivBlueprint::~EquivBlueprint()
{
}

SearchIterator::UP
EquivBlueprint::createLeafSearch(const search::fef::TermFieldMatchDataArray &outputs,
                                 bool strict) const
{
    fef::MatchData::UP md = _layout.createMatchData();
    MultiSearch::Children children(_terms.size());
    search::fef::TermMatchDataMerger::Inputs childMatch;
    for (size_t i = 0; i < _terms.size(); ++i) {
        const State &childState = _terms[i]->getState();
        for (size_t j = 0; j < childState.numFields(); ++j) {
            childMatch.emplace_back(childState.field(j).resolve(*md), _exactness[i]);
        }
        children[i] = _terms[i]->createSearch(*md, strict).release();
    }
    return SearchIterator::UP(EquivSearch::create(children, std::move(md), childMatch, outputs, strict));
}

void
EquivBlueprint::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    LeafBlueprint::visitMembers(visitor);
    visit(visitor, "terms", _terms);
}

void
EquivBlueprint::fetchPostings(const ExecuteInfo &execInfo)
{
    for (size_t i = 0; i < _terms.size(); ++i) {
        _terms[i]->fetchPostings(execInfo);
    }
}

EquivBlueprint&
EquivBlueprint::addTerm(Blueprint::UP term, double exactness)
{
    const State &childState = term->getState();

    HitEstimate childEst = childState.estimate();
    if (_terms.empty() || _estimate < childEst  ) {
        _estimate = childEst;
    }
    setEstimate(_estimate);
    _terms.push_back(std::move(term));
    _exactness.push_back(exactness);
    return *this;
}


} // namespace queryeval
} // namespace search
