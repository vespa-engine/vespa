// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "direct_sparse_tensor_builder.h"
#include <assert.h>

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
    size_t mem_use = _stash.get_memory_usage().usedBytes();
    if (mem_use < (SparseTensor::STASH_CHUNK_SIZE / 4)) {
        Stash smaller_stash(mem_use);
        Cells copy = _cells;
        for (auto &cell : copy) { 
            SparseTensorAddressRef oldRef = cell.first;
            SparseTensorAddressRef newRef(oldRef, smaller_stash);
            cell.first = newRef;
        }
        assert(smaller_stash.get_memory_usage().allocatedBytes() < mem_use + 128);
        return std::make_unique<SparseTensor>(std::move(_type), std::move(copy), std::move(smaller_stash));
    }
    return std::make_unique<SparseTensor>(std::move(_type), std::move(_cells), std::move(_stash));
}

void DirectSparseTensorBuilder::reserve(uint32_t estimatedCells) {
    _cells.resize(estimatedCells*2);
}

}
