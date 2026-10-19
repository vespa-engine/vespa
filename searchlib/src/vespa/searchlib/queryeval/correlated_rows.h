// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

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
 * String views must remain valid throughout evaluation and use exact equality.
 */
class CorrelatedRows {
    uint32_t _column_count;
    std::vector<BindingRow> _matches;

    bool evaluate(std::span<BindingTable> tables, bool collect, std::span<const uint32_t> projection);

public:
    explicit CorrelatedRows(uint32_t column_count);
    ~CorrelatedRows();

    /** Stops at the first complete binding, without collecting other bindings. */
    bool has_match(std::span<BindingTable> tables);

    /** Returns all distinct complete bindings for grouping and summaries. */
    const std::vector<BindingRow>& find_matches(std::span<BindingTable> tables);

    /** Returns distinct projected bindings, checking the remaining variables for existence only. */
    const std::vector<BindingRow>& find_matches(std::span<BindingTable> tables, std::span<const uint32_t> projection);
};

} // namespace search::queryeval
