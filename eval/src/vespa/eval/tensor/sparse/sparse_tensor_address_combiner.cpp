// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_address_combiner.h"
#include "sparse_tensor_address_decoder.h"
#include <vespa/eval/eval/value_type.h>
#include <cassert>

namespace vespalib {
namespace tensor {
namespace sparse {

TensorAddressCombiner::TensorAddressCombiner(const eval::ValueType &lhs,
                                             const eval::ValueType &rhs)
{
    auto rhsItr = rhs.dimensions().cbegin();
    auto rhsItrEnd = rhs.dimensions().cend();
    for (auto &lhsDim : lhs.dimensions()) {
        while (rhsItr != rhsItrEnd && rhsItr->name < lhsDim.name) {
            _ops.push_back(AddressOp::RHS);
            ++rhsItr;
        }
        if (rhsItr != rhsItrEnd && rhsItr->name == lhsDim.name) {
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
