// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/tensor/tensor_operation.h>

namespace vespalib {
namespace tensor {

/**
 * Returns the sparse tensor product of the two given tensors.
 * This is all combinations of all cells in the first tensor with all cells of
 * the second tensor, except the combinations which would have multiple labels
 * for the same dimension due to shared dimensions between the two tensors.
 *
 * If there are no overlapping dimensions this is the regular tensor product.
 * If the two tensors have exactly the same dimensions this is the Hadamard product.
 *
 * The sparse tensor is associative and commutative. Its dimensions are the
 * set of the dimensions of the two input tensors.
 */
class CompactTensorProduct : public TensorOperation<CompactTensor>
{
public:
    using TensorImplType = CompactTensor;
    using Parent = TensorOperation<CompactTensor>;
    using Dimensions = typename Parent::Dimensions;
    using AddressBuilderType = typename Parent::AddressBuilderType;
    using AddressRefType = typename Parent::AddressRefType;
    using AddressType = typename Parent::AddressType;
    using Parent::_builder;

private:
    template <class DimensionsCollection>
    void
    bruteForceProduct(const TensorImplType &lhs, const TensorImplType &rhs);

    void
    fastProduct(const TensorImplType &lhs, const TensorImplType &rhs);

public:
    CompactTensorProduct(const TensorImplType &lhs, const TensorImplType &rhs);
};

} // namespace vespalib::tensor
} // namespace vespalib
