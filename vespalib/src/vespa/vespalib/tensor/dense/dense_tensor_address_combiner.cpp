// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "dense_tensor_address_combiner.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>

namespace vespalib {
namespace tensor {

using Address = DenseTensorAddressCombiner::Address;
using DimensionsMeta = DenseTensorAddressCombiner::DimensionsMeta;

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
    : _ops(),
      _combinedAddress()
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
                                    const CellsIterator &rhsItr)
{
    _combinedAddress.clear();
    AddressReader lhsReader(lhsItr.address());
    AddressReader rhsReader(rhsItr.address());
    for (const auto &op : _ops) {
        switch (op) {
        case AddressOp::LHS:
            _combinedAddress.emplace_back(lhsReader.nextLabel());
            break;
        case AddressOp::RHS:
            _combinedAddress.emplace_back(rhsReader.nextLabel());
            break;
        case AddressOp::BOTH:
            size_t lhsLabel = lhsReader.nextLabel();
            size_t rhsLabel = rhsReader.nextLabel();
            if (lhsLabel != rhsLabel) {
                return false;
            }
            _combinedAddress.emplace_back(lhsLabel);
        }
    }
    assert(!lhsReader.valid());
    assert(!rhsReader.valid());
    return true;
}

namespace {

void
validateDimensionsMeta(const DimensionsMeta &dimensionsMeta)
{
    for (size_t i = 1; i < dimensionsMeta.size(); ++i) {
        const auto &prevDimMeta = dimensionsMeta[i-1];
        const auto &currDimMeta = dimensionsMeta[i];
        if ((prevDimMeta.dimension() == currDimMeta.dimension()) &&
                (prevDimMeta.size() != currDimMeta.size()))
        {
            throw IllegalArgumentException(make_string(
                    "Shared dimension '%s' has mis-matching label ranges: "
                    "[0, %zu> vs [0, %zu>. This is not supported.",
                    prevDimMeta.dimension().c_str(), prevDimMeta.size(), currDimMeta.size()));
        }
    }
}

}

DimensionsMeta
DenseTensorAddressCombiner::combineDimensions(const DimensionsMeta &lhs, const DimensionsMeta &rhs)
{
    DimensionsMeta result;
    std::set_union(lhs.cbegin(), lhs.cend(),
                   rhs.cbegin(), rhs.cend(),
                   std::back_inserter(result));
    validateDimensionsMeta(result);
    return result;
}

} // namespace vespalib::tensor
} // namespace vespalib
