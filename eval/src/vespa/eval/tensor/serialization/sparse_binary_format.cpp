// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_binary_format.h"
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/tensor/types.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/sparse/direct_sparse_tensor_builder.h>
#include <vespa/eval/tensor/sparse/sparse_tensor_address_builder.h>
#include <vespa/eval/tensor/tensor_visitor.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <sstream>
#include <cassert>

using vespalib::nbostream;
using vespalib::eval::CellType;
using vespalib::eval::ValueType;

namespace vespalib::tensor {

namespace {

vespalib::string undefinedLabel("");

void writeTensorAddress(nbostream &output,
                        const eval::ValueType &type,
                        const TensorAddress &value)
{
    auto elemItr = value.elements().cbegin();
    auto elemItrEnd = value.elements().cend();
    for (const auto &dimension : type.dimensions()) {
        if (elemItr != elemItrEnd && dimension.name == elemItr->dimension()) {
            output.writeSmallString(elemItr->label());
            ++elemItr;
        } else {
            output.writeSmallString(undefinedLabel);
        }
    }
    assert(elemItr == elemItrEnd);
}

template <typename T>
class SparseBinaryFormatSerializer : public TensorVisitor
{
private:
    uint32_t _num_cells;
    nbostream &_cells;
    const ValueType &_type;
public:
    SparseBinaryFormatSerializer(nbostream &cells, const ValueType &type);
    size_t num_cells() const { return _num_cells; }
    virtual ~SparseBinaryFormatSerializer() override;
    virtual void visit(const TensorAddress &address, double value) override;
};

template <typename T>
SparseBinaryFormatSerializer<T>::SparseBinaryFormatSerializer(nbostream &cells, const ValueType &type)
    : _num_cells(0),
      _cells(cells),
      _type(type)
{
}

template <typename T>
SparseBinaryFormatSerializer<T>::~SparseBinaryFormatSerializer() = default;

template <typename T>
void
SparseBinaryFormatSerializer<T>::visit(const TensorAddress &address, double value)
{
    ++_num_cells;
    writeTensorAddress(_cells, _type, address);
    _cells << static_cast<T>(value);
}

void encodeDimensions(nbostream &stream, const eval::ValueType &type) {
    stream.putInt1_4Bytes(type.dimensions().size());
    for (const auto &dimension : type.dimensions()) {
        stream.writeSmallString(dimension.name);
    }
}

template <typename T>
size_t encodeCells(nbostream &stream, const Tensor &tensor) {
    SparseBinaryFormatSerializer<T> serializer(stream, tensor.type());
    tensor.accept(serializer);
    return serializer.num_cells();
}

size_t encodeCells(nbostream &stream, const Tensor &tensor, CellType cell_type) {
    switch (cell_type) {
    case CellType::DOUBLE:
        return encodeCells<double>(stream, tensor);
        break;
    case CellType::FLOAT:
        return encodeCells<float>(stream, tensor);
        break;
    }
    return 0;
}

template<typename T>
void decodeCells(nbostream &stream, size_t dimensionsSize, size_t cellsSize, DirectSparseTensorBuilder<T> &builder) {
    T cellValue = 0.0;
    vespalib::string str;
    SparseTensorAddressBuilder address;
    for (size_t cellIdx = 0; cellIdx < cellsSize; ++cellIdx) {
        address.clear();
        for (size_t dimension = 0; dimension < dimensionsSize; ++dimension) {
            stream.readSmallString(str);
            if (!str.empty()) {
                address.add(str);
            } else {
                address.addUndefined();
            }
        }
        stream >> cellValue;
        builder.insertCell(address, cellValue, [](double, double v){ return v; });
    }
}

}

void
SparseBinaryFormat::serialize(nbostream &stream, const Tensor &tensor)
{
    const auto &type = tensor.type();
    encodeDimensions(stream, type);
    nbostream cells;
    size_t numCells = encodeCells(cells, tensor, type.cell_type());
    stream.putInt1_4Bytes(numCells);
    stream.write(cells.peek(), cells.size());
}

struct BuildSparseCells {
    template<typename CT>
    static Tensor::UP invoke(ValueType type, nbostream &stream,
                             size_t dimensionsSize, 
                             size_t cellsSize)
    {
        DirectSparseTensorBuilder<CT> builder(std::move(type));
        builder.reserve(cellsSize);
        decodeCells<CT>(stream, dimensionsSize, cellsSize, builder);
        auto retval = builder.build();
        if (retval->should_shrink()) {
            return retval->shrink();
        } else {
            return retval;
        }
    }
};

std::unique_ptr<Tensor>
SparseBinaryFormat::deserialize(nbostream &stream, CellType cell_type)
{
    vespalib::string str;
    size_t dimensionsSize = stream.getInt1_4Bytes();
    std::vector<ValueType::Dimension> dimensions;
    while (dimensions.size() < dimensionsSize) {
        stream.readSmallString(str);
        dimensions.emplace_back(str);
    }
    size_t cellsSize = stream.getInt1_4Bytes();
    ValueType type = ValueType::tensor_type(std::move(dimensions), cell_type);
    return typify_invoke<1,eval::TypifyCellType,BuildSparseCells>(cell_type,
        std::move(type), stream, dimensionsSize, cellsSize);
}

} // namespace
