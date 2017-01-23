// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib {
namespace tensor {
class Tensor;
class SparseTensor;
namespace sparse {

/**
 * Create new tensor using all combinations of input tensor cells with matching
 * labels for common dimensions, using func to calculate new cell value
 * based on the cell values in the input tensors.
 */
template <typename Function>
std::unique_ptr<Tensor>
apply(const SparseTensor &lhs, const SparseTensor &rhs, Function &&func);


} // namespace vespalib::tensor::sparse
} // namespace vespalib::tensor
} // namespace vespalib
