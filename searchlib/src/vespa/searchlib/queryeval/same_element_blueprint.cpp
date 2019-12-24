// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "same_element_blueprint.h"
#include "same_element_search.h"
#include "field_spec.hpp"
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/attribute/elementiterator.h>
#include <vespa/vespalib/objects/visit.hpp>
#include <algorithm>
#include <map>

namespace search::queryeval {

SameElementBlueprint::SameElementBlueprint(const vespalib::string &struct_field_name_in, bool expensive)
    : ComplexLeafBlueprint(FieldSpecBaseList()),
      _estimate(),
      _layout(),
      _terms(),
      _struct_field_name(struct_field_name_in)
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
              [](const auto &a, const auto &b)
              {
                  return (a->getState().estimate() < b->getState().estimate());
              });
}

void
SameElementBlueprint::fetchPostings(const ExecuteInfo &execInfo)
{
    for (size_t i = 0; i < _terms.size(); ++i) {
        ExecuteInfo childInfo(execInfo.isStrict() && (i == 0), execInfo.hitRate());
        _terms[i]->fetchPostings(childInfo);
    }
}

std::unique_ptr<SameElementSearch>
SameElementBlueprint::create_same_element_search(bool strict) const
{
    fef::MatchDataLayout my_layout = _layout;
    std::vector<fef::TermFieldHandle> extra_handles;
    for (size_t i = 0; i < _terms.size(); ++i) {
        const State &childState = _terms[i]->getState();
        assert(childState.numFields() == 1);
        extra_handles.push_back(my_layout.allocTermField(childState.field(0).getFieldId()));
    }
    fef::MatchData::UP md = my_layout.createMatchData();
    search::fef::TermFieldMatchDataArray childMatch;
    std::vector<SearchIterator::UP> children(_terms.size());
    for (size_t i = 0; i < _terms.size(); ++i) {
        const State &childState = _terms[i]->getState();
        SearchIterator::UP child = _terms[i]->createSearch(*md, (strict && (i == 0)));
        const attribute::ISearchContext *context = _terms[i]->get_attribute_search_context();
        if (context == nullptr) {
            children[i] = std::move(child);
            childMatch.add(childState.field(0).resolve(*md));
        } else {
            fef::TermFieldMatchData *child_tfmd = md->resolveTermField(extra_handles[i]);
            children[i] = std::make_unique<attribute::ElementIterator>(std::move(child), *context, *child_tfmd);
            childMatch.add(child_tfmd);
        }
    }
    return std::make_unique<SameElementSearch>(std::move(md), std::move(children), childMatch, strict);
}

SearchIterator::UP
SameElementBlueprint::createLeafSearch(const search::fef::TermFieldMatchDataArray &tfmda,
                                       bool strict) const
{
    (void) tfmda;
    assert(!tfmda.valid());
    return create_same_element_search(strict);
}

void
SameElementBlueprint::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    ComplexLeafBlueprint::visitMembers(visitor);
    visit(visitor, "terms", _terms);
}

}
