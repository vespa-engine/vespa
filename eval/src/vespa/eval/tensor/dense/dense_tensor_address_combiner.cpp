// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_address_combiner.h"
#include <vespa/vespalib/util/exceptions.h>
#include <cassert>

namespace vespalib::tensor {

DenseTensorAddressCombiner::~DenseTensorAddressCombiner() { }

DenseTensorAddressCombiner::DenseTensorAddressCombiner(const eval::ValueType &lhs,
                                                       const eval::ValueType &rhs)
    : _ops(),
      _combinedAddress()
{
    auto rhsItr = rhs.dimensions().cbegin();
    auto rhsItrEnd = rhs.dimensions().cend();
    for (const auto &lhsDim : lhs.dimensions()) {
        while ((rhsItr != rhsItrEnd) && (rhsItr->name < lhsDim.name)) {
            _right.emplace_back(_ops.size(), rhsItr-rhs.dimensions().cbegin());
            _ops.push_back(AddressOp::RHS);
            ++rhsItr;
        }
        if ((rhsItr != rhsItrEnd) && (rhsItr->name == lhsDim.name)) {
            _left.emplace_back(_ops.size(), _left.size());
            _commonRight.emplace_back(_ops.size(), rhsItr-rhs.dimensions().cbegin());
            _ops.push_back(AddressOp::BOTH);
            ++rhsItr;
        } else {
            _left.emplace_back(_ops.size(), _left.size());
            _ops.push_back(AddressOp::LHS);
        }
    }
    while (rhsItr != rhsItrEnd) {
        _right.emplace_back(_ops.size(), rhsItr-rhs.dimensions().cbegin());
        _ops.push_back(AddressOp::RHS);
        ++rhsItr;
    }
    _combinedAddress.resize(_ops.size());
}

eval::ValueType
DenseTensorAddressCombiner::combineDimensions(const eval::ValueType &lhs,
                                              const eval::ValueType &rhs)
{
    // NOTE: both lhs and rhs are sorted according to dimension names.
    std::vector<eval::ValueType::Dimension> result;
    auto lhsItr = lhs.dimensions().cbegin();
    auto rhsItr = rhs.dimensions().cbegin();
    while (lhsItr != lhs.dimensions().end() &&
           rhsItr != rhs.dimensions().end()) {
        if (lhsItr->name == rhsItr->name) {
            result.emplace_back(lhsItr->name,
                                std::min(lhsItr->size, rhsItr->size));
            ++lhsItr;
            ++rhsItr;
        } else if (lhsItr->name < rhsItr->name) {
            result.emplace_back(*lhsItr++);
        } else {
            result.emplace_back(*rhsItr++);
        }
    }
    while (lhsItr != lhs.dimensions().end()) {
        result.emplace_back(*lhsItr++);
    }
    while (rhsItr != rhs.dimensions().end()) {
        result.emplace_back(*rhsItr++);
    }
    return (result.empty() ?
            eval::ValueType::double_type() :
            eval::ValueType::tensor_type(std::move(result)));
}


CommonDenseTensorCellsIterator::CommonDenseTensorCellsIterator(const Mapping & common,
                                                               const eval::ValueType &type_in,
                                                               CellsRef cells)
    : _type(type_in),
      _cells(cells),
      _address(type_in.dimensions().size(), 0),
      _common(common),
      _mutable(_address.size()),
      _accumulatedSize(_address.size())
{
    for (uint32_t i(0); i < _address.size(); i++) {
        _mutable[i] = i;
    }
    for (auto cur = _common.rbegin(); cur != _common.rend(); cur++) {
        _mutable.erase(_mutable.begin() + cur->second);
    }
    size_t multiplier = 1;
    for (int32_t i(_address.size() - 1); i >= 0; i--) {
        _accumulatedSize[i] = multiplier;
        multiplier *= type_in.dimensions()[i].size;
    }
}
CommonDenseTensorCellsIterator::~CommonDenseTensorCellsIterator() = default;

}
