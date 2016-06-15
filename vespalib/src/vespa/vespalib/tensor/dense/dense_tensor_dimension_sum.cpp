// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "dense_tensor_dimension_sum.h"

namespace vespalib {
namespace tensor {

using DimensionsMeta = DenseTensor::DimensionsMeta;

namespace {

DimensionsMeta
removeDimension(const DimensionsMeta &dimensionsMeta,
                const string &dimension)
{
    DimensionsMeta result = dimensionsMeta;
    auto itr = std::lower_bound(result.begin(), result.end(), dimension,
            [](const auto &dimMeta, const auto &dimension_in)
            { return dimMeta.dimension() < dimension_in; });
    if ((itr != result.end()) && (itr->dimension() == dimension)) {
        result.erase(itr);
    }
    return result;
}

size_t
calcCellsSize(const DimensionsMeta &dimensionsMeta)
{
    size_t cellsSize = 1;
    for (const auto &dimMeta : dimensionsMeta) {
        cellsSize *= dimMeta.size();
    }
    return cellsSize;
}

struct DimensionSummer
{
    size_t _innerDimSize;
    size_t _sumDimSize;
    size_t _outerDimSize;
    using Cells = DenseTensor::Cells;

    DimensionSummer(const DimensionsMeta &dimensionsMeta,
                    const string &dimension)
        : _innerDimSize(1),
          _sumDimSize(1),
          _outerDimSize(1)
    {
        auto itr = std::lower_bound(dimensionsMeta.cbegin(), dimensionsMeta.cend(), dimension,
                [](const auto &dimMeta, const auto &dimension_in)
                { return dimMeta.dimension() < dimension_in; });
        if ((itr != dimensionsMeta.end()) && (itr->dimension() == dimension)) {
            for (auto outerItr = dimensionsMeta.cbegin(); outerItr != itr; ++outerItr) {
                _outerDimSize *= outerItr->size();
            }
            _sumDimSize = itr->size();
            for (++itr; itr != dimensionsMeta.cend(); ++itr) {
                _innerDimSize *= itr->size();
            }
        } else {
            _outerDimSize = calcCellsSize(dimensionsMeta);
        }
    }

    void
    sumCells(Cells &cells, const Cells &cells_in) const
    {
        auto itr_in = cells_in.cbegin();
        auto itr = cells.begin();
        for (size_t outerDim = 0; outerDim < _outerDimSize;
             ++outerDim) {
            auto saved_itr = itr;
            for (size_t sumDim = 0; sumDim < _sumDimSize; ++sumDim) {
                itr = saved_itr;
                for (size_t innerDim = 0; innerDim < _innerDimSize;
                     ++innerDim) {
                    *itr += *itr_in;
                    ++itr;
                    ++itr_in;
                }
            }
        }
        assert(itr == cells.end());
        assert(itr_in == cells_in.cend());
    }
};


}


DenseTensorDimensionSum::DenseTensorDimensionSum(const TensorImplType &tensor,
                                                 const string &dimension)
    : _dimensionsMeta(removeDimension(tensor.dimensionsMeta(),
                                      dimension)),
      _cells(calcCellsSize(_dimensionsMeta))
{
    DimensionSummer dimensionSummer(tensor.dimensionsMeta(),
                                    dimension);
    dimensionSummer.sumCells(_cells, tensor.cells());
}


} // namespace vespalib::tensor
} // namespace vespalib
