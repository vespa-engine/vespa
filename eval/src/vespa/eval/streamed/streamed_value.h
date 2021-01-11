// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/util/shared_string_repo.h>
#include "streamed_value_index.h"
#include <cassert>

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
    using Handles = SharedStringRepo::Handles;

    ValueType _type;
    std::vector<T> _my_cells;
    Handles _my_labels;
    StreamedValueIndex _my_index;

public:
    StreamedValue(ValueType type, size_t num_mapped_dimensions,
                  std::vector<T> cells, size_t num_subspaces, Handles && handles)
      : _type(std::move(type)),
        _my_cells(std::move(cells)),
        _my_labels(std::move(handles)),
        _my_index(num_mapped_dimensions,
                  num_subspaces,
                  _my_labels.view())
    {
        assert(num_subspaces * _type.dense_subspace_size() == _my_cells.size());
    }

    ~StreamedValue();
    const ValueType &type() const final override { return _type; }
    TypedCells cells() const final override { return TypedCells(_my_cells); }
    const Value::Index &index() const final override { return _my_index; }
    MemoryUsage get_memory_usage() const final override;
};

} // namespace
