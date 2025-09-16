// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "same_element_blueprint.h"
#include "same_element_search.h"
#include "field_spec.hpp"
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/vespalib/objects/visit.hpp>
#include <algorithm>
#include <map>

namespace search::queryeval {

SameElementBlueprint::SameElementBlueprint(const FieldSpec &field, fef::MatchDataLayout subtree_mdl, bool expensive)
    : ComplexLeafBlueprint(field),
      _estimate(),
      _layout(std::move(subtree_mdl)),
      _children(),
      _field_name(field.getName())
{
    if (expensive) {
        set_cost_tier(State::COST_TIER_EXPENSIVE);
    }
}

SameElementBlueprint::~SameElementBlueprint() = default;

void
SameElementBlueprint::add_child(Blueprint::UP child)
{
    const State &childState = child->getState();
    HitEstimate childEst = childState.estimate();
    if (_children.empty() ||  (childEst < _estimate)) {
        _estimate = childEst;
        setEstimate(_estimate);
    }
    _children.push_back(std::move(child));
}

void
SameElementBlueprint::sort(InFlow in_flow)
{
    resolve_strict(in_flow);
    auto flow = AndFlow(in_flow);
    for (auto &child: _children) {
        child->sort(InFlow(flow.strict(), flow.flow()));
        flow.add(child->estimate());
    }
}

FlowStats
SameElementBlueprint::calculate_flow_stats(uint32_t docid_limit) const
{
    for (auto &child : _children) {
        child->update_flow_stats(docid_limit);
    }
    double est = AndFlow::estimate_of(_children);
    return {est,
            AndFlow::cost_of(_children, false) + est * _children.size(),
            AndFlow::cost_of(_children, true) + est * _children.size()};
}

void
SameElementBlueprint::optimize_self(OptimizePass pass)
{
    if (pass == OptimizePass::LAST) {
        std::sort(_children.begin(), _children.end(),
                  [](const auto &a, const auto &b) {
                      return (a->getState().estimate() < b->getState().estimate());
                  });
    }
}

void
SameElementBlueprint::fetchPostings(const ExecuteInfo &execInfo)
{
    if (_children.empty()) {
        return;
    }
    _children[0]->fetchPostings(execInfo);
    double hit_rate = execInfo.hit_rate() * _children[0]->estimate();
    for (size_t i = 1; i < _children.size(); ++i) {
        Blueprint& child = *_children[i];
        child.fetchPostings(ExecuteInfo::create(hit_rate, execInfo));
        hit_rate = hit_rate * _children[i]->estimate();
    }
}

std::unique_ptr<SameElementSearch>
SameElementBlueprint::create_same_element_search(search::fef::TermFieldMatchData& tfmd) const
{
    fef::MatchData::UP md = _layout.createMatchData();
    std::vector<std::unique_ptr<SearchIterator>> search_children;
    search_children.reserve(_children.size());
    for (size_t i = 0; i < _children.size(); ++i) {
        search_children.emplace_back(_children[i]->createSearch(*md));
    }
    return std::make_unique<SameElementSearch>(tfmd, std::move(md), std::move(search_children), strict());
}

SearchIterator::UP
SameElementBlueprint::createLeafSearch(const search::fef::TermFieldMatchDataArray &tfmda) const
{
    assert(tfmda.size() == 1);
    return create_same_element_search(*tfmda[0]);
}

SearchIterator::UP
SameElementBlueprint::createFilterSearchImpl(FilterConstraint constraint) const
{
    return create_atmost_and_filter(_children, strict(), constraint);
}

void
SameElementBlueprint::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    ComplexLeafBlueprint::visitMembers(visitor);
    visit(visitor, "children", _children);
}

}
