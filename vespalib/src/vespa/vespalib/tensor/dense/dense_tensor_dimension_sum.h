// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dense_tensor.h"

namespace vespalib {
namespace tensor {

/**
 * Returns a tensor with the given dimension removed and the cell values in that dimension summed.
 */
class DenseTensorDimensionSum
{
public:
    using TensorImplType = DenseTensor;
private:
    using DimensionMeta = DenseTensor::DimensionMeta;
    using DimensionsMeta = DenseTensor::DimensionsMeta;
    using Cells = DenseTensor::Cells;

    DimensionsMeta   _dimensionsMeta;
    Cells            _cells;

public:
    DenseTensorDimensionSum(const TensorImplType &tensor,
                            const vespalib::string &dimension);

    Tensor::UP result() {
        return std::make_unique<DenseTensor>(std::move(_dimensionsMeta),
                                             std::move(_cells));
    }
};

} // namespace vespalib::tensor
} // namespace vespalib
