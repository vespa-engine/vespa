// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "correlate_by_blueprint.h"
#include "emptysearch.h"
#include "flow.h"
#include "leaf_blueprints.h"
#include "same_element_blueprint.h"

#include <vespa/vespalib/util/issue.h>

#include <algorithm>
#include <stdexcept>

namespace search::queryeval {

CorrelateByBlueprint::CorrelateByBlueprint(std::vector<std::optional<std::vector<std::string>>> domains)
    : _clauses(), _clause_of_child(), _first_present(), _domains(std::move(domains)), _label(), _columns() {}
CorrelateByBlueprint::~CorrelateByBlueprint() = default;

void CorrelateByBlueprint::add_first_present(uint32_t clause, uint32_t presence, uint32_t column,
                                            std::vector<std::string> values) {
    if (clause >= _clauses.size() || presence >= _clauses.size()) {
        throw std::invalid_argument("firstPresent refers to a clause that does not exist");
    }
    _first_present.push_back({clause, presence, column, std::move(values)});
}

void CorrelateByBlueprint::set_correlation_identity(std::string label, std::map<std::string, uint32_t> columns) {
    _label = std::move(label);
    _columns = std::move(columns);
}

void CorrelateByBlueprint::add_clause(Blueprint::UP child, std::vector<BoundAttribute> bindings,
                                     std::vector<ExactAttributeValues> exact_values) {
    // The search needs the clause's SameElementSearch, so optimize must keep the clause.
    if (auto* same_element = dynamic_cast<SameElementBlueprint*>(child.get())) {
        same_element->disallow_replacement();
    }
    _clause_of_child.push_back(_clauses.size());
    _clauses.push_back({std::move(bindings), std::move(exact_values)});
    addChild(std::move(child));
}

std::vector<bool> CorrelateByBlueprint::uncased_columns() const {
    std::vector<std::vector<BoundAttribute>> bindings;
    bindings.reserve(_clauses.size());
    for (const auto& clause : _clauses) bindings.push_back(clause.bindings);
    return find_uncased_columns(_domains.size(), bindings);
}

AnyFlow CorrelateByBlueprint::my_flow(InFlow in_flow) const { return AnyFlow::create<AndFlow>(in_flow); }
uint8_t CorrelateByBlueprint::calculate_cost_tier() const { return State::COST_TIER_EXPENSIVE; }
Blueprint::HitEstimate CorrelateByBlueprint::combine(const std::vector<HitEstimate>& estimates) const { return min(estimates); }
FieldSpecBaseList CorrelateByBlueprint::exposeFields() const { return {}; }
bool CorrelateByBlueprint::always_needs_unpack() const { return true; }

void CorrelateByBlueprint::sort(Children& children, InFlow in_flow) const {
    std::vector<const Blueprint*> before;
    before.reserve(children.size());
    for (const auto& child : children) before.push_back(child.get());
    if (opt_sort_by_cost()) AndFlow::sort(children, in_flow.strict());
    else std::sort(children.begin(), children.end(), TieredLessEstimate());
    // Sorting moves children but never replaces them, so their addresses find their old positions.
    std::vector<uint32_t> clause_of_child(children.size());
    for (size_t i = 0; i < children.size(); ++i) {
        size_t old_position = std::find(before.begin(), before.end(), children[i].get()) - before.begin();
        clause_of_child[i] = _clause_of_child[old_position];
    }
    _clause_of_child = std::move(clause_of_child);
}

FlowStats CorrelateByBlueprint::calculate_flow_stats(uint32_t) const {
    const auto& children = get_children();
    double estimate = AndFlow::estimate_of(children);
    // For each candidate document the search reads every bound attribute and joins
    // the rows of every clause. Array lengths are not known when planning, so this
    // charges one unit per clause and one per bound or filtered attribute. It is a
    // lower bound: long arrays make each candidate more expensive.
    size_t attribute_reads = 0;
    for (const auto& clause : _clauses) attribute_reads += clause.bindings.size() + clause.exact_values.size();
    double per_candidate = children.size() + attribute_reads;
    return {estimate, AndFlow::cost_of(children, false) + estimate * per_candidate,
            AndFlow::cost_of(children, true) + estimate * per_candidate};
}

bool CorrelateByBlueprint::has_empty_child() const {
    return std::any_of(get_children().begin(), get_children().end(), [](const auto& child) {
        return dynamic_cast<const EmptyBlueprint*>(child.get()) != nullptr;
    });
}

SearchIterator::UP CorrelateByBlueprint::createSearchImpl(fef::MatchData& md) const {
    if (auto search = create_correlate_search(md)) return SearchIterator::UP(std::move(search));
    // An empty clause matches nothing, which is not an error.
    if (!has_empty_child()) {
        vespalib::Issue::report("correlateBy could not build the search for its sameElement clauses, "
                                "so it matches no documents");
    }
    return std::make_unique<EmptySearch>();
}

std::unique_ptr<CorrelateBySearch> CorrelateByBlueprint::create_correlate_search(fef::MatchData& md) const {
    const auto& children = get_children();
    if (children.size() != _clauses.size() || _clause_of_child.size() != children.size()) return {};
    std::vector<std::unique_ptr<SameElementSearch>> searches;
    std::vector<std::vector<BoundAttribute>> bindings;
    std::vector<std::vector<ExactAttributeValues>> exact_values;
    std::vector<uint32_t> position_of_clause(_clauses.size());
    for (size_t i = 0; i < children.size(); ++i) {
        auto* same_element = dynamic_cast<const SameElementBlueprint*>(children[i].get());
        if (!same_element) return {};
        auto* tfmd = md.resolveTermField(same_element->get_field().getHandle());
        if (!tfmd) return {};
        const auto& clause = _clauses[_clause_of_child[i]];
        searches.push_back(same_element->create_same_element_search(md, *tfmd));
        bindings.push_back(clause.bindings);
        exact_values.push_back(clause.exact_values);
        position_of_clause[_clause_of_child[i]] = i;
    }
    std::vector<FirstPresentSelection> first_present;
    for (const auto& selection : _first_present) {
        first_present.push_back({position_of_clause[selection.clause], position_of_clause[selection.presence],
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
