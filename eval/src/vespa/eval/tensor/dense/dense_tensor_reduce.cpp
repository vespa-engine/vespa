// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_reduce.hpp"

namespace vespalib::tensor::dense {

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

}
DimensionReducer::DimensionReducer(const eval::ValueType &oldType,
                 const string &dimensionToRemove)
        : _type(oldType.reduce({ dimensionToRemove })),
          _cellsResult(calcCellsSize(_type)),
          _innerDimSize(1),
          _sumDimSize(1),
          _outerDimSize(1)
{
    setup(oldType, dimensionToRemove);
}

DimensionReducer::~DimensionReducer() = default;

void
DimensionReducer::setup(const eval::ValueType &oldType, const vespalib::string &dimensionToRemove)
{
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

}

