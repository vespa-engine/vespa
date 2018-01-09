// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dense_tensor_cells_iterator.h"
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/types.h>
#include <vespa/eval/eval/value_type.h>

namespace vespalib::tensor {


/**
 * Combines two dense tensor addresses to a new tensor address.
 * The resulting dimensions is the union of the input dimensions and
 * common dimensions must have matching labels.
 */
class DenseTensorAddressCombiner
{
public:
    using Mapping = std::vector<std::pair<uint32_t, uint32_t>>;

private:
    using Address = DenseTensorCellsIterator::Address;
    using CellsRef = vespalib::ConstArrayRef<double>;
    using size_type = eval::ValueType::Dimension::size_type;

    const eval::ValueType &_rightType;
    Address                _combinedAddress;
    CellsRef               _rightCells;
    Address                _rightAddress;
    std::vector<size_t>    _rightAccumulatedSize;
    Mapping                _left;
    Mapping                _commonRight;
    Mapping                _right;
    void update(const Address & addr, const Mapping & mapping) {
        for (const auto & m : mapping) {
            _combinedAddress[m.first] = addr[m.second];
        }
    }
    double rightCell(size_t cellIdx) const { return _rightCells[cellIdx]; }
    size_t rightIndex(const Address &address) const {
        size_t cellIdx(0);
        for (uint32_t i(0); i < address.size(); i++) {
            cellIdx += address[i]*_rightAccumulatedSize[i];
        }
        return cellIdx;
    }
public:
    DenseTensorAddressCombiner(const eval::ValueType &lhs, const eval::ValueType &rhs, CellsRef rhsCells);
    ~DenseTensorAddressCombiner();
    void updateLeftAndCommon(const Address & addr) { update(addr, _left); }

    bool hasAnyRightOnlyDimensions() const { return ! _right.empty(); }

    const Address &address() const { return _combinedAddress; }

    bool updateCommonRight() {
        for (const auto & m : _commonRight) {
            if (_combinedAddress[m.first] >= _rightType.dimensions()[m.second].size) {
                return false;
            }
            _rightAddress[m.second] = _combinedAddress[m.first];
        }
        return true;
    }
    double rightCell() { return rightCell(rightIndex(_rightAddress)); }

    template <typename Func>
    void for_each(Func && func) {
        const int32_t lastDimension = _right.size() - 1;
        int32_t curDimension = lastDimension;
        size_t rightCellIdx = rightIndex(_rightAddress);
        while (curDimension >= 0) {
            const uint32_t rdim = _right[curDimension].second;
            const uint32_t cdim = _right[curDimension].first;
            size_type & cindex = _combinedAddress[cdim];
            if (curDimension == lastDimension) {
                for (cindex = 0; cindex < _rightType.dimensions()[rdim].size; cindex++) {
                    func(_combinedAddress, rightCell(rightCellIdx));
                    rightCellIdx += _rightAccumulatedSize[rdim];
                }
                cindex = 0;
                rightCellIdx -= _rightAccumulatedSize[rdim] * _rightType.dimensions()[rdim].size;
                curDimension--;
            } else {
                if (cindex < _rightType.dimensions()[rdim].size) {
                    cindex++;
                    rightCellIdx += _rightAccumulatedSize[rdim];
                    curDimension++;
                } else {
                    rightCellIdx -= _rightAccumulatedSize[rdim] * _rightType.dimensions()[rdim].size;
                    cindex = 0;
                    curDimension--;
                }
            }
        }
    }

    static eval::ValueType combineDimensions(const eval::ValueType &lhs, const eval::ValueType &rhs);
};


}
