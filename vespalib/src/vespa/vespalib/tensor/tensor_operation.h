// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "direct_tensor_builder.h"
#include <vespa/vespalib/tensor/sparse/direct_sparse_tensor_builder.h>

namespace vespalib {
namespace tensor {

/**
 * Base class for an operation over tensors.
 */
template <class TensorT>
class TensorOperation
{
public:
    using TensorImplType = TensorT;
    using MyTensorBuilder = DirectTensorBuilder<TensorT>;
    using Dimensions = typename TensorImplType::Dimensions;
    using Cells = typename TensorImplType::Cells;
    using AddressBuilderType = typename MyTensorBuilder::AddressBuilderType;
    using AddressRefType = typename MyTensorBuilder::AddressRefType;
    using AddressType = typename MyTensorBuilder::AddressType;
protected:
    MyTensorBuilder _builder;
    Dimensions &_dimensions;
    Cells &_cells;

public:
    TensorOperation()
        : _builder(),
          _dimensions(_builder.dimensions()),
          _cells(_builder.cells())
    {}
    TensorOperation(const Dimensions &dimensions)
        : _builder(dimensions),
          _dimensions(_builder.dimensions()),
          _cells(_builder.cells())
    {}
    TensorOperation(const Dimensions &dimensions, const Cells &cells)
        : _builder(dimensions, cells),
          _dimensions(_builder.dimensions()),
          _cells(_builder.cells())
    {}
    Tensor::UP result() {
        return _builder.build();
    }
};

} // namespace vespalib::tensor
} // namespace vespalib
