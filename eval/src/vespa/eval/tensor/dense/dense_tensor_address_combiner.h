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

    class AddressContext {
    public:
        AddressContext(const eval::ValueType &type, CellsRef cells);
        size_type dimSize(uint32_t dim) const { return _type.dimensions()[dim].size; }
        size_type wholeDimStep(uint32_t dim) const { return _accumulatedSize[dim] * dimSize(dim); }
        double cell() const { return cell(index()); }
        double cell(size_t cellIdx) const { return _cells[cellIdx]; }
        size_t index() const {
            size_t cellIdx(0);
            for (uint32_t i(0); i < _address.size(); i++) {
                cellIdx += _address[i]*_accumulatedSize[i];
            }
            return cellIdx;
        }

        const eval::ValueType &_type;
        CellsRef               _cells;
        Address                _address;
        std::vector<size_t>    _accumulatedSize;
    };

    AddressContext         _rightAddress;
    Address                _combinedAddress;

    Mapping                _left;
    Mapping                _commonRight;
    Mapping                _right;
    void update(const Address & addr, const Mapping & mapping) {
        for (const auto & m : mapping) {
            _combinedAddress[m.first] = addr[m.second];
        }
    }

public:
    DenseTensorAddressCombiner(const eval::ValueType &lhs, const eval::ValueType &rhs, CellsRef rhsCells);
    ~DenseTensorAddressCombiner();
    void updateLeftAndCommon(const Address & addr) { update(addr, _left); }

    bool hasAnyRightOnlyDimensions() const { return ! _right.empty(); }

    const Address &address() const { return _combinedAddress; }

    bool updateCommonRight() {
        for (const auto & m : _commonRight) {
            if (_combinedAddress[m.first] >= _rightAddress._type.dimensions()[m.second].size) {
                return false;
            }
            _rightAddress._address[m.second] = _combinedAddress[m.first];
        }
        return true;
    }
    double rightCell() { return _rightAddress.cell(); }

    template <typename Func>
    void for_each(Func && func) {
        const int32_t lastDimension = _right.size() - 1;
        int32_t curDimension = lastDimension;
        size_t rightCellIdx = _rightAddress.index();
        while (curDimension >= 0) {
            const uint32_t rdim = _right[curDimension].second;
            const uint32_t cdim = _right[curDimension].first;
            size_type & cindex = _combinedAddress[cdim];
            if (curDimension == lastDimension) {
                for (cindex = 0; cindex < _rightAddress.dimSize(rdim); cindex++) {
                    func(_combinedAddress, _rightAddress.cell(rightCellIdx));
                    rightCellIdx += _rightAddress._accumulatedSize[rdim];
                }
                cindex = 0;
                rightCellIdx -= _rightAddress.wholeDimStep(rdim);
                curDimension--;
            } else {
                if (cindex < _rightAddress.dimSize(rdim)) {
                    cindex++;
                    rightCellIdx += _rightAddress._accumulatedSize[rdim];
                    curDimension++;
                } else {
                    rightCellIdx -= _rightAddress.wholeDimStep(rdim);
                    cindex = 0;
                    curDimension--;
                }
            }
        }
    }

    static eval::ValueType combineDimensions(const eval::ValueType &lhs, const eval::ValueType &rhs);
};


}
