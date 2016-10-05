// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/tensor/direct_tensor_builder.h>
#include "sparse_tensor.h"
#include "sparse_tensor_address_builder.h"
#include "sparse_tensor_address_padder.h"

namespace vespalib {
namespace tensor {

/**
 * Utility class to build tensors of type SparseTensor, to be used by
 * tensor operations.
 */
template <> class DirectTensorBuilder<SparseTensor>
{
public:
    using TensorImplType = SparseTensor;
    using Dimensions = typename TensorImplType::Dimensions;
    using Cells = typename TensorImplType::Cells;
    using AddressBuilderType = SparseTensorAddressBuilder;
    using AddressRefType = SparseTensorAddressRef;

private:
    Stash _stash;
    Dimensions _dimensions;
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
    copyCells(const Cells &cells_in, const Dimensions &cells_in_dimensions)
    {
        SparseTensorAddressPadder addressPadder(_dimensions,
                                                   cells_in_dimensions);
        for (const auto &cell : cells_in) {
            addressPadder.padAddress(cell.first);
            SparseTensorAddressRef oldRef = addressPadder.getAddressRef();
            SparseTensorAddressRef newRef(oldRef, _stash);
            _cells[newRef] = cell.second;
        }
    }

    DirectTensorBuilder()
        : _stash(TensorImplType::STASH_CHUNK_SIZE),
          _dimensions(),
          _cells()
    {
    }

    DirectTensorBuilder(const Dimensions &dimensions_in)
        : _stash(TensorImplType::STASH_CHUNK_SIZE),
          _dimensions(dimensions_in),
          _cells()
    {
    }

    DirectTensorBuilder(const Dimensions &dimensions_in,
                        const Cells &cells_in)
        : _stash(TensorImplType::STASH_CHUNK_SIZE),
          _dimensions(dimensions_in),
          _cells()
    {
        copyCells(cells_in);
    }

    DirectTensorBuilder(const Dimensions &dimensions_in,
                        const Cells &cells_in,
                        const Dimensions &cells_dimensions)
        : _stash(TensorImplType::STASH_CHUNK_SIZE),
          _dimensions(dimensions_in),
          _cells()
    {
        if (dimensions_in.size() == cells_dimensions.size()) {
            copyCells(cells_in);
        } else {
            copyCells(cells_in, cells_dimensions);
        }
    }

    Tensor::UP build() {
        return std::make_unique<SparseTensor>(std::move(_dimensions),
                                                 std::move(_cells),
                                                 std::move(_stash));
    }

    template <class Function>
    void insertCell(SparseTensorAddressRef address, double value,
                    Function &&func)
    {
        SparseTensorAddressRef oldRef(address);
        auto res = _cells.insert(std::make_pair(oldRef, value));
        if (res.second) {
            // Replace key with own copy
            res.first->first = SparseTensorAddressRef(oldRef, _stash);
        } else {
            res.first->second = func(res.first->second, value);
        }
    }

    void insertCell(SparseTensorAddressRef address, double value) {
        // This address should not already exist and a new cell should be inserted.
        insertCell(address, value, [](double, double) -> double { abort(); });
    }

    template <class Function>
    void insertCell(SparseTensorAddressBuilder &address, double value,
                    Function &&func)
    {
        insertCell(address.getAddressRef(), value, func);
    }

    void insertCell(SparseTensorAddressBuilder &address, double value) {
        // This address should not already exist and a new cell should be inserted.
        insertCell(address.getAddressRef(), value, [](double, double) -> double { abort(); });
    }

    Dimensions &dimensions() { return _dimensions; }
    Cells &cells() { return _cells; }
};

} // namespace vespalib::tensor
} // namespace vespalib
