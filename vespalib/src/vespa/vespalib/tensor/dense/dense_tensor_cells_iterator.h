// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/tensor/tensor.h>
#include <vespa/vespalib/tensor/types.h>
#include <vespa/vespalib/eval/value_type.h>
#include <vespa/vespalib/tensor/tensor.h>
#include <vespa/vespalib/util/arrayref.h>

namespace vespalib {
namespace tensor {

/**
 * Utility class to iterate over cells in a dense tensor.
 */
class DenseTensorCellsIterator
{
private:
    using CellsRef = vespalib::ConstArrayRef<double>;
    const eval::ValueType &_type;
    CellsRef _cells;
    size_t _cellIdx;
    std::vector<size_t> _address;

public:
    DenseTensorCellsIterator(const eval::ValueType &type_in, CellsRef cells)
        : _type(type_in),
          _cells(cells),
          _cellIdx(0),
          _address(type_in.dimensions().size(), 0)
    {}
    bool valid() const { return _cellIdx < _cells.size(); }
    void next();
    double cell() const { return _cells[_cellIdx]; }
    const std::vector<size_t> &address() const { return _address; }
    const eval::ValueType &type() const { return _type; }
};

} // namespace vespalib::tensor
} // namespace vespalib
