// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sparse_tensor.h"
#include "direct_sparse_tensor_builder.h"

namespace vespalib::tensor {

/**
 * Returns the match product of two tensors.
 * This returns a tensor which contains the matching cells in the two tensors,
 * with their values multiplied.
 *
 * Only used when two tensors have exactly the same dimensions,
 * this is the Hadamard product.
 */
class SparseTensorMatch
{
public:
    DirectSparseTensorBuilder _builder;
private:
    void fastMatch(const SparseTensor &lhs, const SparseTensor &rhs);
public:
    SparseTensorMatch(const SparseTensor &lhs, const SparseTensor &rhs);
    Tensor::UP result() {
        return _builder.build();
    }
};

}
