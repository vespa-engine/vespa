// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_phrase_blueprint.h"
#include "simple_phrase_search.h"
#include "emptysearch.h"
#include "field_spec.hpp"
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/vespalib/objects/visit.hpp>
#include <algorithm>
#include <map>

namespace search::queryeval {

SimplePhraseBlueprint::SimplePhraseBlueprint(const FieldSpec &field, bool expensive)
    : ComplexLeafBlueprint(field),
      _field(field),
      _estimate(),
      _layout(),
      _terms()
{
    if (expensive) {
        set_cost_tier(State::COST_TIER_EXPENSIVE);
    }
}

SimplePhraseBlueprint::~SimplePhraseBlueprint() = default;

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
    _terms.push_back(std::move(term));
}

SearchIterator::UP
SimplePhraseBlueprint::createLeafSearch(const fef::TermFieldMatchDataArray &tfmda, bool strict) const
{
    assert(tfmda.size() == 1);
    fef::MatchData::UP md = _layout.createMatchData();
    fef::TermFieldMatchDataArray childMatch;
    SimplePhraseSearch::Children children;
    children.reserve(_terms.size());
    std::multimap<uint32_t, uint32_t> order_map;
    for (size_t i = 0; i < _terms.size(); ++i) {
        const State &childState = _terms[i]->getState();
        assert(childState.numFields() == 1);
        auto *child_term_field_match_data = childState.field(0).resolve(*md);
        child_term_field_match_data->setNeedInterleavedFeatures(tfmda[0]->needs_interleaved_features());
        child_term_field_match_data->setNeedNormalFeatures(true);
        childMatch.add(child_term_field_match_data);
        children.push_back(_terms[i]->createSearch(*md, strict));
        order_map.insert(std::make_pair(childState.estimate().estHits, i));
    }
    std::vector<uint32_t> eval_order;
    eval_order.reserve(order_map.size());
    for (const auto & child : order_map) {
        eval_order.push_back(child.second);
    }    

    return std::make_unique<SimplePhraseSearch>(std::move(children),
                                                std::move(md), std::move(childMatch),
                                                std::move(eval_order), *tfmda[0], strict);
}

SearchIterator::UP
SimplePhraseBlueprint::createFilterSearch(bool strict, FilterConstraint constraint) const
{
    return create_atmost_and_filter(_terms, strict, constraint);
}

void
SimplePhraseBlueprint::fetchPostings(const ExecuteInfo &execInfo)
{
    for (auto & term : _terms) {
        term->fetchPostings(execInfo);
    }
}

void
SimplePhraseBlueprint::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    LeafBlueprint::visitMembers(visitor);
    visit(visitor, "terms", _terms);
}

}
