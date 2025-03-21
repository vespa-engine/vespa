// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "converters.h"
#include <vespa/searchcommon/common/iblobconverter.h>
#include <vespa/vespalib/util/buffer.h>
#include <string>
#include <vector>

namespace search::common {

struct FieldSortSpec {
    FieldSortSpec(std::string_view field, bool ascending, BlobConverter::SP converter) noexcept;
    ~FieldSortSpec();
    std::string      _field;
    bool                  _ascending;
    BlobConverter::SP     _converter;
};

class SortSpec
{
    std::string _spec;
    std::vector<FieldSortSpec> _field_sort_specs;
public:
    SortSpec();
    SortSpec(const std::string & spec, const ConverterFactory & ucaFactory);
    ~SortSpec();
    const std::string& getSpec() const noexcept { return _spec; }
    size_t size() const noexcept { return _field_sort_specs.size(); }
    bool empty() const noexcept { return _field_sort_specs.empty(); }
    const FieldSortSpec& operator[](size_t idx) const noexcept { return _field_sort_specs[idx]; }
    auto begin() const noexcept { return _field_sort_specs.begin(); }
    auto end() const noexcept { return _field_sort_specs.end(); }
};

}
