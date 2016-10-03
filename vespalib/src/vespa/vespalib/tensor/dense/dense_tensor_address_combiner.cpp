// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "dense_tensor_address_combiner.h"

namespace vespalib {
namespace tensor {

using Address = DenseTensorAddressCombiner::Address;

namespace {

class AddressReader
{
private:
    const Address &_address;
    size_t _idx;

public:
    AddressReader(const Address &address)
        : _address(address),
          _idx(0)
    {}
    size_t nextLabel() {
        return _address[_idx++];
    }
    bool valid() {
        return _idx < _address.size();
    }
};

}

DenseTensorAddressCombiner::DenseTensorAddressCombiner(const DimensionsMeta &lhs,
                                                       const DimensionsMeta &rhs)
    : _ops()
{
    auto rhsItr = rhs.cbegin();
    auto rhsItrEnd = rhs.cend();
    for (const auto &lhsDim : lhs) {
        while ((rhsItr != rhsItrEnd) && (rhsItr->dimension() < lhsDim.dimension())) {
            _ops.push_back(AddressOp::RHS);
            ++rhsItr;
        }
        if ((rhsItr != rhsItrEnd) && (rhsItr->dimension() == lhsDim.dimension())) {
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

bool
DenseTensorAddressCombiner::combine(const CellsIterator &lhsItr,
                                    const CellsIterator &rhsItr,
                                    Address &combinedAddress)
{
    combinedAddress.clear();
    AddressReader lhsReader(lhsItr.address());
    AddressReader rhsReader(rhsItr.address());
    for (const auto &op : _ops) {
        switch (op) {
        case AddressOp::LHS:
            combinedAddress.emplace_back(lhsReader.nextLabel());
            break;
        case AddressOp::RHS:
            combinedAddress.emplace_back(rhsReader.nextLabel());
            break;
        case AddressOp::BOTH:
            size_t lhsLabel = lhsReader.nextLabel();
            size_t rhsLabel = rhsReader.nextLabel();
            if (lhsLabel != rhsLabel) {
                return false;
            }
            combinedAddress.emplace_back(lhsLabel);
        }
    }
    assert(!lhsReader.valid());
    assert(!rhsReader.valid());
    return true;
}

} // namespace vespalib::tensor
} // namespace vespalib
