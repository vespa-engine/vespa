// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "direct_dense_tensor_builder.h"

namespace vespalib::tensor {

using Address = DirectDenseTensorBuilder::Address;
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

}

DirectDenseTensorBuilder::~DirectDenseTensorBuilder() = default;

DirectDenseTensorBuilder::DirectDenseTensorBuilder(const ValueType &type_in)
    : _type(type_in),
      _cells(calculateCellsSize(_type))
{
}

Tensor::UP
DirectDenseTensorBuilder::build()
{
    return std::make_unique<DenseTensor>(std::move(_type), std::move(_cells));
}

}

