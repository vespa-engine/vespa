// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_cells_iterator.h"

namespace vespalib::tensor {

DenseTensorCellsIterator::DenseTensorCellsIterator(const eval::ValueType &type_in, CellsRef cells)
    : _type(type_in),
      _cells(cells),
      _cellIdx(0),
      _lastDimension(type_in.dimensions().size() - 1),
      _address(type_in.dimensions().size(), 0)
{}
DenseTensorCellsIterator::~DenseTensorCellsIterator() = default;

}
