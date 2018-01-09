// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_reduce.h"
#include <cassert>
#include <algorithm>

namespace vespalib::tensor::dense {

using Cells = DenseTensorView::Cells;
using CellsRef = DenseTensorView::CellsRef;

class DimensionReducer
{
private:
    eval::ValueType _type;
    Cells _cellsResult;
    size_t _innerDimSize;
    size_t _sumDimSize;
    size_t _outerDimSize;

    void setup(const eval::ValueType &oldType, const vespalib::string &dimensionToRemove);

public:
    DimensionReducer(const eval::ValueType &oldType, const string &dimensionToRemove);
    ~DimensionReducer();

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

namespace {

template <typename Function>
DenseTensor::UP
reduce(const DenseTensorView &tensor, const vespalib::string &dimensionToRemove, Function &&func)
{
    DimensionReducer reducer(tensor.fast_type(), dimensionToRemove);
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
            DenseTensor::UP tmpResult = reduce(DenseTensorView(*result), dimensions[i], func);
            result = std::move(tmpResult);
        }
        return result;
    } else {
        return std::unique_ptr<Tensor>();
    }
}

}
