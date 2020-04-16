// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exceptions.h>

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

template<typename T>
void
checkCellsSize(const DenseTensor<T> &arg)
{
    auto cellsSize = calcCellsSize(arg.fast_type());
    if (arg.cellsRef().size != cellsSize) {
        throw IllegalStateException(make_string("Wrong cell size, "
                                                "expected=%zu, "
                                                "actual=%zu",
                                                cellsSize,
                                                arg.cellsRef().size));
    }
    if (arg.fast_type().cell_type() != arg.cellsRef().type) {
        throw IllegalStateException(make_string("Wrong cell type, "
                                                "expected=%u, "
                                                "actual=%u",
                                                (unsigned char)arg.fast_type().cell_type(),
                                                (unsigned char)arg.cellsRef().type));
    }
}

}

template <typename CT>
DenseTensor<CT>::DenseTensor(eval::ValueType type_in,
                             std::vector<CT> &&cells_in)
    : DenseTensorView(_type),
      _type(std::move(type_in)),
      _cells(std::move(cells_in))
{
    initCellsRef(TypedCells(_cells));
    checkCellsSize(*this);
}

template <typename CT>
DenseTensor<CT>::~DenseTensor() = default;

template <typename CT>
template <typename RCT>
bool
DenseTensor<CT>::operator==(const DenseTensor<RCT> &rhs) const
{
    if (_type != rhs._type) return false;
    if (_cells.size != rhs._cells.size) return false;
    for (size_t i = 0; i < _cells.size; i++) {
        if (_cells[i]  != rhs._cells[i]) return false;
    }
    return true;
}

template class DenseTensor<float>;
template class DenseTensor<double>;

}
