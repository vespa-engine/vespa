// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/tensor/direct_tensor_builder.h>
#include "compact_tensor.h"
#include "compact_tensor_address_builder.h"

namespace vespalib {
namespace tensor {

/**
 * Utility class to build tensors of type CompactTensor, to be used by
 * tensor operations.
 */
template <> class DirectTensorBuilder<CompactTensor>
{
public:
    using TensorImplType = CompactTensor;
    using Dimensions = typename TensorImplType::Dimensions;
    using Cells = typename TensorImplType::Cells;
    using AddressBuilderType = CompactTensorAddressBuilder;
    using AddressRefType = CompactTensorAddressRef;
    using AddressType = CompactTensorAddress;

private:
    Stash _stash;
    Dimensions _dimensions;
    Cells _cells;

public:
    void
    copyCells(const Cells &cells_in)
    {
        for (const auto &cell : cells_in) {
            CompactTensorAddressRef oldRef = cell.first;
            CompactTensorAddressRef newRef(oldRef, _stash);
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

    Tensor::UP build() {
        return std::make_unique<CompactTensor>(std::move(_dimensions),
                                               std::move(_cells),
                                               std::move(_stash));
    }

    template <class Function>
    void insertCell(CompactTensorAddressRef address, double value,
                    Function &&func)
    {
        CompactTensorAddressRef oldRef(address);
        auto res = _cells.insert(std::make_pair(oldRef, value));
        if (res.second) {
            // Replace key with own copy
            res.first->first = CompactTensorAddressRef(oldRef, _stash);
        } else {
            res.first->second = func(res.first->second, value);
        }
    }

    void insertCell(CompactTensorAddressRef address, double value) {
        // This address should not already exist and a new cell should be inserted.
        insertCell(address, value, [](double, double) -> double { abort(); });
    }

    template <class Function>
    void insertCell(CompactTensorAddressBuilder &address, double value,
                    Function &&func)
    {
        insertCell(address.getAddressRef(), value, func);
    }

    void insertCell(CompactTensorAddressBuilder &address, double value) {
        // This address should not already exist and a new cell should be inserted.
        insertCell(address.getAddressRef(), value,
                [](double, double) -> double { abort(); });
    }

    Dimensions &dimensions() { return _dimensions; }
    Cells &cells() { return _cells; }
};

} // namespace vespalib::tensor
} // namespace vespalib
