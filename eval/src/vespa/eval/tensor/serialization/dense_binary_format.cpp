// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_binary_format.h"
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <cassert>

using vespalib::nbostream;

namespace vespalib::tensor {

using Dimension = eval::ValueType::Dimension;


namespace {

eval::ValueType
makeValueType(std::vector<Dimension> &&dimensions) {
    return (dimensions.empty() ?
            eval::ValueType::double_type() :
            eval::ValueType::tensor_type(std::move(dimensions)));
}

size_t
encodeDimensions(nbostream &stream, const eval::ValueType & type) {
    stream.putInt1_4Bytes(type.dimensions().size());
    size_t cellsSize = 1;
    for (const auto &dimension : type.dimensions()) {
        stream.writeSmallString(dimension.name);
        stream.putInt1_4Bytes(dimension.size);
        cellsSize *= dimension.size;
    }
    return cellsSize;
}

template<typename T>
void
encodeCells(nbostream &stream, DenseTensorView::CellsRef cells) {
    for (const auto &value : cells) {
        stream << static_cast<T>(value);
    }
}

size_t
decodeDimensions(nbostream & stream, std::vector<Dimension> & dimensions) {
    vespalib::string dimensionName;
    size_t dimensionsSize = stream.getInt1_4Bytes();
    size_t dimensionSize;
    size_t cellsSize = 1;
    while (dimensions.size() < dimensionsSize) {
        stream.readSmallString(dimensionName);
        dimensionSize = stream.getInt1_4Bytes();
        dimensions.emplace_back(dimensionName, dimensionSize);
        cellsSize *= dimensionSize;
    }
    return cellsSize;
}

template<typename T, typename V>
void
decodeCells(nbostream &stream, size_t cellsSize, V & cells) {
    T cellValue = 0.0;
    for (size_t i = 0; i < cellsSize; ++i) {
        stream >> cellValue;
        cells.emplace_back(cellValue);
    }
}

template <typename V>
void decodeCells(SerializeFormat format, nbostream &stream, size_t cellsSize, V & cells)
{
    switch (format) {
    case SerializeFormat::DOUBLE:
        decodeCells<double>(stream, cellsSize, cells);
        break;
    case SerializeFormat::FLOAT:
        decodeCells<float>(stream, cellsSize, cells);
        break;
    }
}

}

void
DenseBinaryFormat::serialize(nbostream &stream, const DenseTensorView &tensor)
{
    size_t cellsSize = encodeDimensions(stream, tensor.fast_type());

    DenseTensorView::CellsRef cells = tensor.cellsRef();
    assert(cells.size() == cellsSize);
    switch (_format) {
        case SerializeFormat::DOUBLE:
            encodeCells<double>(stream, cells);
            break;
        case SerializeFormat::FLOAT:
            encodeCells<float>(stream, cells);
            break;
    }
}

std::unique_ptr<DenseTensor>
DenseBinaryFormat::deserialize(nbostream &stream)
{
    std::vector<Dimension> dimensions;
    size_t cellsSize = decodeDimensions(stream,dimensions);
    DenseTensor::Cells cells;
    cells.reserve(cellsSize);

    decodeCells(_format, stream, cellsSize, cells);

    return std::make_unique<DenseTensor>(makeValueType(std::move(dimensions)), std::move(cells));
}

template <typename T>
void
DenseBinaryFormat::deserializeCellsOnly(nbostream &stream, std::vector<T> & cells)
{
    std::vector<Dimension> dimensions;
    size_t cellsSize = decodeDimensions(stream,dimensions);
    cells.clear();
    cells.reserve(cellsSize);
    decodeCells(_format, stream, cellsSize, cells);
}

template void DenseBinaryFormat::deserializeCellsOnly(nbostream &stream, std::vector<double> & cells);
template void DenseBinaryFormat::deserializeCellsOnly(nbostream &stream, std::vector<float> & cells);

}
