// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_builder.h"
#include <vespa/vespalib/util/exceptions.h>
#include <cassert>
#include <limits>
#include <algorithm>


using vespalib::IllegalArgumentException;
using vespalib::make_string;

namespace vespalib::tensor {

namespace {

constexpr size_t UNDEFINED_LABEL = std::numeric_limits<size_t>::max();

void
validateLabelInRange(size_t label, size_t dimensionSize, const vespalib::string &dimension)
{
    if (label >= dimensionSize) {
        throw IllegalArgumentException(make_string(
                "Label '%zu' for dimension '%s' is outside range [0, %zu>",
                label, dimension.c_str(), dimensionSize));
    }
}

void
validateLabelNotSpecified(size_t oldLabel, const vespalib::string &dimension)
{
    if (oldLabel != UNDEFINED_LABEL) {
        throw IllegalArgumentException(make_string(
                "Label for dimension '%s' is already specified with value '%zu'",
                dimension.c_str(), oldLabel));
    }
}

eval::ValueType
makeValueType(eval::ValueType::CellType cellType, std::vector<eval::ValueType::Dimension> &&dimensions) {
    return (dimensions.empty() ?
            eval::ValueType::double_type() :
            eval::ValueType::tensor_type(cellType, std::move(dimensions)));
}

}

void
DenseTensorBuilder::allocateCellsStorage()
{
    size_t cellsSize = 1;
    for (const auto &dimension : _dimensions) {
        cellsSize *= dimension.size;
    }
    _cells.resize(cellsSize, 0);
}


void
DenseTensorBuilder::sortDimensions()
{
    std::sort(_dimensions.begin(), _dimensions.end(),
              [](const eval::ValueType::Dimension &lhs,
                 const eval::ValueType::Dimension &rhs)
              { return lhs.name < rhs.name; });
    _dimensionsMapping.resize(_dimensions.size());
    Dimension dim = 0;
    for (const auto &dimension : _dimensions) {
        auto itr = _dimensionsEnum.find(dimension.name);
        assert(itr != _dimensionsEnum.end());
        _dimensionsMapping[itr->second] = dim;
        ++dim;
    }
}

size_t
DenseTensorBuilder::calculateCellAddress()
{
    size_t result = 0;
    size_t multiplier = 1;
    for (int64_t i = (_addressBuilder.size() - 1); i >= 0; --i) {
        const size_t label = _addressBuilder[i];
        const auto &dim = _dimensions[i];
        if (label == UNDEFINED_LABEL) {
            throw IllegalArgumentException(make_string("Label for dimension '%s' is undefined. "
                    "Expected a value in the range [0, %u>",
                    dim.name.c_str(), dim.size));
        }
        result += (label * multiplier);
        multiplier *= dim.size;
        _addressBuilder[i] = UNDEFINED_LABEL;
    }
    return result;
}

DenseTensorBuilder::DenseTensorBuilder(eval::ValueType::CellType cellType)
    : _cellType(cellType),
      _dimensionsEnum(),
      _dimensions(),
      _cells(),
      _addressBuilder(),
      _dimensionsMapping()
{
}

DenseTensorBuilder::~DenseTensorBuilder() = default;

DenseTensorBuilder::Dimension
DenseTensorBuilder::defineDimension(const vespalib::string &dimension, size_t dimensionSize)
{
    auto itr = _dimensionsEnum.find(dimension);
    if (itr != _dimensionsEnum.end()) {
        return itr->second;
    }
    assert(_cells.empty());
    Dimension result = _dimensionsEnum.size();
    _dimensionsEnum.insert(std::make_pair(dimension, result));
    _dimensions.emplace_back(dimension, dimensionSize);
    _addressBuilder.push_back(UNDEFINED_LABEL);
    assert(_dimensions.size() == (result + 1));
    assert(_addressBuilder.size() == (result + 1));
    return result;
}

DenseTensorBuilder &
DenseTensorBuilder::addLabel(Dimension dimension, size_t label)
{
    if (_cells.empty()) {
        sortDimensions();
        allocateCellsStorage();
    }
    assert(dimension < _dimensions.size());
    assert(dimension < _addressBuilder.size());
    Dimension mappedDimension = _dimensionsMapping[dimension];
    const auto &dim = _dimensions[mappedDimension];
    validateLabelInRange(label, dim.size, dim.name);
    validateLabelNotSpecified(_addressBuilder[mappedDimension], dim.name);
    _addressBuilder[mappedDimension] = label;
    return *this;
}

DenseTensorBuilder &
DenseTensorBuilder::addCell(double value)
{
    if (_cells.empty()) {
        sortDimensions();
        allocateCellsStorage();
    }
    size_t cellAddress = calculateCellAddress();
    assert(cellAddress < _cells.size());
    _cells[cellAddress] = value;
    return *this;
}

Tensor::UP
DenseTensorBuilder::build()
{
    if (_cells.empty()) {
        allocateCellsStorage();
    }
    Tensor::UP result = std::make_unique<DenseTensor>(makeValueType(_cellType, std::move(_dimensions)), std::move(_cells));
    _dimensionsEnum.clear();
    _dimensions.clear();
    DenseTensor::Cells().swap(_cells);
    _addressBuilder.clear();
    _dimensionsMapping.clear();
    return result;
}

}
