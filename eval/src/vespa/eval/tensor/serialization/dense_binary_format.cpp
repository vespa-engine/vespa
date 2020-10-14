// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_binary_format.h"
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <cassert>

using vespalib::nbostream;
using vespalib::eval::ValueType;
using CellType = vespalib::eval::ValueType::CellType;

namespace vespalib::tensor {

using Dimension = eval::ValueType::Dimension;


namespace {

size_t encodeDimensions(nbostream &stream, const eval::ValueType & type) {
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
void encodeCells(nbostream &stream, TypedCells cells) {
    auto arr = cells.typify<T>();
    for (const auto &value : arr) {
        stream << value;
    }
}

size_t decodeDimensions(nbostream & stream, std::vector<Dimension> & dimensions) {
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
void decodeCells(nbostream &stream, size_t cellsSize, V &cells) {
    T cellValue = 0.0;
    for (size_t i = 0; i < cellsSize; ++i) {
        stream >> cellValue;
        cells.emplace_back(cellValue);
    }
}

template <typename V>
void decodeCells(CellType cell_type, nbostream &stream, size_t cellsSize, V &cells) {
    switch (cell_type) {
    case CellType::DOUBLE:
        decodeCells<double>(stream, cellsSize, cells);
        break;
    case CellType::FLOAT:
        decodeCells<float>(stream, cellsSize, cells);
        break;
    }
}

}

void
DenseBinaryFormat::serialize(nbostream &stream, const DenseTensorView &tensor)
{
    size_t cellsSize = encodeDimensions(stream, tensor.fast_type());
    TypedCells cells = tensor.cells();
    assert(cells.size == cellsSize);
    switch (tensor.fast_type().cell_type()) {
    case CellType::DOUBLE:
        encodeCells<double>(stream, cells);
        break;
    case CellType::FLOAT:
        encodeCells<float>(stream, cells);
        break;
    }
}

struct CallDecodeCells {
    template <typename CT>
    static std::unique_ptr<DenseTensorView>
    invoke(nbostream &stream, size_t numCells, ValueType &&newType) {
        std::vector<CT> newCells;
        newCells.reserve(numCells);
        decodeCells<CT>(stream, numCells, newCells);
        return std::make_unique<DenseTensor<CT>>(std::move(newType), std::move(newCells));
    }
};

std::unique_ptr<DenseTensorView>
DenseBinaryFormat::deserialize(nbostream &stream, CellType cell_type)
{
    std::vector<Dimension> dimensions;
    size_t numCells = decodeDimensions(stream, dimensions);
    ValueType newType = ValueType::tensor_type(std::move(dimensions), cell_type);
    using MyTypify = eval::TypifyCellType;
    return typify_invoke<1,MyTypify,CallDecodeCells>(cell_type, stream, numCells, std::move(newType));
}

template <typename T>
void
DenseBinaryFormat::deserializeCellsOnly(nbostream &stream, std::vector<T> &cells, CellType cell_type)
{
    std::vector<Dimension> dimensions;
    size_t cellsSize = decodeDimensions(stream, dimensions);
    cells.clear();
    cells.reserve(cellsSize);
    decodeCells(cell_type, stream, cellsSize, cells);
}

template void DenseBinaryFormat::deserializeCellsOnly(nbostream &stream, std::vector<double> &cells, CellType cell_type);
template void DenseBinaryFormat::deserializeCellsOnly(nbostream &stream, std::vector<float> &cells, CellType cell_type);

}
