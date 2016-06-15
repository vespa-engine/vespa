// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "compact_tensor_v2_match.h"
#include "compact_tensor_v2_address_decoder.h"

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
transformAddress(CompactTensorV2AddressBuilder &builder,
                 CompactTensorAddressRef ref,
                 const std::vector<AddressOp> &ops)
{
    builder.clear();
    CompactTensorV2AddressDecoder addr(ref);
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
CompactTensorV2Match::fastMatch(const TensorImplType &lhs,
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
CompactTensorV2Match::slowMatch(const TensorImplType &lhs,
                                const TensorImplType &rhs)
{
    std::vector<AddressOp> ops;
    CompactTensorV2AddressBuilder addressBuilder;
    CompactTensorV2AddressPadder addressPadder(_builder.dimensions(),
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

CompactTensorV2Match::CompactTensorV2Match(const TensorImplType &lhs,
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
