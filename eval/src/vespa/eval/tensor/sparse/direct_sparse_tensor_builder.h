// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/tensor/direct_tensor_builder.h>
#include "sparse_tensor.h"
#include "sparse_tensor_address_builder.h"
#include "sparse_tensor_address_padder.h"

namespace vespalib::tensor {

/**
 * Utility class to build tensors of type SparseTensor, to be used by
 * tensor operations.
 */
template <> class DirectTensorBuilder<SparseTensor>
{
public:
    using TensorImplType = SparseTensor;
    using Cells = typename TensorImplType::Cells;
    using AddressBuilderType = SparseTensorAddressBuilder;
    using AddressRefType = SparseTensorAddressRef;

private:
    Stash _stash;
    eval::ValueType _type;
    Cells _cells;

public:
    void
    copyCells(const Cells &cells_in)
    {
        for (const auto &cell : cells_in) {
            SparseTensorAddressRef oldRef = cell.first;
            SparseTensorAddressRef newRef(oldRef, _stash);
            _cells[newRef] = cell.second;
        }
    }

    void
    copyCells(const Cells &cells_in, const eval::ValueType &cells_in_type)
    {
        SparseTensorAddressPadder addressPadder(_type, cells_in_type);
        for (const auto &cell : cells_in) {
            addressPadder.padAddress(cell.first);
            SparseTensorAddressRef oldRef = addressPadder.getAddressRef();
            SparseTensorAddressRef newRef(oldRef, _stash);
            _cells[newRef] = cell.second;
        }
    }

    DirectTensorBuilder()
        : _stash(TensorImplType::STASH_CHUNK_SIZE),
          _type(eval::ValueType::double_type()),
          _cells()
    {
    }

    DirectTensorBuilder(const eval::ValueType &type_in)
        : _stash(TensorImplType::STASH_CHUNK_SIZE),
          _type(type_in),
          _cells()
    {
    }

    DirectTensorBuilder(const eval::ValueType &type_in, const Cells &cells_in)
        : _stash(TensorImplType::STASH_CHUNK_SIZE),
          _type(type_in),
          _cells()
    {
        copyCells(cells_in);
    }

    DirectTensorBuilder(const eval::ValueType &type_in,
                        const Cells &cells_in,
                        const eval::ValueType &cells_in_type)
        : _stash(TensorImplType::STASH_CHUNK_SIZE),
          _type(type_in),
          _cells()
    {
        if (type_in.dimensions().size() == cells_in_type.dimensions().size()) {
            copyCells(cells_in);
        } else {
            copyCells(cells_in, cells_in_type);
        }
    }

    ~DirectTensorBuilder() {}

    Tensor::UP build() {
        return std::make_unique<SparseTensor>(std::move(_type), std::move(_cells), std::move(_stash));
    }

    template <class Function>
    void insertCell(SparseTensorAddressRef address, double value, Function &&func)
    {
        auto res = _cells.insert(std::make_pair(address, value));
        if (res.second) {
            // Replace key with own copy
            res.first->first = SparseTensorAddressRef(address, _stash);
        } else {
            res.first->second = func(res.first->second, value);
        }
    }

    void insertCell(SparseTensorAddressRef address, double value) {
        // This address should not already exist and a new cell should be inserted.
        insertCell(address, value, [](double, double) -> double { abort(); });
    }

    template <class Function>
    void insertCell(SparseTensorAddressBuilder &address, double value, Function &&func)
    {
        insertCell(address.getAddressRef(), value, func);
    }

    void insertCell(SparseTensorAddressBuilder &address, double value) {
        // This address should not already exist and a new cell should be inserted.
        insertCell(address.getAddressRef(), value, [](double, double) -> double { abort(); });
    }

    eval::ValueType &fast_type() { return _type; }
    Cells &cells() { return _cells; }
    void reserve(uint32_t estimatedCells) { _cells.resize(estimatedCells*2); }
};

}
