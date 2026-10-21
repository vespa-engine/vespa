// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "blueprint.h"
#include "correlate_by_search.h"
#include <map>

namespace search::queryeval {

class CorrelateByBlueprint : public IntermediateBlueprint {
    struct Clause {
        std::vector<BoundAttribute> bindings;
        std::vector<ExactAttributeValues> exact_values;
    };
    // Clauses in the order they were added. Optimize may replace children and
    // sort reorders them, so each child position maps to its clause by index.
    std::vector<Clause> _clauses;
    mutable std::vector<uint32_t> _clause_of_child;
    struct FirstPresent {
        uint32_t clause;
        uint32_t presence;
        uint32_t column;
        std::vector<std::string> values;
    };
    std::vector<FirstPresent> _first_present;
    std::vector<std::optional<std::vector<std::string>>> _domains;
    std::string _label;
    std::map<std::string, uint32_t> _columns;
    vespalib::Doom _doom;
    AnyFlow my_flow(InFlow in_flow) const override;
    bool has_empty_child() const;

public:
    /** The search bounds its joins by the deadline of the query, see CorrelateBySearch. */
    CorrelateByBlueprint(std::vector<std::optional<std::vector<std::string>>> domains, const vespalib::Doom& doom);
    ~CorrelateByBlueprint() override;
    void add_clause(Blueprint::UP child, std::vector<BoundAttribute> bindings,
                    std::vector<ExactAttributeValues> exact_values = {});
    void set_correlation_identity(std::string label, std::map<std::string, uint32_t> columns);
    /** Clause numbers count clauses in the order they were added. */
    void add_first_present(uint32_t clause, uint32_t presence, uint32_t column, std::vector<std::string> values);
    const std::string& label() const noexcept { return _label; }
    const vespalib::Doom& doom() const noexcept { return _doom; }
    const std::map<std::string, uint32_t>& columns() const noexcept { return _columns; }
    /** Whether each variable is compared without case, see find_uncased_columns. */
    std::vector<bool> uncased_columns() const;
    uint8_t calculate_cost_tier() const override;
    HitEstimate combine(const std::vector<HitEstimate>& estimates) const override;
    FieldSpecBaseList exposeFields() const override;
    void sort(Children& children, InFlow in_flow) const override;
    FlowStats calculate_flow_stats(uint32_t docid_limit) const override;
    bool always_needs_unpack() const override;
    SearchIteratorUP createSearchImpl(fef::MatchData& md) const override;
    /**
     * Builds the search with one SameElementSearch per child, in child order.
     * Returns nullptr when a child is no longer a sameElement blueprint.
     */
    std::unique_ptr<CorrelateBySearch> create_correlate_search(fef::MatchData& md) const;
    SearchIteratorUP createIntermediateSearch(MultiSearch::Children searches, fef::MatchData& md) const override;
    SearchIteratorUP createFilterSearchImpl(FilterConstraint constraint) const override;
};

} // namespace search::queryeval
