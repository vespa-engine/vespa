// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_match.h"
#include "sparse_tensor_address_decoder.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace vespalib::tensor {

namespace {

enum class AddressOp
{
    REMOVE,
    PAD,
    COPY
};


void
buildTransformOps(std::vector<AddressOp> &ops,
                  const eval::ValueType &lhs,
                  const eval::ValueType &rhs)
{
    auto rhsItr = rhs.dimensions().cbegin();
    auto rhsItrEnd = rhs.dimensions().cend();
    for (auto &lhsDim : lhs.dimensions()) {
        while (rhsItr != rhsItrEnd && rhsItr->name < lhsDim.name) {
            ops.push_back(AddressOp::PAD);
            ++rhsItr;
        }
        if (rhsItr != rhsItrEnd && rhsItr->name == lhsDim.name) {
            ops.push_back(AddressOp::COPY);
            ++rhsItr;
        } else {
            ops.push_back(AddressOp::REMOVE);
        }
    }
    while (rhsItr != rhsItrEnd) {
        ops.push_back(AddressOp::PAD);
        ++rhsItr;
    }
}


bool
transformAddress(SparseTensorAddressBuilder &builder,
                 SparseTensorAddressRef ref,
                 const std::vector<AddressOp> &ops)
{
    builder.clear();
    SparseTensorAddressDecoder addr(ref);
    for (auto op : ops) {
        switch (op) {
        case AddressOp::REMOVE:
        {
            auto label = addr.decodeLabel();
            if (label.size() != 0u) {
                return false;
            }
        }
        break;
        case AddressOp::PAD:
            builder.addUndefined();
            break;
        case AddressOp::COPY:
            builder.add(addr.decodeLabel());
        }
    }
    assert(!addr.valid());
    return true;
}

}


void
SparseTensorMatch::fastMatch(const TensorImplType &lhs, const TensorImplType &rhs)
{
    _builder.reserve(lhs.cells().size());
    for (const auto &lhsCell : lhs.cells()) {
        auto rhsItr = rhs.cells().find(lhsCell.first);
        if (rhsItr != rhs.cells().end()) {
            _builder.insertCell(lhsCell.first, lhsCell.second * rhsItr->second);
        }
    }
}

void
SparseTensorMatch::slowMatch(const TensorImplType &lhs, const TensorImplType &rhs)
{
    std::vector<AddressOp> ops;
    SparseTensorAddressBuilder addressBuilder;
    SparseTensorAddressPadder addressPadder(_builder.fast_type(), lhs.fast_type());
    buildTransformOps(ops, lhs.fast_type(), rhs.fast_type());
    for (const auto &lhsCell : lhs.cells()) {
        if (!transformAddress(addressBuilder, lhsCell.first, ops)) {
            continue;
        }
        SparseTensorAddressRef ref(addressBuilder.getAddressRef());
        auto rhsItr = rhs.cells().find(ref);
        if (rhsItr != rhs.cells().end()) {
            addressPadder.padAddress(lhsCell.first);
            _builder.insertCell(addressPadder, lhsCell.second * rhsItr->second);
        }
    }
}

SparseTensorMatch::SparseTensorMatch(const TensorImplType &lhs, const TensorImplType &rhs)
    : Parent(lhs.combineDimensionsWith(rhs))
{
    if ((lhs.fast_type().dimensions().size() == rhs.fast_type().dimensions().size()) &&
        (lhs.fast_type().dimensions().size() == _builder.fast_type().dimensions().size())) {
        // Ensure that first tensor to fastMatch has fewest cells.
        if (lhs.cells().size() <= rhs.cells().size()) {
            fastMatch(lhs, rhs);
        } else {
            fastMatch(rhs, lhs);
        }
    } else {
        slowMatch(lhs, rhs);
    }
}

}
