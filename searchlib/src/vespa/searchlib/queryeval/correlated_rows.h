// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstddef>
#include <cstdint>
#include <span>
#include <string_view>
#include <variant>
#include <vector>
#include <vespa/searchlib/common/correlation_bindings.h>

namespace search::queryeval {

using BindingValue = CorrelationValue;
using BindingRow = std::vector<BindingValue>;

/** Matching rows from one clause, projected onto its bound variables. */
struct BindingTable {
    std::vector<uint32_t> columns;
    std::vector<BindingRow> rows;
};

/**
 * Keeps candidates at the first priority value present for their other bindings.
 * Presence is evaluated independently of the candidate's value predicate: an
 * earlier present value that fails the predicate still suppresses later values.
 */
void select_first_present_rows(const BindingTable& presence, BindingTable& candidates,
                               uint32_t priority_column, std::span<const BindingValue> priority);

/**
 * Document-local natural join of matching rows. A column omitted by a clause is
 * unbound there, so a unit-only clause also applies to matching unit/zone pairs.
 * String views must remain valid throughout evaluation. Values are compared as
 * given, so callers fold the case of values that should match without case.
 *
 * Before joining, each clause keeps only the variables that another clause also
 * binds or that the caller asked for. A variable that only one clause binds
 * just has to exist, so it does not multiply the work. Each clause is then
 * sorted on the variables that are already bound when the join reaches it, so a
 * lookup never depends on how the variables are named or numbered.
 */
class CorrelatedRows {
    // The rows of one clause in a single buffer, with columns.size() values per row.
    // The first lookup_size columns are bound by earlier clauses in the join order.
    struct WorkTable {
        std::vector<uint32_t> columns;
        size_t lookup_size = 0;
        std::vector<BindingValue> values;
        size_t row_count() const noexcept { return values.size() / columns.size(); }
        const BindingValue* row(size_t index) const noexcept { return values.data() + index * columns.size(); }
    };

    uint32_t _column_count;
    std::vector<BindingRow> _matches;
    // Buffers kept between documents to avoid allocating for every candidate.
    std::vector<WorkTable> _work;
    size_t _work_size;
    std::vector<bool> _projected;
    std::vector<bool> _needed;
    std::vector<bool> _bound;
    std::vector<uint32_t> _binding_counts;
    std::vector<uint32_t> _row_order;
    std::vector<uint32_t> _column_positions;
    std::vector<uint32_t> _column_scratch;
    std::vector<BindingValue> _value_scratch;
    BindingRow _binding;
    size_t _projection_depth;
    size_t _visited_rows;

    bool evaluate(std::span<BindingTable> tables, bool collect, std::span<const uint32_t> projection);
    void project_tables(std::span<const BindingTable> tables);
    bool shared_values_overlap() const;
    void plan_join_order(bool has_projection);
    // Returns true when the columns moved, so the rows must be sorted again.
    bool put_lookup_columns_first(WorkTable& table);
    void sort_and_deduplicate(WorkTable& table);
    bool extend(size_t depth, bool exists_only, std::span<const uint32_t> projection);
    void append_match(std::span<const uint32_t> projection);

public:
    explicit CorrelatedRows(uint32_t column_count);
    ~CorrelatedRows();

    /** Stops at the first complete binding, without collecting other bindings. */
    bool has_match(std::span<BindingTable> tables);

    /** Returns all distinct complete bindings for grouping and summaries. */
    const std::vector<BindingRow>& find_matches(std::span<BindingTable> tables);

    /** Returns distinct projected bindings, checking the remaining variables for existence only. */
    const std::vector<BindingRow>& find_matches(std::span<BindingTable> tables, std::span<const uint32_t> projection);

    /** Number of clause rows the last evaluation looked at while joining. Used by tests to bound the work. */
    size_t visited_rows() const noexcept { return _visited_rows; }
};

} // namespace search::queryeval
