// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/fast_value_index.h>

namespace search::tensor {

/*
 * Tensor view that is not self-contained. It references external cell values.
 */
struct FastValueView final : vespalib::eval::Value {
    const vespalib::eval::ValueType& _type;
    vespalib::StringIdVector         _labels;
    vespalib::eval::FastValueIndex   _index;
    vespalib::eval::TypedCells       _cells;
    FastValueView(const vespalib::eval::ValueType& type, vespalib::ConstArrayRef<vespalib::string_id> labels, vespalib::eval::TypedCells cells, size_t num_mapped_dimensions, size_t num_subspaces);
    const vespalib::eval::ValueType& type() const override { return _type; }
    const vespalib::eval::Value::Index& index() const override { return _index; }
    vespalib::eval::TypedCells cells() const override { return _cells; }
    vespalib::MemoryUsage get_memory_usage() const override;
};

}
