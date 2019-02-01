// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib::tensor {
    struct Tensor;
    class DenseTensor;
}

namespace vespalib::tensor::dense {

/**
 * Creates a new tensor using all combinations of input tensor cells with matching
 * labels for common dimensions, using func to calculate new cell value
 * based on the cell values in the input tensors.
 */
template <typename Function>
std::unique_ptr<Tensor>
apply(const DenseTensorView &lhs, const Tensor &rhs, Function &&func);
template <typename Function>
std::unique_ptr<Tensor>
apply(const DenseTensorView &lhs, const DenseTensorView &rhs, Function &&func);

}
