// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "cell_function.h"
#include "tensor_operation.h"

namespace vespalib {
namespace tensor {

/**
 * Returns a tensor with the given function applied to all cells in the input tensor.
 */
template <class TensorT>
class TensorApply : public TensorOperation<TensorT>
{
public:
    using Parent = TensorOperation<TensorT>;
    using typename Parent::TensorImplType;
    using Parent::_builder;
    TensorApply(const TensorImplType &tensor, const CellFunction &func);
};

extern template class TensorApply<SparseTensor>;

} // namespace vespalib::tensor
} // namespace vespalib
