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
    using Address = DenseTensorCellsIterator::Address;

private:
    enum class AddressOp { LHS, RHS, BOTH };

    using CellsIterator = DenseTensorCellsIterator;

    class AddressReader
    {
    private:
        const Address &_address;
        uint32_t       _idx;
    public:
        AddressReader(const Address &address) : _address(address), _idx(0) {}
        Address::value_type nextLabel() { return _address[_idx++]; }
    };

    std::vector<AddressOp> _ops;
    Address                _combinedAddress;
public:
    DenseTensorAddressCombiner(const eval::ValueType &lhs, const eval::ValueType &rhs);
    ~DenseTensorAddressCombiner();

    const Address &address() const { return _combinedAddress; }

    bool combine(const CellsIterator &lhsItr, const CellsIterator &rhsItr) {
        uint32_t index(0);
        AddressReader lhsReader(lhsItr.address());
        AddressReader rhsReader(rhsItr.address());
        for (const auto &op : _ops) {
            switch (op) {
                case AddressOp::LHS:
                    _combinedAddress[index] = lhsReader.nextLabel();
                    break;
                case AddressOp::RHS:
                    _combinedAddress[index] = rhsReader.nextLabel();
                    break;
                case AddressOp::BOTH:
                    Address::value_type lhsLabel = lhsReader.nextLabel();
                    Address::value_type rhsLabel = rhsReader.nextLabel();
                    if (lhsLabel != rhsLabel) {
                        return false;
                    }
                    _combinedAddress[index] = lhsLabel;
            }
            index++;
        }
        return true;
    }

    static eval::ValueType combineDimensions(const eval::ValueType &lhs, const eval::ValueType &rhs);
};

}
