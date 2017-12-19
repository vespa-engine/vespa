// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_cells_iterator.h"

namespace vespalib::tensor {

DenseTensorCellsIterator::DenseTensorCellsIterator(const eval::ValueType &type_in, CellsRef cells)
        : _type(type_in),
          _cells(cells),
          _cellIdx(0),
          _address(type_in.dimensions().size(), 0)
{}
DenseTensorCellsIterator::~DenseTensorCellsIterator() = default;

void
DenseTensorCellsIterator::next()
{
    ++_cellIdx;
    for (int64_t i = (_address.size() - 1); i >= 0; --i) {
        _address[i]++;
        if (__builtin_expect((_address[i] != _type.dimensions()[i].size), true)) {
            // Outer dimension labels can only be increased when this label wraps around.
            break;
        } else {
            _address[i] = 0;
        }
    }
}

}
