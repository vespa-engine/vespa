// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "same_element_blueprint.h"
#include "same_element_search.h"
#include "field_spec.hpp"
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/vespalib/objects/visit.hpp>
#include <algorithm>
#include <cassert>
#include <map>

using search::fef::MatchData;
using search::fef::TermFieldMatchData;

namespace search::queryeval {

SameElementBlueprint::SameElementBlueprint(const FieldSpec &field,
                                           const std::vector<search::fef::TermFieldHandle>& descendants_index_handles,
                                           bool expensive,
                                           std::vector<uint32_t> element_filter)
    : IntermediateBlueprint(),
      _field(field),
      _descendants_index_handles(descendants_index_handles),
      _expensive(expensive),
      _element_filter(std::move(element_filter))
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

bool
SameElementBlueprint::always_needs_unpack() const
{
    return true; // Need unpack to filter match data for descendants
};

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
SameElementBlueprint::createSearchImpl(MatchData& md) const
{
    auto* tfmd = md.resolveTermField(_field.getHandle());
    assert(tfmd != nullptr);
    return create_same_element_search(md, *tfmd);
}

Blueprint::HitEstimate
SameElementBlueprint::combine(const std::vector<HitEstimate>& data) const
{
    return min(data);
}

FieldSpecBaseList SameElementBlueprint::exposeFields() const
{
    FieldSpecBaseList fields;
    fields.add(_field);
    return fields;
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
SameElementBlueprint::createIntermediateSearch(MultiSearch::Children, MatchData&) const
{
    abort(); // Handled by createSearchImpl and create_same_element_search
}

std::unique_ptr<SameElementSearch>
SameElementBlueprint::create_same_element_search(MatchData& md, TermFieldMatchData& tfmd) const
{
    MultiSearch::Children sub_searches;
    auto& children = get_children();
    sub_searches.reserve(children.size());
    for (const auto & child : children) {
        sub_searches.push_back(child->createSearch(md));
    }
    std::vector<TermFieldMatchData*> descendants_index_tfmd;
    descendants_index_tfmd.reserve(_descendants_index_handles.size());
    for (auto handle : _descendants_index_handles) {
        descendants_index_tfmd.emplace_back(md.resolveTermField(handle));
    }
    // match data for subtree (subtree_md) must be owned by search iterator
    return std::make_unique<SameElementSearch>(tfmd, std::move(descendants_index_tfmd), std::move(sub_searches),
                                               strict(),
                                               _element_filter); // Copy element filter, do not move it
}

SearchIterator::UP
SameElementBlueprint::createFilterSearchImpl(FilterConstraint constraint) const
{
    return create_atmost_and_filter(get_children(), constraint);
}

void
SameElementBlueprint::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    IntermediateBlueprint::visitMembers(visitor);
}

}
