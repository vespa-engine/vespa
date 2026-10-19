// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "correlate_by_search.h"
#include <vespa/searchcommon/common/undefinedvalues.h>
#include <vespa/vespalib/objects/visit.hpp>

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

}

CorrelateBySearch::CorrelateBySearch(std::vector<std::unique_ptr<SameElementSearch>> children,
                                   std::vector<std::vector<BoundAttribute>> bindings,
                                   std::vector<std::optional<std::vector<std::string>>> domains, bool strict,
                                   std::vector<std::vector<ExactAttributeValues>> exact_values,
                                   std::vector<FirstPresentSelection> first_present)
    : _children(std::move(children)), _bindings(std::move(bindings)), _domains(std::move(domains)),
      _exact_values(std::move(exact_values)),
      _attributes(), _tables(_children.size() - first_present.size()), _presence_tables(first_present.size()),
      _child_tables(_children.size()), _presence_children(_children.size(), false),
      _first_present(std::move(first_present)), _join(_domains.size()), _strict(strict),
      _loaded_docid(std::numeric_limits<uint32_t>::max()), _integers(), _strings() {
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
    auto add_attribute = [this](const auto* attribute) {
        if (std::none_of(_attributes.begin(), _attributes.end(), [attribute](const auto& entry) {
                return entry.attribute == attribute;
            })) {
            _attributes.push_back({attribute, {}});
        }
    };
    for (size_t i = 0; i < _bindings.size(); ++i) {
        for (const auto& binding : _bindings[i]) {
            _child_tables[i]->columns.push_back(binding.column);
            add_attribute(binding.attribute);
        }
        for (const auto& filter : _exact_values[i]) add_attribute(filter.attribute);
    }
}

CorrelateBySearch::~CorrelateBySearch() = default;

void CorrelateBySearch::initRange(uint32_t begin, uint32_t end) {
    SearchIterator::initRange(begin, end);
    for (const auto& child : _children) child->initRange(begin, end);
    _loaded_docid = std::numeric_limits<uint32_t>::max();
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
            uint32_t fetched = entry.attribute->get(docid, _strings.data(), count);
            for (uint32_t i = 0; i < std::min(count, fetched); ++i)
                entry.values.emplace_back(std::string_view(_strings[i] ? _strings[i] : ""));
        } else {
            _integers.resize(count);
            uint32_t fetched = entry.attribute->get(docid, _integers.data(), count);
            for (uint32_t i = 0; i < std::min(count, fetched); ++i) entry.values.emplace_back(_integers[i]);
        }
    }
    _loaded_docid = docid;
}

const std::vector<BindingValue>& CorrelateBySearch::values_for(const attribute::IAttributeVector* attribute) const {
    return std::find_if(_attributes.begin(), _attributes.end(), [attribute](const auto& entry) {
        return entry.attribute == attribute;
    })->values;
}

void CorrelateBySearch::read_matching_rows(uint32_t docid) {
    read_attributes(docid);
    for (size_t i = 0; i < _children.size(); ++i) {
        auto& rows = _child_tables[i]->rows;
        rows.clear();
        for (uint32_t element : _children[i]->matching_elements()) {
            if (!std::all_of(_exact_values[i].begin(), _exact_values[i].end(), [&](const auto& filter) {
                    const auto& values = values_for(filter.attribute);
                    std::string_view value = element < values.size() ? std::get<std::string_view>(values[element]) : std::string_view();
                    return std::find(filter.values.begin(), filter.values.end(), value) != filter.values.end();
                })) continue;
            BindingRow row;
            bool valid = true;
            for (const auto& binding : _bindings[i]) {
                const auto& values = values_for(binding.attribute);
                if (element >= values.size()) { valid = false; break; }
                const auto& value = values[element];
                // Struct attributes represent absent fields with their undefined values.
                if (is_undefined(value, binding.attribute->getBasicType())) {
                    valid = false;
                    break;
                }
                if (!_presence_children[i] && _domains[binding.column] &&
                    std::find(_domains[binding.column]->begin(), _domains[binding.column]->end(),
                              std::get<std::string_view>(value)) == _domains[binding.column]->end()) {
                    valid = false;
                    break;
                }
                row.push_back(value);
            }
            if (valid) rows.push_back(std::move(row));
        }
    }
    for (const auto& selection : _first_present) {
        std::vector<BindingValue> priority;
        priority.reserve(selection.values.size());
        for (const auto& value : selection.values) priority.emplace_back(std::string_view(value));
        select_first_present_rows(*_child_tables[selection.presence], *_child_tables[selection.clause],
                                  selection.column, priority);
    }
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
    for (const auto& child : _children) child->unpack(docid);
}

SearchIterator::Trinary CorrelateBySearch::is_strict() const { return _strict ? Trinary::True : Trinary::False; }

const std::vector<BindingRow>& CorrelateBySearch::find_bindings(uint32_t docid) {
    std::vector<uint32_t> projection(_domains.size());
    std::iota(projection.begin(), projection.end(), 0);
    return find_bindings(docid, projection);
}

const std::vector<BindingRow>& CorrelateBySearch::find_bindings(uint32_t docid, std::span<const uint32_t> projection) {
    if (check_docid_match(docid)) {
        read_matching_rows(docid);
    } else {
        for (auto& table : _tables) table.rows.clear();
    }
    return _join.find_matches(_tables, projection);
}

void CorrelateBySearch::visitMembers(vespalib::ObjectVisitor& visitor) const {
    SearchIterator::visitMembers(visitor);
    visit(visitor, "children", _children);
    visit(visitor, "strict", _strict);
}

} // namespace search::queryeval
