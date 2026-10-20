// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "correlated_rows.h"

#include <algorithm>
#include <map>
#include <numeric>
#include <stdexcept>

namespace search::queryeval {

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

CorrelatedRows::CorrelatedRows(uint32_t column_count)
    : _column_count(column_count),
      _matches(),
      _work(),
      _work_size(0),
      _projected(),
      _needed(),
      _bound(),
      _binding_counts(),
      _row_order(),
      _column_positions(),
      _column_scratch(),
      _value_scratch(),
      _binding(column_count),
      _projection_depth(0),
      _visited_rows(0) {
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
    _visited_rows = 0;
    _projected.assign(_column_count, false);
    for (uint32_t column : projection) {
        if (column >= _column_count || _projected[column])
            throw std::invalid_argument("Projected columns must be distinct and in range");
        _projected[column] = true;
    }
    if (tables.empty()) throw std::invalid_argument("Correlation needs at least one clause");
    _binding_counts.assign(_column_count, 0);
    bool empty = false;
    for (const auto& table : tables) {
        if (table.columns.empty() || !std::is_sorted(table.columns.begin(), table.columns.end()) ||
            std::adjacent_find(table.columns.begin(), table.columns.end()) != table.columns.end() ||
            table.columns.back() >= _column_count) {
            throw std::invalid_argument("Clause binding columns must be nonempty, distinct, sorted and in range");
        }
        for (uint32_t column : table.columns) ++_binding_counts[column];
        for (const auto& row : table.rows) {
            if (row.size() != table.columns.size()) throw std::invalid_argument("Binding row has wrong arity");
        }
        empty |= table.rows.empty();
    }
    if (std::find(_binding_counts.begin(), _binding_counts.end(), 0) != _binding_counts.end()) {
        throw std::invalid_argument("Every correlation variable must be bound by a clause");
    }
    if (empty) return false;

    // Keep a variable when the caller asked for it or when it joins two clauses.
    _needed.assign(_column_count, false);
    for (uint32_t column = 0; column < _column_count; ++column) {
        _needed[column] = _projected[column] || _binding_counts[column] > 1;
    }
    project_tables(tables);
    if (_work_size == 0) {
        // Every variable is bound by a single clause and none was asked for, so
        // the clauses only have to have rows.
        if (collect) _matches.emplace_back();
        return true;
    }
    plan_join_order(!projection.empty());
    bool found = extend(0, !collect, projection);
    if (!collect) return found;
    std::sort(_matches.begin(), _matches.end());
    _matches.erase(std::unique(_matches.begin(), _matches.end()), _matches.end());
    return !_matches.empty();
}

void CorrelatedRows::project_tables(std::span<const BindingTable> tables) {
    _work_size = 0;
    for (const auto& table : tables) {
        _column_positions.clear();
        for (size_t i = 0; i < table.columns.size(); ++i) {
            if (_needed[table.columns[i]]) _column_positions.push_back(i);
        }
        // A clause with only its own variables just has to have a row, which was checked above.
        if (_column_positions.empty()) continue;
        if (_work_size == _work.size()) _work.emplace_back();
        auto& work = _work[_work_size++];
        work.columns.clear();
        for (size_t position : _column_positions) work.columns.push_back(table.columns[position]);
        work.lookup_size = 0;
        work.values.clear();
        work.values.reserve(table.rows.size() * _column_positions.size());
        for (const auto& row : table.rows) {
            for (size_t position : _column_positions) work.values.push_back(row[position]);
        }
        sort_and_deduplicate(work);
    }
}

void CorrelatedRows::plan_join_order(bool has_projection) {
    _bound.assign(_column_count, false);
    _projection_depth = has_projection ? _work_size : 0;
    auto count_columns = [this](const WorkTable& table, bool bound) {
        return std::count_if(table.columns.begin(), table.columns.end(),
                             [this, bound](uint32_t column) { return _bound[column] == bound; });
    };
    auto count_projected = [this](const WorkTable& table) {
        return std::count_if(table.columns.begin(), table.columns.end(),
                             [this](uint32_t column) { return _projected[column]; });
    };
    for (size_t depth = 0; depth < _work_size; ++depth) {
        // Start with the clause that binds the most requested variables. After that,
        // take the clause sharing the most bound variables, so each step is a lookup.
        auto better = [&](const WorkTable& a, const WorkTable& b) {
            if (depth == 0) {
                if (count_projected(a) != count_projected(b)) return count_projected(a) > count_projected(b);
            } else {
                if (count_columns(a, true) != count_columns(b, true)) return count_columns(a, true) > count_columns(b, true);
                if (count_columns(a, false) != count_columns(b, false)) return count_columns(a, false) < count_columns(b, false);
            }
            return a.row_count() < b.row_count();
        };
        size_t best = depth;
        for (size_t i = depth + 1; i < _work_size; ++i) {
            if (better(_work[i], _work[best])) best = i;
        }
        if (best != depth) std::swap(_work[depth], _work[best]);
        auto& table = _work[depth];
        if (put_lookup_columns_first(table)) sort_and_deduplicate(table);
        for (uint32_t column : table.columns) _bound[column] = true;
        // Remember the first depth where every requested variable is bound.
        if (has_projection && _projection_depth == _work_size) {
            bool all_bound = true;
            for (uint32_t column = 0; column < _column_count; ++column) {
                if (_projected[column] && !_bound[column]) all_bound = false;
            }
            if (all_bound) _projection_depth = depth + 1;
        }
    }
}

bool CorrelatedRows::put_lookup_columns_first(WorkTable& table) {
    _column_positions.clear();
    for (size_t i = 0; i < table.columns.size(); ++i) {
        if (_bound[table.columns[i]]) _column_positions.push_back(i);
    }
    table.lookup_size = _column_positions.size();
    for (size_t i = 0; i < table.columns.size(); ++i) {
        if (!_bound[table.columns[i]]) _column_positions.push_back(i);
    }
    bool same_order = true;
    for (size_t i = 0; i < _column_positions.size(); ++i) same_order &= (_column_positions[i] == i);
    if (same_order) return false;
    _value_scratch.clear();
    _value_scratch.reserve(table.values.size());
    for (size_t row = 0; row < table.row_count(); ++row) {
        const BindingValue* values = table.row(row);
        for (size_t position : _column_positions) _value_scratch.push_back(values[position]);
    }
    table.values.swap(_value_scratch);
    _column_scratch.clear();
    for (size_t position : _column_positions) _column_scratch.push_back(table.columns[position]);
    table.columns.swap(_column_scratch);
    return true;
}

void CorrelatedRows::sort_and_deduplicate(WorkTable& table) {
    size_t width = table.columns.size();
    size_t rows = table.row_count();
    _row_order.resize(rows);
    std::iota(_row_order.begin(), _row_order.end(), 0);
    std::sort(_row_order.begin(), _row_order.end(), [&table, width](uint32_t a, uint32_t b) {
        return std::lexicographical_compare(table.row(a), table.row(a) + width, table.row(b), table.row(b) + width);
    });
    _value_scratch.clear();
    _value_scratch.reserve(table.values.size());
    const BindingValue* previous = nullptr;
    for (uint32_t index : _row_order) {
        const BindingValue* row = table.row(index);
        if (previous != nullptr && std::equal(row, row + width, previous)) continue;
        _value_scratch.insert(_value_scratch.end(), row, row + width);
        previous = row;
    }
    table.values.swap(_value_scratch);
}

bool CorrelatedRows::extend(size_t depth, bool exists_only, std::span<const uint32_t> projection) {
    if (depth == _work_size) {
        if (exists_only) return true;
        append_match(projection);
        return false;
    }
    if (!exists_only && depth == _projection_depth) {
        // Every requested variable is bound, so the remaining clauses only need one completion.
        if (extend(depth, true, projection)) append_match(projection);
        return false;
    }
    const auto& table = _work[depth];
    size_t width = table.columns.size();
    // Compares the bound columns of a row with the current binding.
    auto compare_lookup = [this, &table](size_t index) {
        const BindingValue* row = table.row(index);
        for (size_t i = 0; i < table.lookup_size; ++i) {
            const auto& bound = _binding[table.columns[i]];
            if (row[i] < bound) return -1;
            if (bound < row[i]) return 1;
        }
        return 0;
    };
    size_t first = 0;
    size_t last = table.row_count();
    if (table.lookup_size > 0) {
        size_t low = 0;
        size_t high = last;
        while (low < high) {
            size_t middle = low + (high - low) / 2;
            if (compare_lookup(middle) < 0) low = middle + 1;
            else high = middle;
        }
        first = low;
        high = last;
        while (low < high) {
            size_t middle = low + (high - low) / 2;
            if (compare_lookup(middle) <= 0) low = middle + 1;
            else high = middle;
        }
        last = low;
    }
    for (size_t index = first; index < last; ++index) {
        ++_visited_rows;
        const BindingValue* row = table.row(index);
        for (size_t i = table.lookup_size; i < width; ++i) _binding[table.columns[i]] = row[i];
        if (extend(depth + 1, exists_only, projection)) return true;
    }
    return false;
}

void CorrelatedRows::append_match(std::span<const uint32_t> projection) {
    BindingRow row;
    row.reserve(projection.size());
    for (uint32_t column : projection) row.push_back(_binding[column]);
    _matches.push_back(std::move(row));
}

} // namespace search::queryeval
