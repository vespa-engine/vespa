// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "empty_subspace.h"
#include "subspace_type.h"

namespace search::tensor {

EmptySubspace::EmptySubspace(const SubspaceType& type)
    : _empty_space(),
      _cells()
{
    _empty_space.resize(type.mem_size());
    _cells = vespalib::eval::TypedCells(&_empty_space[0], type.cell_type(), type.size());
}

EmptySubspace::~EmptySubspace() = default;

}
