// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/tensor/tensor_operation.h>

namespace vespalib::tensor {

/**
 * Returns the match product of two tensors.
 * This returns a tensor which contains the matching cells in the two tensors,
 * with their values multiplied.
 *
 * Only used when two tensors have exactly the same dimensions,
 * this is the Hadamard product.
 */
class SparseTensorMatch : public TensorOperation<SparseTensor>
{
public:
    using Parent = TensorOperation<SparseTensor>;
    using typename Parent::TensorImplType;
    using Parent::_builder;
private:
    void fastMatch(const TensorImplType &lhs, const TensorImplType &rhs);
public:
    SparseTensorMatch(const TensorImplType &lhs, const TensorImplType &rhs);
};

}
