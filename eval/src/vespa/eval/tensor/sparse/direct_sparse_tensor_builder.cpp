// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "direct_sparse_tensor_builder.h"

namespace vespalib::tensor {

void
DirectSparseTensorBuilder::copyCells(const Cells &cells_in)
{
    for (const auto &cell : cells_in) {
        SparseTensorAddressRef oldRef = cell.first;
        SparseTensorAddressRef newRef(oldRef, _stash);
        _cells[newRef] = cell.second;
    }
}

DirectSparseTensorBuilder::DirectSparseTensorBuilder()
    : _stash(SparseTensor::STASH_CHUNK_SIZE),
      _type(eval::ValueType::double_type()),
      _cells()
{
}

DirectSparseTensorBuilder::DirectSparseTensorBuilder(const eval::ValueType &type_in)
    : _stash(SparseTensor::STASH_CHUNK_SIZE),
      _type(type_in),
      _cells()
{
}

DirectSparseTensorBuilder::DirectSparseTensorBuilder(const eval::ValueType &type_in, const Cells &cells_in)
    : _stash(SparseTensor::STASH_CHUNK_SIZE),
      _type(type_in),
      _cells()
{
    copyCells(cells_in);
}

DirectSparseTensorBuilder::~DirectSparseTensorBuilder() = default;

Tensor::UP
DirectSparseTensorBuilder::build() {
    return std::make_unique<SparseTensor>(std::move(_type), std::move(_cells), std::move(_stash));
}

void
DirectSparseTensorBuilder::insertCell(SparseTensorAddressRef address, double value) {
    // This address should not already exist and a new cell should be inserted.
    insertCell(address, value, [](double, double) -> double { HDR_ABORT("should not be reached"); });
}

void
DirectSparseTensorBuilder::insertCell(SparseTensorAddressBuilder &address, double value) {
    // This address should not already exist and a new cell should be inserted.
    insertCell(address.getAddressRef(), value, [](double, double) -> double { HDR_ABORT("should not be reached"); });
}

void DirectSparseTensorBuilder::reserve(uint32_t estimatedCells) {
    _cells.resize(estimatedCells*2);
}

}