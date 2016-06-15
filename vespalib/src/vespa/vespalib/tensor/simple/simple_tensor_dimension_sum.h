// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/tensor/tensor_operation.h>

namespace vespalib {
namespace tensor {

/**
 * Returns a tensor with the given dimension removed and the cell values in that dimension summed.
 */
class SimpleTensorDimensionSum : public TensorOperation<SimpleTensor>
{
public:
    using TensorImplType = SimpleTensor;
    using Parent = TensorOperation<SimpleTensor>;
    using AddressBuilderType = typename Parent::AddressBuilderType;
    using AddressType = typename Parent::AddressType;
    using Parent::_builder;
    SimpleTensorDimensionSum(const TensorImplType &tensor,
                             const vespalib::string &dimension);
};


} // namespace vespalib::tensor
} // namespace vespalib
