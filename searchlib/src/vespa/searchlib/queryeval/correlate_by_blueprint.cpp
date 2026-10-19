// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "correlate_by_blueprint.h"
#include "emptysearch.h"
#include "flow.h"
#include "same_element_blueprint.h"

#include <algorithm>
#include <stdexcept>

namespace search::queryeval {

CorrelateByBlueprint::CorrelateByBlueprint(std::vector<std::optional<std::vector<std::string>>> domains)
    : _bindings(), _domains(std::move(domains)) {}
CorrelateByBlueprint::~CorrelateByBlueprint() = default;

void CorrelateByBlueprint::add_first_present(uint32_t clause, uint32_t presence, uint32_t column,
                                            std::vector<std::string> values) {
    _first_present.push_back({&getChild(clause), &getChild(presence), column, std::move(values)});
}

void CorrelateByBlueprint::set_correlation_identity(std::string label, std::map<std::string, uint32_t> columns) {
    _label = std::move(label);
    _columns = std::move(columns);
}

void CorrelateByBlueprint::add_clause(Blueprint::UP child, std::vector<BoundAttribute> bindings,
                                     std::vector<ExactAttributeValues> exact_values) {
    _bindings.emplace(child.get(), Clause{std::move(bindings), std::move(exact_values)});
    addChild(std::move(child));
}

AnyFlow CorrelateByBlueprint::my_flow(InFlow in_flow) const { return AnyFlow::create<AndFlow>(in_flow); }
uint8_t CorrelateByBlueprint::calculate_cost_tier() const { return State::COST_TIER_EXPENSIVE; }
Blueprint::HitEstimate CorrelateByBlueprint::combine(const std::vector<HitEstimate>& estimates) const { return min(estimates); }
FieldSpecBaseList CorrelateByBlueprint::exposeFields() const { return {}; }
bool CorrelateByBlueprint::always_needs_unpack() const { return true; }

void CorrelateByBlueprint::sort(Children& children, InFlow in_flow) const {
    if (opt_sort_by_cost()) AndFlow::sort(children, in_flow.strict());
    else std::sort(children.begin(), children.end(), TieredLessEstimate());
}

FlowStats CorrelateByBlueprint::calculate_flow_stats(uint32_t) const {
    const auto& children = get_children();
    double estimate = AndFlow::estimate_of(children);
    return {estimate, AndFlow::cost_of(children, false) + estimate * children.size(),
            AndFlow::cost_of(children, true) + estimate * children.size()};
}

SearchIterator::UP CorrelateByBlueprint::createSearchImpl(fef::MatchData& md) const {
    auto search = create_correlate_search(md);
    return search ? SearchIterator::UP(std::move(search)) : std::make_unique<EmptySearch>();
}

std::unique_ptr<CorrelateBySearch> CorrelateByBlueprint::create_correlate_search(fef::MatchData& md) const {
    std::vector<std::unique_ptr<SameElementSearch>> searches;
    std::vector<std::vector<BoundAttribute>> bindings;
    std::vector<std::vector<ExactAttributeValues>> exact_values;
    std::map<const Blueprint*, uint32_t> positions;
    for (const auto& child : get_children()) {
        auto* same_element = dynamic_cast<const SameElementBlueprint*>(child.get());
        auto bound = _bindings.find(child.get());
        if (!same_element || bound == _bindings.end()) return {};
        auto* tfmd = md.resolveTermField(same_element->get_field().getHandle());
        searches.push_back(same_element->create_same_element_search(md, *tfmd));
        bindings.push_back(bound->second.bindings);
        exact_values.push_back(bound->second.exact_values);
        positions.emplace(child.get(), searches.size() - 1);
    }
    std::vector<FirstPresentSelection> first_present;
    for (const auto& selection : _first_present) {
        first_present.push_back({positions.at(selection.clause), positions.at(selection.presence),
                                 selection.column, selection.values});
    }
    return std::make_unique<CorrelateBySearch>(std::move(searches), std::move(bindings), _domains, strict(),
                                              std::move(exact_values), std::move(first_present));
}

SearchIterator::UP CorrelateByBlueprint::createIntermediateSearch(MultiSearch::Children, fef::MatchData&) const {
    throw std::logic_error("correlateBy constructs its sameElement searches directly");
}

SearchIterator::UP CorrelateByBlueprint::createFilterSearchImpl(FilterConstraint constraint) const {
    return create_atmost_and_filter(get_children(), constraint);
}

} // namespace search::queryeval
