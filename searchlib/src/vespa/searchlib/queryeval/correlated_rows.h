// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstddef>
#include <cstdint>
#include <span>
#include <string_view>
#include <variant>
#include <vector>
#include <vespa/searchlib/common/correlation_bindings.h>
#include <vespa/vespalib/util/doom.h>

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

/** Why the last evaluation stopped before it was complete. */
enum class CorrelationStop { none, deadline, memory_limit };

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
 *
 * When three or more clauses share a variable, a join can try many values of
 * other variables before it finds that the shared variable has no common
 * value. So a join of three or more clauses checks once that every shared
 * variable has a value found in all its clauses. When one has none, the join
 * ends without a match, and stop_reason() is none. has_match checks at the
 * first check point after shared_values_check_rows rows, since it decides for
 * every candidate document. find_matches checks only after
 * deadline_check_interval rows, since it reads documents that already matched.
 * Most small joins finish before the check, so they do not pay for it.
 *
 * A cyclic join can take long even when it finds nothing, and independent
 * variables can multiply into very many matches. The join therefore checks the
 * deadline every 1024 visited rows, counted across evaluations so that many
 * small documents are checked too. Reading and sorting the clause rows before
 * the join is not checked. has_match uses the soft deadline, which ends
 * matching. find_matches uses the hard deadline, since grouping and ranking
 * still read the bindings of matched documents after matching has ended.
 * find_matches also stops when its matches fill the memory limit and removing
 * duplicate matches frees less than half of it. A stopped evaluation returns
 * no match and no bindings, and stop_reason() tells why.
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
    vespalib::Doom _doom;
    size_t _memory_limit;
    size_t _match_limit;
    bool _collect;
    // Whether this evaluation still has to check that shared variables have common values.
    bool _check_shared_values;
    // Set when that check found a shared variable without a common value, so the join cannot match.
    bool _no_common_values;
    CorrelationStop _stop;
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
    // Values of one shared variable, kept between documents like the buffers above.
    std::vector<BindingValue> _common_values;
    std::vector<BindingValue> _table_values;
    std::vector<BindingValue> _intersection;
    BindingRow _binding;
    size_t _projection_depth;
    // Rows visited by all evaluations, and by the ones before the current evaluation.
    size_t _total_visited_rows;
    size_t _visited_rows_before;

    bool evaluate(std::span<BindingTable> tables, bool collect, std::span<const uint32_t> projection);
    void project_tables(std::span<const BindingTable> tables);
    bool shared_values_overlap();
    void plan_join_order(bool has_projection);
    // Returns true when the columns moved, so the rows must be sorted again.
    bool put_lookup_columns_first(WorkTable& table);
    void sort_and_deduplicate(WorkTable& table);
    bool extend(size_t depth, bool exists_only, std::span<const uint32_t> projection);
    // Returns false, without adding the match, when the distinct matches would pass the memory limit.
    bool append_match(std::span<const uint32_t> projection);
    void sort_and_deduplicate_matches();
    // Called every shared_values_check_rows visited rows. Checks the shared values or the
    // deadline when they are due, and returns true to end the join, because the join cannot
    // match or because the deadline has passed.
    bool at_check_point();
    bool interrupted() const noexcept { return _stop != CorrelationStop::none || _no_common_values; }

public:
    /** Collected matches of one evaluation may use about this many bytes. */
    static constexpr size_t default_memory_limit = size_t(64) << 20;
    /** The deadline is checked once per this many visited rows, counted across evaluations. */
    static constexpr size_t deadline_check_interval = 1024;
    /** The join calls its check point once per this many visited rows, counted across evaluations. */
    static constexpr size_t shared_values_check_rows = 64;

    explicit CorrelatedRows(uint32_t column_count, const vespalib::Doom& doom = vespalib::Doom::never(),
                            size_t memory_limit = default_memory_limit);
    ~CorrelatedRows();

    /** Stops at the first complete binding, without collecting other bindings. */
    bool has_match(std::span<BindingTable> tables);

    /** Returns all distinct complete bindings for grouping and summaries. */
    const std::vector<BindingRow>& find_matches(std::span<BindingTable> tables);

    /** Returns distinct projected bindings, checking the remaining variables for existence only. */
    const std::vector<BindingRow>& find_matches(std::span<BindingTable> tables, std::span<const uint32_t> projection);

    /** Number of clause rows the last evaluation looked at while joining. Used by tests to bound the work. */
    size_t visited_rows() const noexcept { return _total_visited_rows - _visited_rows_before; }

    /** Why the last evaluation stopped early, or none when it completed. */
    CorrelationStop stop_reason() const noexcept { return _stop; }
    size_t memory_limit() const noexcept { return _memory_limit; }
};

} // namespace search::queryeval
