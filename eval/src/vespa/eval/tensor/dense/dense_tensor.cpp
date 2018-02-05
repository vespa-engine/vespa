// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/eval/eval/operation.h>

using vespalib::eval::TensorSpec;

namespace vespalib::tensor {

namespace {

size_t
calcCellsSize(const eval::ValueType &type)
{
    size_t cellsSize = 1;
    for (const auto &dim : type.dimensions()) {
        cellsSize *= dim.size;
    }
    return cellsSize;
}

void
checkCellsSize(const DenseTensor &arg)
{
    auto cellsSize = calcCellsSize(arg.fast_type());
    if (arg.cellsRef().size() != cellsSize) {
        throw IllegalStateException(make_string("Wrong cell size, "
                                                "expected=%zu, "
                                                "actual=%zu",
                                                cellsSize,
                                                arg.cellsRef().size()));
    }
}

}

DenseTensor::DenseTensor()
    : DenseTensorView(_type),
      _type(eval::ValueType::double_type()),
      _cells(1)
{
    initCellsRef(CellsRef(_cells));
}

DenseTensor::DenseTensor(const eval::ValueType &type_in,
                         const Cells &cells_in)
    : DenseTensorView(_type),
      _type(type_in),
      _cells(cells_in)
{
    initCellsRef(CellsRef(_cells));
    checkCellsSize(*this);
}


DenseTensor::DenseTensor(const eval::ValueType &type_in,
                         Cells &&cells_in)
    : DenseTensorView(_type),
      _type(type_in),
      _cells(std::move(cells_in))
{
    initCellsRef(CellsRef(_cells));
    checkCellsSize(*this);
}

DenseTensor::DenseTensor(eval::ValueType &&type_in,
                         Cells &&cells_in)
    : DenseTensorView(_type),
      _type(std::move(type_in)),
      _cells(std::move(cells_in))
{
    initCellsRef(CellsRef(_cells));
    checkCellsSize(*this);
}

bool
DenseTensor::operator==(const DenseTensor &rhs) const
{
    return (_type == rhs._type) &&
            (_cells == rhs._cells);
}

}

