// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/value.h>
#include "streamed_value_index.h"

namespace vespalib::eval {

/**
 *  A very simple Value implementation.
 *  Cheap to construct from serialized data,
 *  and cheap to serialize or iterate through.
 *  Slow for full or partial lookups.
 **/
template <typename T>
class StreamedValue : public Value
{
private:
    ValueType _type;
    std::vector<T> _my_cells;
    Array<char> _label_buf;
    StreamedValueIndex _my_index;

public:
    StreamedValue(ValueType type, std::vector<T> cells, size_t num_ss, Array<char> && label_buf)
      : _type(std::move(type)),
        _my_cells(std::move(cells)),
        _label_buf(std::move(label_buf)),
        _my_index(_type.count_mapped_dimensions(),
                  num_ss,
                  ConstArrayRef<char>(_label_buf.begin(), _label_buf.size()))
    {
        if (num_ss * _type.dense_subspace_size() != _my_cells.size()) abort();
    }

    ~StreamedValue();
    const ValueType &type() const final override { return _type; }
    TypedCells cells() const final override { return TypedCells(_my_cells); }
    const Value::Index &index() const override { return _my_index; }
    MemoryUsage get_memory_usage() const final override;
    auto serialize_index() const { return _my_index.serialize(); }
};

} // namespace
