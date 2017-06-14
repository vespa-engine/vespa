// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_phrase_blueprint.h"
#include "simple_phrase_search.h"
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/vespalib/objects/visit.hpp>
#include <algorithm>
#include <map>

namespace search {
namespace queryeval {

SimplePhraseBlueprint::SimplePhraseBlueprint(const FieldSpec &field, const IRequestContext & requestContext)
    : ComplexLeafBlueprint(field),
      _doom(requestContext.getSoftDoom()),
      _field(field),
      _estimate(),
      _layout(),
      _terms()
{
}

SimplePhraseBlueprint::~SimplePhraseBlueprint()
{
    while (!_terms.empty()) {
        delete _terms.back();
        _terms.pop_back();
    }
}

FieldSpec
SimplePhraseBlueprint::getNextChildField(const FieldSpec &outer)
{
    return FieldSpec(outer.getName(), outer.getFieldId(), _layout.allocTermField(outer.getFieldId()), false);
}

void
SimplePhraseBlueprint::addTerm(Blueprint::UP term)
{
    const State &childState = term->getState();
    assert(childState.numFields() == 1);
    const FieldSpecBase &childField = childState.field(0);
    assert(childField.getFieldId() == _field.getFieldId());
    (void) childField;

    HitEstimate childEst = childState.estimate();
    if (_terms.empty() ||  childEst < _estimate) {
        _estimate = childEst;
    }
    setEstimate(_estimate);
    _terms.push_back(term.get());
    term.release();
}

SearchIterator::UP
SimplePhraseBlueprint::createLeafSearch(const search::fef::TermFieldMatchDataArray &tfmda,
                                        bool strict) const
{
    assert(tfmda.size() == 1);
    fef::MatchData::UP md = _layout.createMatchData();
    search::fef::TermFieldMatchDataArray childMatch;
    SimplePhraseSearch::Children children(_terms.size());
    std::multimap<uint32_t, uint32_t> order_map;
    for (size_t i = 0; i < _terms.size(); ++i) {
        const State &childState = _terms[i]->getState();
        assert(childState.numFields() == 1);
        childMatch.add(childState.field(0).resolve(*md));
        children[i] = _terms[i]->createSearch(*md, strict).release();
        order_map.insert(std::make_pair(childState.estimate().estHits, i));
    }
    std::vector<uint32_t> eval_order;
    for (std::multimap<uint32_t, uint32_t>::iterator
             it = order_map.begin(); it != order_map.end(); ++it) {
        eval_order.push_back(it->second);
    }
    
    SimplePhraseSearch * phrase = new SimplePhraseSearch(children, std::move(md), childMatch,
                                                         eval_order, *tfmda[0], strict);
    phrase->setDoom(& _doom);
    return SearchIterator::UP(phrase);
}


void
SimplePhraseBlueprint::fetchPostings(bool strict)
{
    for (size_t i = 0; i < _terms.size(); ++i) {
        _terms[i]->fetchPostings(strict);
    }
}

void
SimplePhraseBlueprint::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    LeafBlueprint::visitMembers(visitor);
    visit(visitor, "terms", _terms);
}

}  // namespace search::queryeval
}  // namespace search
