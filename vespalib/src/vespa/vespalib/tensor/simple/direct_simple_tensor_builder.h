// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/tensor/direct_tensor_builder.h>
#include "simple_tensor.h"
#include <vespa/vespalib/tensor/tensor_address_builder.h>

namespace vespalib {
namespace tensor {

/**
 * Utility class to build tensors of type SimpleTensor, to be used by
 * tensor operations.
 */
template <> class DirectTensorBuilder<SimpleTensor>
{
public:
    using TensorImplType = SimpleTensor;
    using Dimensions = typename TensorImplType::Dimensions;
    using Cells = typename TensorImplType::Cells;
    using AddressBuilderType = TensorAddressBuilder;
    using AddressRefType = TensorAddress;
    using AddressType = TensorAddress;

private:
    Dimensions _dimensions;
    Cells _cells;

public:
    DirectTensorBuilder()
        : _dimensions(),
          _cells()
    {
    }

    DirectTensorBuilder(const Dimensions &dimensions_in)
        : _dimensions(dimensions_in),
          _cells()
    {
    }

    DirectTensorBuilder(const Dimensions &dimensions_in,
                        const Cells &cells_in)
        : _dimensions(dimensions_in),
          _cells(cells_in)
    {
    }

    Tensor::UP build() {
        return std::make_unique<SimpleTensor>(std::move(_dimensions),
                                              std::move(_cells));
    }

    template <class Function>
    void insertCell(const TensorAddress &address, double value,
                    Function &&func)
    {
        auto res = _cells.insert(std::make_pair(address, value));
        if (!res.second) {
            res.first->second = func(res.first->second, value);
        }
    }

    void insertCell(const TensorAddress &address, double value) {
        // This address should not already exist and a new cell should be inserted.
        insertCell(address, value, [](double, double) -> double { abort(); });
    }

    // Note: moves data from TensorAddressBuilder to new TensorAddress.
    template <class Function>
    void insertCell(TensorAddressBuilder &address, double value,
                    Function &&func)
    {
        auto res =
            _cells.insert(std::make_pair(address.build(), value));
        if (!res.second) {
            res.first->second = func(res.first->second, value);
        }
    }

    void insertCell(TensorAddressBuilder &address, double value) {
        // This address should not already exist and a new cell should be inserted.
        insertCell(address, value, [](double, double) -> double { abort(); });
    }

    Dimensions &dimensions() { return _dimensions; }
    Cells &cells() { return _cells; }
};


} // namespace vespalib::tensor
} // namespace vespalib
