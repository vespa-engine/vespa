// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace search::common {

class BlobConverter;
class ConverterFactory;

namespace sortspec {

enum class SortOrder : uint8_t {
    ASCENDING,
    DESCENDING
};

enum class MissingPolicy : uint8_t {
    DEFAULT, // Single value: first on ascending, last on descending. Multi value: last
    FIRST,
    LAST,
    AS
};

}

struct FieldSortSpec {
    FieldSortSpec(std::string_view field, bool ascending, std::shared_ptr<BlobConverter> converter) noexcept;
    FieldSortSpec(std::string_view field, sortspec::SortOrder, std::shared_ptr<BlobConverter> converter) noexcept;
    ~FieldSortSpec();
    std::string                    _field;
    bool                           _ascending;  // Deprecated, _sort order will take over.
    sortspec::SortOrder            _sort_order;
    std::shared_ptr<BlobConverter> _converter;
    sortspec::MissingPolicy        _missing_policy;
    std::string                    _missing_value;
};

class SortSpec
{
    std::string _spec;
    std::vector<FieldSortSpec> _field_sort_specs;
public:
    SortSpec();
    SortSpec(const std::string& spec, const ConverterFactory& ucaFactory);
    ~SortSpec();
    const std::string& getSpec() const noexcept { return _spec; }
    size_t size() const noexcept { return _field_sort_specs.size(); }
    bool empty() const noexcept { return _field_sort_specs.empty(); }
    const FieldSortSpec& operator[](size_t idx) const noexcept { return _field_sort_specs[idx]; }
    auto begin() const noexcept { return _field_sort_specs.begin(); }
    auto end() const noexcept { return _field_sort_specs.end(); }
};

}
