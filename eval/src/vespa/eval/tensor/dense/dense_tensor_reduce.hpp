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
        std::vector<T> cellsOut(calcCellsSize(_type));
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
};

template <typename Function>
std::unique_ptr<DenseTensorView>
reduce(const DenseTensorView &tensor, const vespalib::string &dimensionToRemove, Function &&func)
{
    DimensionReducer reducer(tensor.fast_type(), dimensionToRemove);
    TypedCells oldCells = tensor.cellsRef();
    return dispatch_1<CallReduceCells>(oldCells, reducer, func);
}

}

template <typename Function>
std::unique_ptr<Tensor>
reduce(const DenseTensorView &tensor, const std::vector<vespalib::string> &dimensions, Function &&func)
{
    if (dimensions.size() == 1) {
        return reduce(tensor, dimensions[0], func);
    } else if (dimensions.size() > 0) {
        std::unique_ptr<DenseTensorView> result = reduce(tensor, dimensions[0], func);
        for (size_t i = 1; i < dimensions.size(); ++i) {
            std::unique_ptr<DenseTensorView> tmpResult = reduce(*result, dimensions[i], func);
            result = std::move(tmpResult);
        }
        return result;
    } else {
        return std::unique_ptr<Tensor>();
    }
}

}
