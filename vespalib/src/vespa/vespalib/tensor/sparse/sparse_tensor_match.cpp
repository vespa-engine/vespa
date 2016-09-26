// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "sparse_tensor_match.h"
#include "sparse_tensor_address_decoder.h"

namespace vespalib {
namespace tensor {

namespace {

enum class AddressOp
{
    REMOVE,
    PAD,
    COPY
};


void
buildTransformOps(std::vector<AddressOp> &ops,
                  const TensorDimensions &lhs,
                  const TensorDimensions &rhs)
{
    auto rhsItr = rhs.cbegin();
    auto rhsItrEnd = rhs.cend();
    for (auto &lhsDim : lhs) {
        while (rhsItr != rhsItrEnd && *rhsItr < lhsDim) {
            ops.push_back(AddressOp::PAD);
            ++rhsItr;
        }
        if (rhsItr != rhsItrEnd && *rhsItr == lhsDim) {
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
                 CompactTensorAddressRef ref,
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
SparseTensorMatch::fastMatch(const TensorImplType &lhs,
                                const TensorImplType &rhs)
{
    for (const auto &lhsCell : lhs.cells()) {
        auto rhsItr = rhs.cells().find(lhsCell.first);
        if (rhsItr != rhs.cells().end()) {
            _builder.insertCell(lhsCell.first, lhsCell.second * rhsItr->second);
        }
    }
}

void
SparseTensorMatch::slowMatch(const TensorImplType &lhs,
                                const TensorImplType &rhs)
{
    std::vector<AddressOp> ops;
    SparseTensorAddressBuilder addressBuilder;
    SparseTensorAddressPadder addressPadder(_builder.dimensions(),
                                               lhs.dimensions());
    buildTransformOps(ops, lhs.dimensions(), rhs.dimensions());
    for (const auto &lhsCell : lhs.cells()) {
        if (!transformAddress(addressBuilder, lhsCell.first, ops)) {
            continue;
        }
        CompactTensorAddressRef ref(addressBuilder.getAddressRef());
        auto rhsItr = rhs.cells().find(ref);
        if (rhsItr != rhs.cells().end()) {
            addressPadder.padAddress(lhsCell.first);
            _builder.insertCell(addressPadder, lhsCell.second * rhsItr->second);
        }
    }
}

SparseTensorMatch::SparseTensorMatch(const TensorImplType &lhs,
                                           const TensorImplType &rhs)
    : Parent(lhs.combineDimensionsWith(rhs))
{
    if ((lhs.dimensions().size() == rhs.dimensions().size()) &&
        (lhs.dimensions().size() == _builder.dimensions().size())) {
        fastMatch(lhs, rhs);
    } else {
        slowMatch(lhs, rhs);
    }
}


} // namespace vespalib::tensor
} // namespace vespalib
