// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "correlated_rows.h"

#include <algorithm>
#include <functional>
#include <map>
#include <numeric>
#include <stdexcept>

namespace search::queryeval {

namespace {

int compare_projection(const BindingRow& row, const BindingRow& binding,
                       std::span<const uint32_t> columns) {
    for (size_t i = 0; i < columns.size(); ++i) {
        if (row[i] < binding[columns[i]]) return -1;
        if (binding[columns[i]] < row[i]) return 1;
    }
    return 0;
}

bool contains_binding(const BindingTable& table, const BindingRow& binding) {
    auto it = std::lower_bound(table.rows.begin(), table.rows.end(), binding,
                              [&](const auto& row, const auto& value) {
                                  return compare_projection(row, value, table.columns) < 0;
                              });
    return it != table.rows.end() && compare_projection(*it, binding, table.columns) == 0;
}

} // namespace

void select_first_present_rows(const BindingTable& presence, BindingTable& candidates,
                               uint32_t priority_column, std::span<const BindingValue> priority) {
    if (presence.columns != candidates.columns ||
        !std::is_sorted(presence.columns.begin(), presence.columns.end()) ||
        std::adjacent_find(presence.columns.begin(), presence.columns.end()) != presence.columns.end()) {
        throw std::invalid_argument("Presence and candidate binding columns must agree and be distinct and sorted");
    }
    auto column = std::find(presence.columns.begin(), presence.columns.end(), priority_column);
    if (column == presence.columns.end()) throw std::invalid_argument("Priority variable is not bound by the clause");
    size_t offset = column - presence.columns.begin();
    auto group_key = [offset](const BindingRow& row) {
        BindingRow key;
        key.reserve(row.size() - 1);
        for (size_t i = 0; i < row.size(); ++i) {
            if (i != offset) key.push_back(row[i]);
        }
        return key;
    };
    std::map<BindingRow, size_t> effective;
    for (const auto& row : presence.rows) {
        if (row.size() != presence.columns.size()) throw std::invalid_argument("Presence row has wrong arity");
        auto it = std::find(priority.begin(), priority.end(), row[offset]);
        if (it == priority.end()) continue;
        size_t rank = it - priority.begin();
        auto [entry, inserted] = effective.try_emplace(group_key(row), rank);
        if (!inserted) entry->second = std::min(entry->second, rank);
    }
    for (const auto& row : candidates.rows) {
        if (row.size() != candidates.columns.size()) throw std::invalid_argument("Candidate row has wrong arity");
    }
    std::erase_if(candidates.rows, [&](const auto& row) {
        auto it = effective.find(group_key(row));
        return it == effective.end() || row[offset] != priority[it->second];
    });
}

CorrelatedRows::CorrelatedRows(uint32_t column_count) : _column_count(column_count), _matches() {
    if (column_count == 0) throw std::invalid_argument("Correlation needs at least one bound variable");
}

CorrelatedRows::~CorrelatedRows() = default;

bool CorrelatedRows::has_match(std::span<BindingTable> tables) {
    return evaluate(tables, false, {});
}

const std::vector<BindingRow>& CorrelatedRows::find_matches(std::span<BindingTable> tables) {
    std::vector<uint32_t> projection(_column_count);
    std::iota(projection.begin(), projection.end(), 0);
    evaluate(tables, true, projection);
    return _matches;
}

const std::vector<BindingRow>& CorrelatedRows::find_matches(std::span<BindingTable> tables,
                                                         std::span<const uint32_t> projection) {
    evaluate(tables, true, projection);
    return _matches;
}

bool CorrelatedRows::evaluate(std::span<BindingTable> tables, bool collect, std::span<const uint32_t> projection) {
    _matches.clear();
    std::vector<bool> projected(_column_count, false);
    for (uint32_t column : projection) {
        if (column >= _column_count || projected[column])
            throw std::invalid_argument("Projected columns must be distinct and in range");
        projected[column] = true;
    }
    auto append_match = [&](const BindingRow& binding) {
        BindingRow row;
        row.reserve(projection.size());
        for (uint32_t column : projection) row.push_back(binding[column]);
        _matches.push_back(std::move(row));
    };
    auto finish_matches = [&] {
        std::sort(_matches.begin(), _matches.end());
        _matches.erase(std::unique(_matches.begin(), _matches.end()), _matches.end());
        return !_matches.empty();
    };
    if (tables.empty()) throw std::invalid_argument("Correlation needs at least one clause");
    std::vector<bool> bound_columns(_column_count, false);
    size_t anchor = tables.size();
    bool empty = false;
    for (size_t i = 0; i < tables.size(); ++i) {
        auto& table = tables[i];
        if (table.columns.empty() || !std::is_sorted(table.columns.begin(), table.columns.end()) ||
            std::adjacent_find(table.columns.begin(), table.columns.end()) != table.columns.end() ||
            table.columns.back() >= _column_count) {
            throw std::invalid_argument("Clause binding columns must be nonempty, distinct, sorted and in range");
        }
        for (uint32_t column : table.columns) bound_columns[column] = true;
        for (const auto& row : table.rows) {
            if (row.size() != table.columns.size()) throw std::invalid_argument("Binding row has wrong arity");
        }
        std::sort(table.rows.begin(), table.rows.end());
        table.rows.erase(std::unique(table.rows.begin(), table.rows.end()), table.rows.end());
        empty |= table.rows.empty();
        if (table.columns.size() == _column_count &&
            (anchor == tables.size() || table.rows.size() < tables[anchor].rows.size())) {
            anchor = i;
        }
    }
    if (std::find(bound_columns.begin(), bound_columns.end(), false) != bound_columns.end()) {
        throw std::invalid_argument("Every correlation variable must be bound by a clause");
    }
    if (empty) return false;

    // The usual unit/zone shape has a clause that binds every variable. Its rows
    // can probe the other clauses directly without building intermediate joins.
    if (anchor != tables.size()) {
        for (const auto& binding : tables[anchor].rows) {
            bool match = true;
            for (size_t i = 0; i < tables.size(); ++i) {
                if (i != anchor && !contains_binding(tables[i], binding)) {
                    match = false;
                    break;
                }
            }
            if (match) {
                if (!collect) return true;
                append_match(binding);
            }
        }
        return finish_matches();
    }

    // Different zone namespaces may be bound in different clauses. Extend one
    // binding at a time so boolean evaluation still stops at its first match.
    std::vector<size_t> order(tables.size());
    std::iota(order.begin(), order.end(), 0);
    auto projected_count = [&](size_t index) {
        return std::count_if(tables[index].columns.begin(), tables[index].columns.end(),
                             [&](uint32_t column) { return projected[column]; });
    };
    std::stable_sort(order.begin(), order.end(), [&](size_t a, size_t b) {
        if (projected_count(a) != projected_count(b)) return projected_count(a) > projected_count(b);
        return tables[a].rows.size() < tables[b].rows.size();
    });
    BindingRow binding(_column_count);
    std::vector<bool> bound(_column_count, false);
    std::function<bool(size_t, bool)> extend = [&](size_t depth, bool exists_only) {
        if (depth == order.size()) {
            if (exists_only) return true;
            append_match(binding);
            return false;
        }
        if (!exists_only && std::all_of(projection.begin(), projection.end(), [&](uint32_t column) { return bound[column]; })) {
            if (extend(depth, true)) append_match(binding);
            return false;
        }
        const auto& table = tables[order[depth]];
        std::vector<uint32_t> added;
        for (uint32_t column : table.columns) {
            if (!bound[column]) added.push_back(column);
        }
        size_t prefix_size = 0;
        while (prefix_size < table.columns.size() && bound[table.columns[prefix_size]]) ++prefix_size;
        auto prefix = std::span<const uint32_t>(table.columns).first(prefix_size);
        auto first = table.rows.begin();
        auto last = table.rows.end();
        if (!prefix.empty()) {
            first = std::lower_bound(first, last, binding, [&](const auto& row, const auto& value) {
                return compare_projection(row, value, prefix) < 0;
            });
            last = std::upper_bound(first, last, binding, [&](const auto& value, const auto& row) {
                return compare_projection(row, value, prefix) > 0;
            });
        }
        for (auto candidate = first; candidate != last; ++candidate) {
            const auto& row = *candidate;
            bool compatible = true;
            for (size_t i = 0; i < table.columns.size(); ++i) {
                uint32_t column = table.columns[i];
                if (bound[column] && binding[column] != row[i]) {
                    compatible = false;
                    break;
                }
            }
            if (!compatible) continue;
            for (size_t i = 0; i < table.columns.size(); ++i) binding[table.columns[i]] = row[i];
            for (uint32_t column : added) bound[column] = true;
            bool found = extend(depth + 1, exists_only);
            for (uint32_t column : added) bound[column] = false;
            if (found) return true;
        }
        return false;
    };
    bool found = extend(0, !collect);
    if (collect) {
        return finish_matches();
    }
    return found;
}

} // namespace search::queryeval
