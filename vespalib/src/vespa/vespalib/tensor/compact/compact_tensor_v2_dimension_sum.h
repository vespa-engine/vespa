// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/tensor/tensor_operation.h>

namespace vespalib {
namespace tensor {

/**
 * Returns a tensor with the given dimension removed and the cell values in that dimension summed.
 */
class CompactTensorV2DimensionSum : public TensorOperation<CompactTensorV2>
{
public:
    using TensorImplType = CompactTensorV2;
    using Parent = TensorOperation<CompactTensorV2>;
    using AddressBuilderType = typename Parent::AddressBuilderType;
    using AddressType = typename Parent::AddressType;
    using Parent::_builder;
    CompactTensorV2DimensionSum(const TensorImplType &tensor,
                                const vespalib::string &dimension);
};

} // namespace vespalib::tensor
} // namespace vespalib
