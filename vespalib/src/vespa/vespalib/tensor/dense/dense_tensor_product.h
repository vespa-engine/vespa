// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dense_tensor.h"
#include <vespa/vespalib/tensor/tensor_operation.h>

namespace vespalib {
namespace tensor {

/**
 * Returns the tensor product of the two given dense tensors.
 * This is all combinations of all cells in the first tensor with all cells of
 * the second tensor.
 *
 * Shared dimensions must have the same label range from [0, dimSize>.
 */
class DenseTensorProduct
{
private:
    DenseTensor::DimensionsMeta _dimensionsMeta;
    DenseTensor::Cells _cells;

    void bruteForceProduct(const DenseTensor &lhs, const DenseTensor &rhs);

public:
    DenseTensorProduct(const DenseTensor &lhs, const DenseTensor &rhs);
    Tensor::UP result();
};

} // namespace vespalib::tensor
} // namespace vespalib
