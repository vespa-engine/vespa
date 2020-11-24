// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/value.h>
#include "streamed_value_index.h"

namespace vespalib::eval {

 /**
  *  Same characteristics as StreamedValue, but does not
  *  own its data - refers to type, cells and serialized
  *  labels that must be kept outside the Value.
  **/
class StreamedValueView : public Value
{
private:
    const ValueType &_type;
    TypedCells _cells_ref;
    StreamedValueIndex _my_index;

public:
    StreamedValueView(const ValueType &type, TypedCells cells,
                      size_t num_ss, ConstArrayRef<char> labels_buf)
      : _type(type),
        _cells_ref(cells),
        _my_index(_type.count_mapped_dimensions(), num_ss, labels_buf)
    {
        if (num_ss * _type.dense_subspace_size() != _cells_ref.size) abort();
    }

    ~StreamedValueView();
    const ValueType &type() const final override { return _type; }
    TypedCells cells() const final override { return _cells_ref; }
    const Value::Index &index() const override { return _my_index; }
    MemoryUsage get_memory_usage() const final override {
        return self_memory_usage<StreamedValueView>();
    }
    auto serialize_index() const { return _my_index.serialize(); }
};

} // namespace
