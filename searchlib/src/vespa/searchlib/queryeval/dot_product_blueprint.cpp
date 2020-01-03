// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dot_product_blueprint.h"
#include "dot_product_search.h"
#include <vespa/vespalib/objects/visit.hpp>

namespace search::queryeval {

DotProductBlueprint::DotProductBlueprint(const FieldSpec &field)
    : ComplexLeafBlueprint(field),
      _estimate(),
      _layout(),
      _weights(),
      _terms()
{
}

DotProductBlueprint::~DotProductBlueprint()
{
    while (!_terms.empty()) {
        delete _terms.back();
        _terms.pop_back();
    }
}

FieldSpec
DotProductBlueprint::getNextChildField(const FieldSpec &outer)
{
    return FieldSpec(outer.getName(), outer.getFieldId(), _layout.allocTermField(outer.getFieldId()), false);
}

void
DotProductBlueprint::addTerm(Blueprint::UP term, int32_t weight)
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
DotProductBlueprint::createLeafSearch(const search::fef::TermFieldMatchDataArray &tfmda,
                                      bool) const
{
    assert(tfmda.size() == 1);
    fef::MatchData::UP md = _layout.createMatchData();
    std::vector<fef::TermFieldMatchData*> childMatch;
    std::vector<SearchIterator*> children(_terms.size());
    for (size_t i = 0; i < _terms.size(); ++i) {
        const State &childState = _terms[i]->getState();
        assert(childState.numFields() == 1);
        childMatch.push_back(childState.field(0).resolve(*md));
        children[i] = _terms[i]->createSearch(*md, true).release();
    }
    return DotProductSearch::create(children, *tfmda[0], childMatch, _weights, std::move(md));
}

void
DotProductBlueprint::fetchPostings(bool strict)
{
    (void) strict;
    for (size_t i = 0; i < _terms.size(); ++i) {
        _terms[i]->fetchPostings(true);
    }    
}

void
DotProductBlueprint::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    LeafBlueprint::visitMembers(visitor);
    visit(visitor, "_weights", _weights);
    visit(visitor, "_terms", _terms);
}

}
