// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_address_combiner.h"
#include "sparse_tensor_address_decoder.h"
#include <vespa/eval/eval/value_type.h>
#include <cassert>

namespace vespalib::tensor::sparse {

TensorAddressCombiner::TensorAddressCombiner(const eval::ValueType &lhs, const eval::ValueType &rhs)
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

TensorAddressCombiner::~TensorAddressCombiner() = default;

size_t
TensorAddressCombiner::numOverlappingDimensions() const {
    size_t count = 0;
    for (AddressOp op : _ops) {
        if (op == AddressOp::BOTH) {
            count++;
        }
    }
    return count;
}

bool
TensorAddressCombiner::combine(SparseTensorAddressRef lhsRef,
                               SparseTensorAddressRef rhsRef)
{
    clear();
    ensure_room(lhsRef.size() + rhsRef.size());
    SparseTensorAddressDecoder lhs(lhsRef);
    SparseTensorAddressDecoder rhs(rhsRef);
    for (auto op : _ops) {
        switch (op) {
        case AddressOp::LHS:
            append(lhs.decodeLabel());
            break;
        case AddressOp::RHS:
            append(rhs.decodeLabel());
            break;
        case AddressOp::BOTH:
            auto lhsLabel(lhs.decodeLabel());
            auto rhsLabel(rhs.decodeLabel());
            if (lhsLabel != rhsLabel) {
                return false;
            }
            append(lhsLabel);
        }
    }
    return true;
}

}
