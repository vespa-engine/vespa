// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/types.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/vespalib/util/arrayref.h>

namespace vespalib::tensor {

/**
 * Utility class to iterate over cells in a dense tensor.
 */
class DenseTensorCellsIterator
{
public:
    using size_type = eval::ValueType::Dimension::size_type;
    using Address = std::vector<size_type>;
private:
    using CellsRef = vespalib::ConstArrayRef<double>;
    const eval::ValueType &_type;
    CellsRef       _cells;
    size_t         _cellIdx;
    const int32_t  _lastDimension;
    Address        _address;
public:
    DenseTensorCellsIterator(const eval::ValueType &type_in, CellsRef cells);
    ~DenseTensorCellsIterator();
    void next() {
        ++_cellIdx;
        for (int32_t i = _lastDimension; i >= 0; --i) {
            _address[i]++;
            if (__builtin_expect((_address[i] != _type.dimensions()[i].size), true)) {
                // Outer dimension labels can only be increased when this label wraps around.
                break;
            } else {
                _address[i] = 0;
            }
        }
    }
    bool valid() const { return _cellIdx < _cells.size(); }
    double cell() const { return _cells[_cellIdx]; }
    const Address &address() const { return _address; }
    const eval::ValueType &fast_type() const { return _type; }
};

}
