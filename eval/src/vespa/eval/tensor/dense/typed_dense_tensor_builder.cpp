// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include "typed_dense_tensor_builder.h"

namespace vespalib::tensor {

using Address = DenseTensorView::Address;
using eval::ValueType;

namespace {

size_t
calculateCellsSize(const ValueType &type)
{
    size_t cellsSize = 1;
    for (const auto &dim : type.dimensions()) {
        cellsSize *= dim.size;
    }
    return cellsSize;
}

} // namespace

template <typename CT>
TypedDenseTensorBuilder<CT>::~TypedDenseTensorBuilder() = default;

template <typename CT>
TypedDenseTensorBuilder<CT>::TypedDenseTensorBuilder(const ValueType &type_in)
    : _type(type_in),
      _cells(calculateCellsSize(_type))
{
    assert(vespalib::eval::check_cell_type<CT>(_type.cell_type()));
}

template <typename CT>
Tensor::UP
TypedDenseTensorBuilder<CT>::build()
{
    return std::make_unique<DenseTensor<CT>>(std::move(_type), std::move(_cells));
}

template class TypedDenseTensorBuilder<double>;
template class TypedDenseTensorBuilder<float>;

} // namespace
