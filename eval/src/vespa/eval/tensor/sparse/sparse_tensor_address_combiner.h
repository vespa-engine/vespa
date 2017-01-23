// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sparse_tensor_address_builder.h"
#include <vespa/eval/tensor/types.h>

namespace vespalib {
namespace eval { class ValueType; }
namespace tensor {
namespace sparse {

/**
 * Combine two tensor addresses to a new tensor address.  Common dimensions
 * must have matching labels.
 */
class TensorAddressCombiner : public SparseTensorAddressBuilder
{
    enum class AddressOp
    {
        LHS,
        RHS,
        BOTH
    };

    std::vector<AddressOp> _ops;

public:
    TensorAddressCombiner(const eval::ValueType &lhs,
                          const eval::ValueType &rhs);

    ~TensorAddressCombiner();

    bool combine(SparseTensorAddressRef lhsRef, SparseTensorAddressRef rhsRef);
};


} // namespace vespalib::tensor::sparse
} // namespace vespalib::tensor
} // namespace vespalib
