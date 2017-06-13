// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_reduce.h"
#include <cassert>

namespace vespalib {
namespace tensor {
namespace dense {

using Cells = DenseTensorView::Cells;
using CellsRef = DenseTensorView::CellsRef;

namespace {

size_t
calcCellsSize(const eval::ValueType &type)
{
    size_t cellsSize = 1;
    for (const auto &dim : type.dimensions()) {
        cellsSize *= dim.size;
    }
    return cellsSize;
}


class DimensionReducer
{
private:
    eval::ValueType _type;
    Cells _cellsResult;
    size_t _innerDimSize;
    size_t _sumDimSize;
    size_t _outerDimSize;

    void setup(const eval::ValueType &oldType,
               const vespalib::string &dimensionToRemove) {
        auto itr = std::lower_bound(oldType.dimensions().cbegin(),
                                    oldType.dimensions().cend(),
                                    dimensionToRemove,
                                    [](const auto &dim, const auto &dimension)
                                    { return dim.name < dimension; });
        if ((itr != oldType.dimensions().end()) && (itr->name == dimensionToRemove)) {
            for (auto outerItr = oldType.dimensions().cbegin(); outerItr != itr; ++outerItr) {
                _outerDimSize *= outerItr->size;
            }
            _sumDimSize = itr->size;
            for (++itr; itr != oldType.dimensions().cend(); ++itr) {
                _innerDimSize *= itr->size;
            }
        } else {
            _outerDimSize = calcCellsSize(oldType);
        }
    }

public:
    DimensionReducer(const eval::ValueType &oldType,
                     const string &dimensionToRemove)
        : _type(oldType.reduce({ dimensionToRemove })),
          _cellsResult(calcCellsSize(_type)),
          _innerDimSize(1),
          _sumDimSize(1),
          _outerDimSize(1)
    {
        setup(oldType, dimensionToRemove);
    }

    template <typename Function>
    DenseTensor::UP
    reduceCells(CellsRef cellsIn, Function &&func) {
        auto itr_in = cellsIn.cbegin();
        auto itr_out = _cellsResult.begin();
        for (size_t outerDim = 0; outerDim < _outerDimSize; ++outerDim) {
            auto saved_itr = itr_out;
            for (size_t innerDim = 0; innerDim < _innerDimSize; ++innerDim) {
                *itr_out = *itr_in;
                ++itr_out;
                ++itr_in;
            }
            for (size_t sumDim = 1; sumDim < _sumDimSize; ++sumDim) {
                itr_out = saved_itr;
                for (size_t innerDim = 0; innerDim < _innerDimSize; ++innerDim) {
                    *itr_out = func(*itr_out, *itr_in);
                    ++itr_out;
                    ++itr_in;
                }
            }
        }
        assert(itr_out == _cellsResult.end());
        assert(itr_in == cellsIn.cend());
        return std::make_unique<DenseTensor>(std::move(_type), std::move(_cellsResult));
    }
};

template <typename Function>
DenseTensor::UP
reduce(const DenseTensorView &tensor, const vespalib::string &dimensionToRemove, Function &&func)
{
    DimensionReducer reducer(tensor.type(), dimensionToRemove);
    return reducer.reduceCells(tensor.cellsRef(), func);
}

}

template <typename Function>
std::unique_ptr<Tensor>
reduce(const DenseTensorView &tensor, const std::vector<vespalib::string> &dimensions, Function &&func)
{
    if (dimensions.size() == 1) {
        return reduce(tensor, dimensions[0], func);
    } else if (dimensions.size() > 0) {
        DenseTensor::UP result = reduce(tensor, dimensions[0], func);
        for (size_t i = 1; i < dimensions.size(); ++i) {
            DenseTensor::UP tmpResult = reduce(DenseTensorView(*result),
                                               dimensions[i], func);
            result = std::move(tmpResult);
        }
        return result;
    } else {
        return std::unique_ptr<Tensor>();
    }
}

} // namespace dense
} // namespace tensor
} // namespace vespalib
