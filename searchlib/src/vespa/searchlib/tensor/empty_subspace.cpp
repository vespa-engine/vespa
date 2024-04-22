// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "empty_subspace.h"
#include "subspace_type.h"

namespace search::tensor {

EmptySubspace::EmptySubspace(const SubspaceType& type)
    : _empty_space(),
      _cells()
{
    _empty_space.resize(type.mem_size());
    // Set size to zero to signal empty/invalid subspace
    _cells = vespalib::eval::TypedCells(_empty_space.data(), type.cell_type(), 0);
}

EmptySubspace::~EmptySubspace() = default;

}
