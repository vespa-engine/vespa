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
    DenseTensorAddressCombiner(const eval::ValueType &lhs, const eval::ValueType &rhs);
    ~DenseTensorAddressCombiner();
    void updateLeftAndCommon(const Address & addr) { update(addr, _left); }
    const Mapping & getCommonRight() const { return _commonRight; }
    const Mapping & getRight() const { return _right; }

    bool hasAnyRightOnlyDimensions() const { return ! _right.empty(); }

    const Address &address() const { return _combinedAddress; }
    Address &address() { return _combinedAddress; }

    static eval::ValueType combineDimensions(const eval::ValueType &lhs, const eval::ValueType &rhs);
};


/**
 * Utility class to iterate over common cells in a dense tensor.
 */
class CommonDenseTensorCellsIterator
{
public:
    using size_type = eval::ValueType::Dimension::size_type;
    using Address = std::vector<size_type>;
    using Mapping = DenseTensorAddressCombiner::Mapping;
private:
    using Dims = std::vector<uint32_t>;
    using CellsRef = vespalib::ConstArrayRef<double>;
    const eval::ValueType &_type;
    CellsRef               _cells;
    Address                _address;
    const Mapping         &_common;
    const Mapping         &_right;
    std::vector<size_t>    _accumulatedSize;

    double cell(size_t cellIdx) const { return _cells[cellIdx]; }
    size_t index(const Address &address) const {
        size_t cellIdx(0);
        for (uint32_t i(0); i < address.size(); i++) {
            cellIdx += address[i]*_accumulatedSize[i];
        }
        return cellIdx;
    }
public:
    CommonDenseTensorCellsIterator(const Mapping & common, const Mapping & right,
                                   const eval::ValueType &type_in, CellsRef cells);
    ~CommonDenseTensorCellsIterator();
    template <typename Func>
    void for_each(Address & combined, Func && func) const {
        const int32_t lastDimension = _right.size() - 1;
        int32_t curDimension = lastDimension;
        size_t cellIdx = index(_address);
        while (curDimension >= 0) {
            const uint32_t rdim = _right[curDimension].second;
            const uint32_t cdim = _right[curDimension].first;
            size_type & cindex = combined[cdim];
            if (curDimension == lastDimension) {
                for (cindex = 0; cindex < _type.dimensions()[rdim].size; cindex++) {
                    func(combined, cell(cellIdx));
                    cellIdx += _accumulatedSize[rdim];
                }
                cindex = 0;
                cellIdx -= _accumulatedSize[rdim] * _type.dimensions()[rdim].size;
                curDimension--;
            } else {
                if (cindex < _type.dimensions()[rdim].size) {
                    cindex++;
                    cellIdx += _accumulatedSize[rdim];
                    curDimension++;
                } else {
                    cellIdx -= _accumulatedSize[rdim] * _type.dimensions()[rdim].size;
                    cindex = 0;
                    curDimension--;
                }
            }
        }
    }
    bool updateCommon(const Address & combined) {
        for (const auto & m : _common) {
            if (combined[m.first] >= _type.dimensions()[m.second].size) {
                return false;
            }
            _address[m.second] = combined[m.first];
        }
        return true;
    }
    double cell() const {
        return cell(index(_address));
    }

    const eval::ValueType &fast_type() const { return _type; }
};

}
