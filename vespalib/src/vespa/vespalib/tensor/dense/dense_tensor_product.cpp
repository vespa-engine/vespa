// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "dense_tensor_product.h"
#include <vespa/vespalib/util/exceptions.h>

namespace vespalib {
namespace tensor {

using DimensionsMeta = DenseTensor::DimensionsMeta;
using CellsIterator = DenseTensor::CellsIterator;
using Address = std::vector<size_t>;

using vespalib::IllegalArgumentException;
using vespalib::make_string;

namespace {

enum class AddressCombineOp
{
    LHS,
    RHS,
    BOTH
};

using AddressCombineOps = std::vector<AddressCombineOp>;

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

class CellsInserter
{
private:
    const DimensionsMeta &_dimensionsMeta;
    DenseTensor::Cells &_cells;

    size_t calculateCellAddress(const Address &address) {
        assert(address.size() == _dimensionsMeta.size());
        size_t result = 0;
        for (size_t i = 0; i < address.size(); ++i) {
            result *= _dimensionsMeta[i].size();
            result += address[i];
        }
        return result;
    }

public:
    CellsInserter(const DimensionsMeta &dimensionsMeta,
                  DenseTensor::Cells &cells)
        : _dimensionsMeta(dimensionsMeta),
          _cells(cells)
    {}
    void insertCell(const Address &address, double cellValue) {
        size_t cellAddress = calculateCellAddress(address);
        assert(cellAddress < _cells.size());
        _cells[cellAddress] = cellValue;
    }
};

void
validateDimensionsMeta(const DimensionsMeta &dimensionsMeta)
{
    for (size_t i = 1; i < dimensionsMeta.size(); ++i) {
        const auto &prevDimMeta = dimensionsMeta[i-1];
        const auto &currDimMeta = dimensionsMeta[i];
        if ((prevDimMeta.dimension() == currDimMeta.dimension()) &&
                (prevDimMeta.size() != currDimMeta.size())) {
            throw IllegalArgumentException(make_string(
                    "Shared dimension '%s' in dense tensor product has mis-matching label ranges: "
                    "[0, %zu> vs [0, %zu>. This is not supported.",
                    prevDimMeta.dimension().c_str(), prevDimMeta.size(), currDimMeta.size()));
        }
    }
}

DimensionsMeta
combineDimensions(const DimensionsMeta &lhs, const DimensionsMeta &rhs)
{
    DimensionsMeta result;
    std::set_union(lhs.cbegin(), lhs.cend(),
                   rhs.cbegin(), rhs.cend(),
                   std::back_inserter(result));
    validateDimensionsMeta(result);
    return result;
}

size_t
calculateCellsSize(const DimensionsMeta &dimensionsMeta)
{
    size_t cellsSize = 1;
    for (const auto &dimMeta : dimensionsMeta) {
        cellsSize *= dimMeta.size();
    }
    return cellsSize;
}

AddressCombineOps
buildCombineOps(const DimensionsMeta &lhs,
                const DimensionsMeta &rhs)
{
    AddressCombineOps ops;
    auto rhsItr = rhs.cbegin();
    auto rhsItrEnd = rhs.cend();
    for (const auto &lhsDim : lhs) {
        while ((rhsItr != rhsItrEnd) && (rhsItr->dimension() < lhsDim.dimension())) {
            ops.push_back(AddressCombineOp::RHS);
            ++rhsItr;
        }
        if ((rhsItr != rhsItrEnd) && (rhsItr->dimension() == lhsDim.dimension())) {
            ops.push_back(AddressCombineOp::BOTH);
            ++rhsItr;
        } else {
            ops.push_back(AddressCombineOp::LHS);
        }
    }
    while (rhsItr != rhsItrEnd) {
        ops.push_back(AddressCombineOp::RHS);
        ++rhsItr;
    }
    return ops;
}

bool
combineAddress(Address &combinedAddress,
               const CellsIterator &lhsItr,
               const CellsIterator &rhsItr,
               const AddressCombineOps &ops)
{
    combinedAddress.clear();
    AddressReader lhsReader(lhsItr.address());
    AddressReader rhsReader(rhsItr.address());
    for (const auto &op : ops) {
        switch (op) {
        case AddressCombineOp::LHS:
            combinedAddress.emplace_back(lhsReader.nextLabel());
            break;
        case AddressCombineOp::RHS:
            combinedAddress.emplace_back(rhsReader.nextLabel());
            break;
        case AddressCombineOp::BOTH:
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

}

void
DenseTensorProduct::bruteForceProduct(const DenseTensor &lhs,
                                      const DenseTensor &rhs)
{
    AddressCombineOps ops = buildCombineOps(lhs.dimensionsMeta(), rhs.dimensionsMeta());
    Address combinedAddress;
    CellsInserter cellsInserter(_dimensionsMeta, _cells);
    for (CellsIterator lhsItr = lhs.cellsIterator(); lhsItr.valid(); lhsItr.next()) {
        for (CellsIterator rhsItr = rhs.cellsIterator(); rhsItr.valid(); rhsItr.next()) {
            bool combineSuccess = combineAddress(combinedAddress, lhsItr, rhsItr, ops);
            if (combineSuccess) {
                cellsInserter.insertCell(combinedAddress, lhsItr.cell() * rhsItr.cell());
            }
        }
    }
}

DenseTensorProduct::DenseTensorProduct(const DenseTensor &lhs,
                                       const DenseTensor &rhs)
    : _dimensionsMeta(combineDimensions(lhs.dimensionsMeta(), rhs.dimensionsMeta())),
      _cells(calculateCellsSize(_dimensionsMeta))
{
    bruteForceProduct(lhs, rhs);
}

Tensor::UP
DenseTensorProduct::result()
{
    return std::make_unique<DenseTensor>(std::move(_dimensionsMeta), std::move(_cells));
}

} // namespace vespalib::tensor
} // namespace vespalib
