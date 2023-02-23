// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fast_value_view.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

using vespalib::ConstArrayRef;
using vespalib::MemoryUsage;
using vespalib::string_id;
using vespalib::eval::FastAddrMap;
using vespalib::eval::TypedCells;
using vespalib::eval::Value;
using vespalib::eval::ValueType;
using vespalib::eval::self_memory_usage;

namespace search::tensor {

FastValueView::FastValueView(const ValueType& type, ConstArrayRef<string_id> labels, TypedCells cells, size_t num_mapped_dimensions, size_t num_subspaces)
    : Value(),
      _type(type),
      _labels(labels.begin(), labels.end()),
      _index(num_mapped_dimensions, _labels, num_subspaces),
      _cells(cells)
{
    for (size_t i = 0; i < num_subspaces; ++i) {
        ConstArrayRef<string_id> addr(_labels.data() + (i * num_mapped_dimensions), num_mapped_dimensions);
        _index.map.add_mapping(FastAddrMap::hash_labels(addr));
    }
    assert(_index.map.size() == num_subspaces);
}

MemoryUsage
FastValueView::get_memory_usage() const
{
    MemoryUsage usage = self_memory_usage<FastValueView>();
    usage.merge(_index.map.estimate_extra_memory_usage());
    return usage;
}

}
