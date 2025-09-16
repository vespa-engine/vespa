// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "same_element_blueprint.h"
#include "same_element_search.h"
#include "field_spec.hpp"
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/vespalib/objects/visit.hpp>
#include <algorithm>
#include <cassert>
#include <map>

namespace search::queryeval {

SameElementBlueprint::SameElementBlueprint(const FieldSpec &field, fef::MatchDataLayout subtree_mdl, bool expensive)
    : IntermediateBlueprint(),
      _layout(std::move(subtree_mdl)),
      _field_name(field.getName()),
      _handle(field.getHandle()),
      _expensive(expensive)
{
}

SameElementBlueprint::~SameElementBlueprint() = default;

AnyFlow
SameElementBlueprint::my_flow(InFlow in_flow) const
{
    return AnyFlow::create<AndFlow>(in_flow);
}

FlowStats
SameElementBlueprint::calculate_flow_stats(uint32_t) const
{
    auto& children = get_children();
    double est = AndFlow::estimate_of(children);
    return {est,
            AndFlow::cost_of(children, false) + est * children.size(),
            AndFlow::cost_of(children, true) + est * children.size()};
}

void
SameElementBlueprint::optimize_self(OptimizePass pass)
{
    (void) pass;
}

uint8_t
SameElementBlueprint::calculate_cost_tier() const
{
    uint8_t cost_tier = State::COST_TIER_MAX;
    auto& children = get_children();
    for (auto& child : children) {
        cost_tier = std::min(cost_tier, child->getState().cost_tier());
    }
    if (_expensive) {
        cost_tier = std::max(cost_tier, State::COST_TIER_EXPENSIVE);
    }
    return cost_tier;
}

std::unique_ptr<SearchIterator>
SameElementBlueprint::createSearchImpl(fef::MatchData& md) const
{
    auto* tfmd = md.resolveTermField(_handle);
    assert(tfmd != nullptr);
    return create_same_element_search(*tfmd);
}

Blueprint::HitEstimate
SameElementBlueprint::combine(const std::vector<HitEstimate>& data) const
{
    return min(data);
}

FieldSpecBaseList SameElementBlueprint::exposeFields() const
{
    return {};
}

void SameElementBlueprint::sort(Children& children, InFlow in_flow) const
{
    if (opt_sort_by_cost()) {
        AndFlow::sort(children, in_flow.strict());
        if (opt_allow_force_strict()) {
            AndFlow::reorder_for_extra_strictness(children, in_flow, 3);
        }
    } else {
        std::sort(children.begin(), children.end(), TieredLessEstimate());
    }
}

std::unique_ptr<SearchIterator>
SameElementBlueprint::createIntermediateSearch(MultiSearch::Children, fef::MatchData&) const
{
    abort(); // match data for subtree (subtree_md) must be owned by search iterator
}

std::unique_ptr<SameElementSearch>
SameElementBlueprint::create_same_element_search(search::fef::TermFieldMatchData& tfmd) const
{
    auto subtree_md = _layout.createMatchData();
    MultiSearch::Children sub_searches;
    auto& children = get_children();
    sub_searches.reserve(children.size());
    for (const auto & child : children) {
        sub_searches.push_back(child->createSearch(*subtree_md));
    }
    // match data for subtree (subtree_md) must be owned by search iterator
    return std::make_unique<SameElementSearch>(tfmd, std::move(subtree_md), std::move(sub_searches), strict());
}

SearchIterator::UP
SameElementBlueprint::createFilterSearchImpl(FilterConstraint constraint) const
{
    return create_atmost_and_filter(get_children(), strict(), constraint);
}

void
SameElementBlueprint::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    IntermediateBlueprint::visitMembers(visitor);
}

}
