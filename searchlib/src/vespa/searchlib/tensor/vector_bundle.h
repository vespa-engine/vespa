// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "subspace_type.h"
#include <vespa/eval/eval/typed_cells.h>
#include <cassert>

namespace search::tensor {

/*
 * Class referencing the cells owned by a tensor in a form suitable to extract tensor cells for
 * a subspace.
 */
class VectorBundle
{
    const void*              _data;
    vespalib::eval::CellType _cell_type;
    uint32_t                 _subspaces;
    size_t                   _subspace_mem_size;
    size_t                   _subspace_size;
public:
    VectorBundle()
        : _data(nullptr),
          _cell_type(vespalib::eval::CellType::DOUBLE),
          _subspaces(0),
          _subspace_mem_size(0),
          _subspace_size(0)
    {
    }
    VectorBundle(const void *data, uint32_t subspaces, const SubspaceType& subspace_type)
        : _data(data),
          _cell_type(subspace_type.cell_type()),
          _subspaces(subspaces),
          _subspace_mem_size(subspace_type.mem_size()),
          _subspace_size(subspace_type.size())
    {
    }
    ~VectorBundle() = default;
    uint32_t subspaces() const noexcept { return _subspaces; }
    vespalib::eval::TypedCells cells(uint32_t subspace) const noexcept {
        assert(subspace < _subspaces);
        return vespalib::eval::TypedCells(static_cast<const char*>(_data) + _subspace_mem_size * subspace, _cell_type, _subspace_size);
    }
};

}
