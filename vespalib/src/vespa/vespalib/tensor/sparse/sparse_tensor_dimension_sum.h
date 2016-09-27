// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/tensor/tensor_operation.h>

namespace vespalib {
namespace tensor {

/**
 * Returns a tensor with the given dimension removed and the cell values in that dimension summed.
 */
class SparseTensorDimensionSum : public TensorOperation<SparseTensor>
{
public:
    using TensorImplType = SparseTensor;
    using Parent = TensorOperation<SparseTensor>;
    using AddressBuilderType = typename Parent::AddressBuilderType;
    using Parent::_builder;
    SparseTensorDimensionSum(const TensorImplType &tensor,
                                const vespalib::string &dimension);
};

} // namespace vespalib::tensor
} // namespace vespalib
