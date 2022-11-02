// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "empty_subspace.h"
#include <vespa/eval/eval/value_type.h>

namespace search::tensor {

EmptySubspace::EmptySubspace(const vespalib::eval::ValueType& type)
    : _empty_space(),
      _empty()
{
    auto dense_subspace_size = type.dense_subspace_size();
    auto cell_type = type.cell_type();
    _empty_space.resize(vespalib::eval::CellTypeUtils::mem_size(cell_type, dense_subspace_size), 0);
    _empty = vespalib::eval::TypedCells(&_empty_space[0], cell_type, dense_subspace_size);
}

EmptySubspace::~EmptySubspace() = default;

}
