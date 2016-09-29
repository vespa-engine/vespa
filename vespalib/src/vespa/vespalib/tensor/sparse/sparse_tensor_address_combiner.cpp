// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "sparse_tensor_address_combiner.h"
#include "sparse_tensor_address_decoder.h"

namespace vespalib {
namespace tensor {
namespace sparse {

TensorAddressCombiner::TensorAddressCombiner(const TensorDimensions &lhs,
                                             const TensorDimensions &rhs)
{
    auto rhsItr = rhs.cbegin();
    auto rhsItrEnd = rhs.cend();
    for (auto &lhsDim : lhs) {
        while (rhsItr != rhsItrEnd && *rhsItr < lhsDim) {
            _ops.push_back(AddressOp::RHS);
            ++rhsItr;
        }
        if (rhsItr != rhsItrEnd && *rhsItr == lhsDim) {
            _ops.push_back(AddressOp::BOTH);
            ++rhsItr;
        } else {
            _ops.push_back(AddressOp::LHS);
        }
    }
    while (rhsItr != rhsItrEnd) {
        _ops.push_back(AddressOp::RHS);
        ++rhsItr;
    }
}

TensorAddressCombiner::~TensorAddressCombiner()
{
}

bool
TensorAddressCombiner::combine(SparseTensorAddressRef lhsRef,
                               SparseTensorAddressRef rhsRef)
{
    clear();
    SparseTensorAddressDecoder lhs(lhsRef);
    SparseTensorAddressDecoder rhs(rhsRef);
    for (auto op : _ops) {
        switch (op) {
        case AddressOp::LHS:
            add(lhs.decodeLabel());
            break;
        case AddressOp::RHS:
            add(rhs.decodeLabel());
            break;
        case AddressOp::BOTH:
            auto lhsLabel(lhs.decodeLabel());
            auto rhsLabel(rhs.decodeLabel());
            if (lhsLabel != rhsLabel) {
                return false;
            }
            add(lhsLabel);
        }
    }
    assert(!lhs.valid());
    assert(!rhs.valid());
    return true;
}

} // namespace vespalib::tensor::sparse
} // namespace vespalib::tensor
} // namespace vespalib
