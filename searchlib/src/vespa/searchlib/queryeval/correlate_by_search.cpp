// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "correlate_by_search.h"
#include <vespa/searchcommon/common/undefinedvalues.h>
#include <vespa/vespalib/objects/visit.hpp>
#include <vespa/vespalib/text/lowercase.h>

#include <algorithm>
#include <cassert>
#include <limits>
#include <numeric>

namespace search::queryeval {

namespace {

bool is_undefined(const BindingValue& value, attribute::BasicType::Type type) {
    using attribute::BasicType;
    switch (type) {
        case BasicType::STRING: return std::get<std::string_view>(value).empty();
        case BasicType::INT8: return std::get<int64_t>(value) == attribute::getUndefined<int8_t>();
        case BasicType::INT16: return std::get<int64_t>(value) == attribute::getUndefined<int16_t>();
        case BasicType::INT32: return std::get<int64_t>(value) == attribute::getUndefined<int32_t>();
        case BasicType::INT64: return std::get<int64_t>(value) == attribute::getUndefined<int64_t>();
        default: return false;
    }
}

bool needs_folding(std::string_view value) {
    return std::any_of(value.begin(), value.end(), [](char c) {
        auto byte = static_cast<unsigned char>(c);
        return (byte >= 'A' && byte <= 'Z') || byte >= 0x80;
    });
}

void sort_and_deduplicate(std::vector<std::string>& values) {
    std::sort(values.begin(), values.end());
    values.erase(std::unique(values.begin(), values.end()), values.end());
}

bool contains(const std::vector<std::string>& sorted_values, std::string_view value) {
    return std::binary_search(sorted_values.begin(), sorted_values.end(), value,
                              [](std::string_view a, std::string_view b) { return a < b; });
}

} // namespace

std::vector<bool> find_uncased_columns(size_t column_count, std::span<const std::vector<BoundAttribute>> bindings) {
    std::vector<bool> uncased(column_count, false);
    for (const auto& clause : bindings) {
        for (const auto& binding : clause) {
            if (binding.column < column_count && binding.attribute->isStringType() &&
                binding.attribute->has_uncased_matching()) {
                uncased[binding.column] = true;
            }
        }
    }
    return uncased;
}

CorrelateBySearch::CorrelateBySearch(std::vector<std::unique_ptr<SameElementSearch>> children,
                                   std::vector<std::vector<BoundAttribute>> bindings,
                                   std::vector<std::optional<std::vector<std::string>>> domains, bool strict,
                                   std::vector<std::vector<ExactAttributeValues>> exact_values,
                                   std::vector<FirstPresentSelection> first_present)
    : _children(std::move(children)), _bindings(std::move(bindings)), _domains(std::move(domains)),
      _exact_values(std::move(exact_values)), _uncased_columns(), _attributes(), _binding_attributes(),
      _exact_attributes(), _tables(_children.size() - first_present.size()), _presence_tables(first_present.size()),
      _child_tables(_children.size()), _presence_children(_children.size(), false),
      _first_present(std::move(first_present)), _priorities(), _join(_domains.size()), _strict(strict),
      _loaded_docid(std::numeric_limits<uint32_t>::max()), _rows_docid(std::numeric_limits<uint32_t>::max()),
      _integers(), _strings(), _row(), _elements() {
    assert(_children.size() == _bindings.size());
    if (_exact_values.empty()) _exact_values.resize(_children.size());
    assert(_exact_values.size() == _children.size());
    for (size_t i = 0; i < _first_present.size(); ++i) {
        uint32_t child = _first_present[i].presence;
        _presence_children[child] = true;
        _child_tables[child] = &_presence_tables[i];
    }
    size_t table = 0;
    for (size_t i = 0; i < _children.size(); ++i) {
        if (!_presence_children[i]) _child_tables[i] = &_tables[table++];
    }
    _uncased_columns = find_uncased_columns(_domains.size(), _bindings);
    _binding_attributes.resize(_children.size());
    _exact_attributes.resize(_children.size());
    for (size_t i = 0; i < _bindings.size(); ++i) {
        for (const auto& binding : _bindings[i]) {
            _child_tables[i]->columns.push_back(binding.column);
            bool fold = binding.attribute->isStringType() && _uncased_columns[binding.column];
            _binding_attributes[i].push_back(attribute_index(binding.attribute, fold));
        }
        for (const auto& filter : _exact_values[i]) {
            bool fold = filter.attribute->isStringType() && filter.attribute->has_uncased_matching();
            _exact_attributes[i].push_back(attribute_index(filter.attribute, fold));
        }
    }
    fold_literals();
}

CorrelateBySearch::~CorrelateBySearch() = default;

uint32_t CorrelateBySearch::attribute_index(const attribute::IAttributeVector* attribute, bool fold) {
    for (uint32_t i = 0; i < _attributes.size(); ++i) {
        if (_attributes[i].attribute == attribute && _attributes[i].fold == fold) {
            return i;
        }
    }
    _attributes.push_back({attribute, fold, {}, {}});
    return _attributes.size() - 1;
}

void CorrelateBySearch::fold_literals() {
    for (size_t column = 0; column < _domains.size(); ++column) {
        auto& domain = _domains[column];
        if (!domain) continue;
        if (_uncased_columns[column]) {
            for (auto& value : *domain) value = vespalib::LowerCase::convert(value);
        }
        sort_and_deduplicate(*domain);
    }
    for (size_t i = 0; i < _exact_values.size(); ++i) {
        for (size_t j = 0; j < _exact_values[i].size(); ++j) {
            auto& values = _exact_values[i][j].values;
            if (_attributes[_exact_attributes[i][j]].fold) {
                for (auto& value : values) value = vespalib::LowerCase::convert(value);
            }
            sort_and_deduplicate(values);
        }
    }
    // Priority values keep their order, since the first present value wins.
    _priorities.resize(_first_present.size());
    for (size_t i = 0; i < _first_present.size(); ++i) {
        auto& selection = _first_present[i];
        if (selection.column < _uncased_columns.size() && _uncased_columns[selection.column]) {
            for (auto& value : selection.values) value = vespalib::LowerCase::convert(value);
        }
        for (const auto& value : selection.values) _priorities[i].emplace_back(std::string_view(value));
    }
}

void CorrelateBySearch::initRange(uint32_t begin, uint32_t end) {
    SearchIterator::initRange(begin, end);
    for (const auto& child : _children) child->initRange(begin, end);
    _loaded_docid = std::numeric_limits<uint32_t>::max();
    _rows_docid = std::numeric_limits<uint32_t>::max();
}

bool CorrelateBySearch::check_docid_match(uint32_t docid) {
    for (const auto& child : _children) {
        if (!child->seek(docid)) return false;
    }
    return true;
}

void CorrelateBySearch::read_attributes(uint32_t docid) {
    if (_loaded_docid == docid) return;
    for (auto& entry : _attributes) {
        uint32_t count = entry.attribute->getValueCount(docid);
        entry.values.clear();
        entry.values.reserve(count);
        if (entry.attribute->isStringType()) {
            _strings.resize(count);
            uint32_t fetched = std::min(count, entry.attribute->get(docid, _strings.data(), count));
            entry.folded.clear();
            // Reserve up front so the views below stay valid while strings are added.
            if (entry.fold) entry.folded.reserve(fetched);
            for (uint32_t i = 0; i < fetched; ++i) {
                std::string_view value(_strings[i] ? _strings[i] : "");
                if (entry.fold && needs_folding(value)) {
                    entry.folded.push_back(vespalib::LowerCase::convert(value));
                    value = entry.folded.back();
                }
                entry.values.emplace_back(value);
            }
        } else {
            _integers.resize(count);
            uint32_t fetched = entry.attribute->get(docid, _integers.data(), count);
            for (uint32_t i = 0; i < std::min(count, fetched); ++i) entry.values.emplace_back(_integers[i]);
        }
    }
    _loaded_docid = docid;
}

bool CorrelateBySearch::make_row(size_t child, uint32_t element, BindingRow& row) const {
    row.clear();
    const auto& filters = _exact_values[child];
    for (size_t i = 0; i < filters.size(); ++i) {
        const auto& values = _attributes[_exact_attributes[child][i]].values;
        // A missing value compares as the empty string, so "" accepts rows without the field.
        std::string_view value;
        if (element < values.size() && std::holds_alternative<std::string_view>(values[element])) {
            value = std::get<std::string_view>(values[element]);
        }
        if (!contains(filters[i].values, value)) return false;
    }
    const auto& bindings = _bindings[child];
    for (size_t i = 0; i < bindings.size(); ++i) {
        const auto& values = _attributes[_binding_attributes[child][i]].values;
        if (element >= values.size()) return false;
        const auto& value = values[element];
        // Struct attributes represent absent fields with their undefined values.
        if (is_undefined(value, bindings[i].attribute->getBasicType())) return false;
        const auto& domain = _domains[bindings[i].column];
        if (!_presence_children[child] && domain &&
            (!std::holds_alternative<std::string_view>(value) ||
             !contains(*domain, std::get<std::string_view>(value)))) {
            return false;
        }
        row.push_back(value);
    }
    return true;
}

void CorrelateBySearch::read_matching_rows(uint32_t docid) {
    read_attributes(docid);
    for (size_t i = 0; i < _children.size(); ++i) {
        auto& rows = _child_tables[i]->rows;
        size_t used = 0;
        for (uint32_t element : _children[i]->matching_elements()) {
            // Reuse the row vectors of the previous document instead of allocating new ones.
            if (used == rows.size()) rows.emplace_back();
            if (make_row(i, element, rows[used])) ++used;
        }
        rows.resize(used);
    }
    for (size_t i = 0; i < _first_present.size(); ++i) {
        const auto& selection = _first_present[i];
        select_first_present_rows(*_child_tables[selection.presence], *_child_tables[selection.clause],
                                  selection.column, _priorities[i]);
    }
    _rows_docid = docid;
}

void CorrelateBySearch::doSeek(uint32_t docid) {
    while (!isAtEnd(docid)) {
        if (check_docid_match(docid)) {
            read_matching_rows(docid);
            if (_join.has_match(_tables)) { setDocId(docid); return; }
        }
        if (!_strict) return;
        docid = std::max(docid + 1, _children.front()->getDocId());
    }
    if (_strict) setAtEnd();
}

void CorrelateBySearch::doUnpack(uint32_t docid) {
    for (size_t i = 0; i < _children.size(); ++i) {
        if (_presence_children[i]) continue;
        _elements.clear();
        if (_children[i]->needs_element_filter()) find_correlated_elements(docid, i, _elements);
        _children[i]->unpack_elements(docid, _elements);
    }
}

void CorrelateBySearch::find_correlated_elements(uint32_t docid, size_t child, std::vector<uint32_t>& dst) {
    if (_presence_children[child]) return;
    if (_rows_docid != docid) read_matching_rows(docid);
    // Correlated matches projected onto this clause's variables, sorted for lookup.
    const auto& correlated = _join.find_matches(_tables, _child_tables[child]->columns);
    for (uint32_t element : _children[child]->matching_elements()) {
        if (make_row(child, element, _row) && std::binary_search(correlated.begin(), correlated.end(), _row)) {
            dst.push_back(element);
        }
    }
}

SearchIterator::Trinary CorrelateBySearch::is_strict() const { return _strict ? Trinary::True : Trinary::False; }

const std::vector<BindingRow>& CorrelateBySearch::find_bindings(uint32_t docid) {
    std::vector<uint32_t> projection(_domains.size());
    std::iota(projection.begin(), projection.end(), 0);
    return find_bindings(docid, projection);
}

const std::vector<BindingRow>& CorrelateBySearch::find_bindings(uint32_t docid, std::span<const uint32_t> projection) {
    if (_rows_docid != docid && check_docid_match(docid)) {
        read_matching_rows(docid);
    } else if (_rows_docid != docid) {
        for (auto& table : _tables) table.rows.clear();
        _rows_docid = std::numeric_limits<uint32_t>::max();
    }
    return _join.find_matches(_tables, projection);
}

void CorrelateBySearch::visitMembers(vespalib::ObjectVisitor& visitor) const {
    SearchIterator::visitMembers(visitor);
    visit(visitor, "children", _children);
    visit(visitor, "strict", _strict);
}

} // namespace search::queryeval
