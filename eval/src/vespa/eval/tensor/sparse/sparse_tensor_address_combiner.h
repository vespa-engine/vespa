// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sparse_tensor_address_builder.h"

#define VESPA_DLL_LOCAL  __attribute__ ((visibility("hidden")))

namespace vespalib::eval { class ValueType; }
namespace vespalib::tensor::sparse {

/**
 * Combine two tensor addresses to a new tensor address.  Common dimensions
 * must have matching labels.
 */
class TensorAddressCombiner : public SparseTensorAddressBuilder
{
    enum class AddressOp { LHS, RHS, BOTH };

    std::vector<AddressOp> _ops;
public:
    TensorAddressCombiner(const eval::ValueType &lhs, const eval::ValueType &rhs);
    ~TensorAddressCombiner();

    VESPA_DLL_LOCAL bool combine(SparseTensorAddressRef lhsRef, SparseTensorAddressRef rhsRef);
    size_t numOverlappingDimensions() const;
    size_t numDimensions() const { return _ops.size(); }
};

}

