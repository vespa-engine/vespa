// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_reduce.h"
#include <cassert>
#include <algorithm>

namespace vespalib::tensor::dense {

class DimensionReducer
{
private:
    eval::ValueType _type;
    size_t _innerDimSize;
    size_t _sumDimSize;
    size_t _outerDimSize;

    static size_t calcCellsSize(const eval::ValueType &type);
    void setup(const eval::ValueType &oldType, const vespalib::string &dimensionToRemove);
public:
    DimensionReducer(const eval::ValueType &oldType, const string &dimensionToRemove);
    ~DimensionReducer();

    template <typename T, typename Function>
    std::unique_ptr<DenseTensorView>
    reduceCells(ConstArrayRef<T> cellsIn, Function &&func) {
        size_t resultSize = calcCellsSize(_type);
        std::vector<T> cellsOut(resultSize);
        auto itr_in = cellsIn.cbegin();
        auto itr_out = cellsOut.begin();
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
        assert(itr_out == cellsOut.end());
        assert(itr_in == cellsIn.cend());
        return std::make_unique<DenseTensor<T>>(std::move(_type), std::move(cellsOut));
    }
};

namespace {

struct CallReduceCells {
    template <typename CT, typename Function>
    static std::unique_ptr<DenseTensorView>
    call(const ConstArrayRef<CT> &oldCells, DimensionReducer &reducer, Function &&func) {
        return reducer.reduceCells(oldCells, func);
    }

    template <typename CT, typename Function>
    static double
    call(const ConstArrayRef<CT> &oldCells, Function &&func) {
        assert(oldCells.size() > 0);
        double result = oldCells[0];
        for (size_t i = 1; i < oldCells.size(); ++i) {
            result = func(result, oldCells[i]);
        }
        return result;
    }
};

template <typename Function>
std::unique_ptr<DenseTensorView>
reduce(const DenseTensorView &tensor, const vespalib::string &dimensionToRemove, Function &&func)
{
    DimensionReducer reducer(tensor.fast_type(), dimensionToRemove);
    TypedCells oldCells = tensor.cells();
    return dispatch_1<CallReduceCells>(oldCells, reducer, func);
}

template <typename Function>
double
reduce_all_dimensions(TypedCells oldCells, Function &&func)
{
    return dispatch_1<CallReduceCells>(oldCells, func);
}

}

template <typename Function>
std::unique_ptr<Tensor>
reduce(const DenseTensorView &tensor, const std::vector<vespalib::string> &dimensions, Function &&func)
{
    if ((dimensions.size() == 0) ||
        (dimensions.size() == tensor.fast_type().dimensions().size()))
    {
        eval::ValueType newType = tensor.fast_type().reduce(dimensions);
        assert(newType.is_double());
        double result = reduce_all_dimensions(tensor.cells(), func);
        std::vector<double> newCells({result});
        return std::make_unique<DenseTensor<double>>(std::move(newType), std::move(newCells));
    }
    std::unique_ptr<DenseTensorView> result = reduce(tensor, dimensions[0], func);
    for (size_t i = 1; i < dimensions.size(); ++i) {
        std::unique_ptr<DenseTensorView> tmpResult = reduce(*result, dimensions[i], func);
        result = std::move(tmpResult);
    }
    return result;
}

}
