// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sparse_tensor.h"
#include "sparse_tensor_t.h"
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
template<typename LCT, typename RCT>
class SparseTensorMatch
{
public:
    using OCT = typename eval::UnifyCellTypes<LCT,RCT>::type;
    DirectSparseTensorBuilder<OCT> _builder;
private:
    void fastMatch(const SparseTensorT<LCT> &lhs, const SparseTensorT<RCT> &rhs);
public:
    SparseTensorMatch(const SparseTensorT<LCT> &lhs, const SparseTensorT<RCT> &rhs, eval::ValueType res_type);
    Tensor::UP result() {
        return _builder.build();
    }
};

}
