// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dense_tensor_cells_iterator.h"
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/types.h>
#include <vespa/eval/eval/value_type.h>

namespace vespalib::tensor {

class AddressContext {
public:
    using Address = DenseTensorCellsIterator::Address;
    using size_type = eval::ValueType::Dimension::size_type;
    using Mapping = std::vector<std::pair<uint32_t, uint32_t>>;
    AddressContext(const eval::ValueType &type);
    ~AddressContext();
    size_type dimSize(uint32_t dim) const { return _type.dimensions()[dim].size; }
    size_type wholeDimStep(uint32_t dim) const { return _accumulatedSize[dim] * dimSize(dim); }
    size_t index() const {
        size_t cellIdx(0);
        for (uint32_t i(0); i < _address.size(); i++) {
            cellIdx += _address[i]*_accumulatedSize[i];
        }
        return cellIdx;
    }
    void update(const Address & addr, const Mapping & mapping) {
        for (const auto & m : mapping) {
            _address[m.first] = addr[m.second];
        }
    }
    bool updateCommon(const Address & addr, const Mapping & mapping) {
        for (const auto & m : mapping) {
            if (addr[m.first] >= dimSize(m.second)) {
                return false;
            }
            _address[m.second] = addr[m.first];
        }
        return true;
    }

    const eval::ValueType &_type;
    std::vector<size_t>    _accumulatedSize;
    Address                _address;

};

/**
 * Combines two dense tensor addresses to a new tensor address.
 * The resulting dimensions is the union of the input dimensions and
 * common dimensions must have matching labels.
 */
class DenseTensorAddressCombiner
{
public:
    using Mapping = AddressContext::Mapping;

private:
    using Address = DenseTensorCellsIterator::Address;
    using CellsRef = vespalib::ConstArrayRef<double>;
    using size_type = eval::ValueType::Dimension::size_type;

    AddressContext         _combinedAddress;

    Mapping                _left;
    Mapping                _commonRight;
    Mapping                _right;

public:
    DenseTensorAddressCombiner(const eval::ValueType &combined, const eval::ValueType &lhs, const eval::ValueType &rhs);
    ~DenseTensorAddressCombiner();
    void updateLeftAndCommon(const Address & addr) { _combinedAddress.update(addr, _left); }

    bool hasAnyRightOnlyDimensions() const { return ! _right.empty(); }

    const Address & address() const { return _combinedAddress._address; }
    const Mapping & commonRight() const { return _commonRight; }

    template <typename Func>
    void for_each(const AddressContext & rightAddress, const CellsRef & rhsCells, Func && func) {
        // The rightAddress oly holds the starting point for iteration and what is need to efficiently maintain
        // an index for addressing th ecells.
        const int32_t lastDimension = _right.size() - 1;
        int32_t curDimension = lastDimension;
        size_t rightCellIdx = rightAddress.index();
        size_t combinedCellIdx = _combinedAddress.index();
        while (curDimension >= 0) {
            const uint32_t rdim = _right[curDimension].second;
            const uint32_t cdim = _right[curDimension].first;
            size_type & cindex = _combinedAddress._address[cdim];
            if (curDimension == lastDimension) {
                for (cindex = 0; cindex < rightAddress.dimSize(rdim); cindex++) {
                    func(combinedCellIdx, rhsCells[rightCellIdx]);
                    rightCellIdx += rightAddress._accumulatedSize[rdim];
                    combinedCellIdx += _combinedAddress._accumulatedSize[cdim];
                }
                cindex = 0;
                rightCellIdx -= rightAddress.wholeDimStep(rdim);
                combinedCellIdx -= _combinedAddress.wholeDimStep(cdim);
                curDimension--;
            } else {
                if (cindex < rightAddress.dimSize(rdim)) {
                    cindex++;
                    rightCellIdx += rightAddress._accumulatedSize[rdim];
                    combinedCellIdx += _combinedAddress._accumulatedSize[cdim];
                    curDimension++;
                } else {
                    rightCellIdx -= rightAddress.wholeDimStep(rdim);
                    combinedCellIdx -= _combinedAddress.wholeDimStep(cdim);
                    cindex = 0;
                    curDimension--;
                }
            }
        }
    }

    static eval::ValueType combineDimensions(const eval::ValueType &lhs, const eval::ValueType &rhs);
};


}
