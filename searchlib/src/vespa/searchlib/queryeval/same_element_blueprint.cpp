// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "same_element_blueprint.h"
#include "same_element_search.h"
#include "field_spec.hpp"
#include "andsearch.h"
#include "emptysearch.h"
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/attribute/searchcontextelementiterator.h>
#include <vespa/vespalib/objects/visit.hpp>
#include <algorithm>
#include <map>

namespace search::queryeval {

SameElementBlueprint::SameElementBlueprint(const FieldSpec &field, bool expensive)
    : ComplexLeafBlueprint(field),
      _estimate(),
      _layout(),
      _terms(),
      _field_name(field.getName())
{
    if (expensive) {
        set_cost_tier(State::COST_TIER_EXPENSIVE);
    }
}

SameElementBlueprint::~SameElementBlueprint() = default;

FieldSpec
SameElementBlueprint::getNextChildField(const vespalib::string &field_name, uint32_t field_id)
{
    return FieldSpec(field_name, field_id, _layout.allocTermField(field_id), false);
}

void
SameElementBlueprint::addTerm(Blueprint::UP term)
{
    const State &childState = term->getState();
    assert(childState.numFields() == 1);
    HitEstimate childEst = childState.estimate();
    if (_terms.empty() ||  (childEst < _estimate)) {
        _estimate = childEst;
        setEstimate(_estimate);
    }
    _terms.push_back(std::move(term));
}

void
SameElementBlueprint::optimize_self()
{
    std::sort(_terms.begin(), _terms.end(),
              [](const auto &a, const auto &b) {
                  return (a->getState().estimate() < b->getState().estimate());
              });
}

void
SameElementBlueprint::fetchPostings(const ExecuteInfo &execInfo)
{
    for (size_t i = 0; i < _terms.size(); ++i) {
        _terms[i]->fetchPostings(ExecuteInfo::create(execInfo.isStrict() && (i == 0), execInfo.hitRate()));
    }
}

std::unique_ptr<SameElementSearch>
SameElementBlueprint::create_same_element_search(search::fef::TermFieldMatchData& tfmd, bool strict) const
{
    fef::MatchDataLayout my_layout = _layout;
    fef::MatchData::UP md = my_layout.createMatchData();
    std::vector<ElementIterator::UP> children(_terms.size());
    for (size_t i = 0; i < _terms.size(); ++i) {
        const State &childState = _terms[i]->getState();
        SearchIterator::UP child = _terms[i]->createSearch(*md, (strict && (i == 0)));
        const attribute::ISearchContext *context = _terms[i]->get_attribute_search_context();
        if (context == nullptr) {
            children[i] = std::make_unique<ElementIteratorWrapper>(std::move(child), *childState.field(0).resolve(*md));
        } else {
            children[i] = std::make_unique<attribute::SearchContextElementIterator>(std::move(child), *context);
        }
    }
    return std::make_unique<SameElementSearch>(tfmd, std::move(md), std::move(children), strict);
}

SearchIterator::UP
SameElementBlueprint::createLeafSearch(const search::fef::TermFieldMatchDataArray &tfmda, bool strict) const
{
    assert(tfmda.size() == 1);
    return create_same_element_search(*tfmda[0], strict);
}

SearchIterator::UP
SameElementBlueprint::createFilterSearch(bool strict, FilterConstraint constraint) const
{
    return create_atmost_and_filter(_terms, strict, constraint);
}

void
SameElementBlueprint::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    ComplexLeafBlueprint::visitMembers(visitor);
    visit(visitor, "terms", _terms);
}

}
