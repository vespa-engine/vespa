// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/tensor/sparse/direct_sparse_tensor_builder.h>

namespace vespalib::tensor {

/**
 * Base class for an operation over tensors.
 */
template <class TensorT>
class TensorOperation
{
public:
    using TensorImplType = TensorT;
    using MyTensorBuilder = DirectSparseTensorBuilder;
    using Cells = typename TensorImplType::Cells;
    using AddressBuilderType = typename MyTensorBuilder::AddressBuilderType;
    using AddressRefType = typename MyTensorBuilder::AddressRefType;
protected:
    MyTensorBuilder _builder;
    eval::ValueType &_type;
    Cells &_cells;

public:
    TensorOperation()
        : _builder(),
          _type(_builder.fast_type()),
          _cells(_builder.cells())
    {}
    TensorOperation(const eval::ValueType &type)
        : _builder(type),
          _type(_builder.fast_type()),
          _cells(_builder.cells())
    {}
    TensorOperation(const eval::ValueType &type, const Cells &cells)
        : _builder(type, cells),
          _type(_builder.fast_type()),
          _cells(_builder.cells())
    {}
    Tensor::UP result() {
        return _builder.build();
    }
};

}
