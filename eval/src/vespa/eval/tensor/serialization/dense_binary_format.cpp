// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_binary_format.h"
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <cassert>

using vespalib::nbostream;

namespace vespalib::tensor {

using CellType = eval::ValueType::CellType;
using Dimension = eval::ValueType::Dimension;


namespace {

using EncodeType = DenseBinaryFormat::EncodeType;

constexpr int DOUBLE_VALUE_TYPE = 0;
constexpr int FLOAT_VALUE_TYPE = 1;

eval::ValueType
makeValueType(CellType cellType, std::vector<Dimension> &&dimensions) {
    return (dimensions.empty() ?
            eval::ValueType::double_type() :
            eval::ValueType::tensor_type(cellType, std::move(dimensions)));
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

void
encodeValueType(nbostream & stream, CellType valueType, EncodeType encodeType) {
    switch (valueType) {
        case CellType::DOUBLE:
            if (encodeType != EncodeType::DOUBLE_IS_DEFAULT) {
                stream.putInt1_4Bytes(DOUBLE_VALUE_TYPE);
            }
            break;
        case CellType::FLOAT:
            stream.putInt1_4Bytes(FLOAT_VALUE_TYPE);
            break;
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

CellType
decodeCellType(nbostream & stream, EncodeType encodeType) {
    if (encodeType != EncodeType::DOUBLE_IS_DEFAULT) {
        uint32_t serializedType = stream.getInt1_4Bytes();
        switch (serializedType) {
            case DOUBLE_VALUE_TYPE:
                return CellType::DOUBLE;
            case FLOAT_VALUE_TYPE:
                return  CellType::FLOAT;
            default:
                throw IllegalArgumentException(make_string("Received unknown tensor value type = %u. Only 0(double), or 1(float) are legal.", serializedType));
        }
    } else {
        return CellType::DOUBLE;
    }
}

template<typename T>
void
decodeCells(nbostream &stream, size_t cellsSize, DenseTensor::Cells & cells) {
    T cellValue = 0.0;
    for (size_t i = 0; i < cellsSize; ++i) {
        stream >> cellValue;
        cells.emplace_back(cellValue);
    }
}

}

void
DenseBinaryFormat::serialize(nbostream &stream, const DenseTensorView &tensor)
{
    const eval::ValueType & type = tensor.fast_type();
    encodeValueType(stream, type.cell_type(), _encodeType);
    size_t cellsSize = encodeDimensions(stream, type);

    DenseTensorView::CellsRef cells = tensor.cellsRef();
    assert(cells.size() == cellsSize);
    switch (type.cell_type()) {
        case CellType::DOUBLE:
            encodeCells<double>(stream, cells);
            break;
        case CellType::FLOAT:
            encodeCells<float>(stream, cells);
            break;
    }
}

std::unique_ptr<DenseTensor>
DenseBinaryFormat::deserialize(nbostream &stream)
{
    CellType cellType = decodeCellType(stream, _encodeType);
    std::vector<Dimension> dimensions;
    size_t cellsSize = decodeDimensions(stream,dimensions);
    DenseTensor::Cells cells;
    cells.reserve(cellsSize);
    switch (cellType) {
        case CellType::DOUBLE:
            decodeCells<double>(stream, cellsSize,cells);
            break;
        case CellType::FLOAT:
            decodeCells<float>(stream, cellsSize,cells);
            break;
    }

    return std::make_unique<DenseTensor>(makeValueType(cellType, std::move(dimensions)), std::move(cells));
}

}
