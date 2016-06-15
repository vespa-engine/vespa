// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "dense_tensor_builder.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <memory>
#include <string>

using vespalib::IllegalArgumentException;
using vespalib::make_string;

namespace vespalib {
namespace tensor {

using DimensionMeta = DenseTensor::DimensionMeta;

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

}

void
DenseTensorBuilder::allocateCellsStorage()
{
    size_t cellsSize = 1;
    for (const auto &dimensionMeta : _dimensionsMeta) {
        cellsSize *= dimensionMeta.size();
    }
    _cells.resize(cellsSize, 0);
}


void
DenseTensorBuilder::sortDimensions()
{
    std::sort(_dimensionsMeta.begin(), _dimensionsMeta.end(),
              [](const DimensionMeta &lhs, const DimensionMeta &rhs)
              { return lhs.dimension() < rhs.dimension(); });
    _dimensionsMapping.resize(_dimensionsMeta.size());
    Dimension dim = 0;
    for (const auto &dimension : _dimensionsMeta) {
        auto itr = _dimensionsEnum.find(dimension.dimension());
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
        const auto &dimMeta = _dimensionsMeta[i];
        if (label == UNDEFINED_LABEL) {
            throw IllegalArgumentException(make_string("Label for dimension '%s' is undefined. "
                    "Expected a value in the range [0, %zu>",
                    dimMeta.dimension().c_str(), dimMeta.size()));
        }
        result += (label * multiplier);
        multiplier *= dimMeta.size();
        _addressBuilder[i] = UNDEFINED_LABEL;
    }
    return result;
}

DenseTensorBuilder::DenseTensorBuilder()
    : _dimensionsEnum(),
      _dimensionsMeta(),
      _cells(),
      _addressBuilder(),
      _dimensionsMapping()
{
}

DenseTensorBuilder::Dimension
DenseTensorBuilder::defineDimension(const vespalib::string &dimension,
                                    size_t dimensionSize)
{
    auto itr = _dimensionsEnum.find(dimension);
    if (itr != _dimensionsEnum.end()) {
        return itr->second;
    }
    assert(_cells.empty());
    Dimension result = _dimensionsEnum.size();
    _dimensionsEnum.insert(std::make_pair(dimension, result));
    _dimensionsMeta.emplace_back(dimension, dimensionSize);
    _addressBuilder.push_back(UNDEFINED_LABEL);
    assert(_dimensionsMeta.size() == (result + 1));
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
    assert(dimension < _dimensionsMeta.size());
    assert(dimension < _addressBuilder.size());
    Dimension mappedDimension = _dimensionsMapping[dimension];
    const auto &dimMeta = _dimensionsMeta[mappedDimension];
    validateLabelInRange(label, dimMeta.size(), dimMeta.dimension());
    validateLabelNotSpecified(_addressBuilder[mappedDimension],
                              dimMeta.dimension());
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
    Tensor::UP result = std::make_unique<DenseTensor>(std::move(_dimensionsMeta),
            std::move(_cells));
    _dimensionsEnum.clear();
    _dimensionsMeta.clear();
    DenseTensor::Cells().swap(_cells);
    _addressBuilder.clear();
    _dimensionsMapping.clear();
    return result;
}

} // namespace vespalib::tensor
} // namespace vespalib
