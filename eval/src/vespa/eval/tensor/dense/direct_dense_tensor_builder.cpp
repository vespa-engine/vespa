// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "direct_dense_tensor_builder.h"

namespace vespalib {
namespace tensor {

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

size_t
calculateCellAddress(const Address &address, const ValueType &type)
{
    assert(address.size() == type.dimensions().size());
    size_t result = 0;
    for (size_t i = 0; i < address.size(); ++i) {
        result *= type.dimensions()[i].size;
        result += address[i];
    }
    return result;
}

}

DirectDenseTensorBuilder::~DirectDenseTensorBuilder() { }

DirectDenseTensorBuilder::DirectDenseTensorBuilder(const ValueType &type_in)
    : _type(type_in),
      _cells(calculateCellsSize(_type))
{
}

void
DirectDenseTensorBuilder::insertCell(const Address &address, double cellValue)
{
    size_t cellAddress = calculateCellAddress(address, _type);
    assert(cellAddress < _cells.size());
    _cells[cellAddress] = cellValue;
}

Tensor::UP
DirectDenseTensorBuilder::build()
{
    return std::make_unique<DenseTensor>(std::move(_type), std::move(_cells));
}

} // namespace tensor
} // namesapce vespalib
