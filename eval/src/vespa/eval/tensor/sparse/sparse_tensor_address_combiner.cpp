// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_address_combiner.h"
#include "sparse_tensor_address_decoder.h"
#include <vespa/eval/eval/value_type.h>
#include <vespa/vespalib/util/overload.h>
#include <vespa/vespalib/util/visit_ranges.h>

namespace vespalib::tensor::sparse {

TensorAddressCombiner::TensorAddressCombiner(const eval::ValueType &lhs, const eval::ValueType &rhs)
{
    auto visitor = overload{
        [this](visit_ranges_first, const auto &) { _ops.push_back(AddressOp::LHS); },
        [this](visit_ranges_second, const auto &) { _ops.push_back(AddressOp::RHS); },
        [this](visit_ranges_both, const auto &, const auto &) { _ops.push_back(AddressOp::BOTH); }
    };
    visit_ranges(visitor,
                 lhs.dimensions().cbegin(), lhs.dimensions().cend(),
                 rhs.dimensions().cbegin(), rhs.dimensions().cend(),
                 [](const auto & li, const auto & ri) { return li.name < ri.name; });
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
