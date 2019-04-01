// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_binary_format.h"
#include <vespa/eval/tensor/types.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/tensor_builder.h>
#include <vespa/eval/tensor/tensor_visitor.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <sstream>
#include <cassert>

using vespalib::nbostream;

namespace vespalib::tensor {

namespace {

vespalib::string undefinedLabel("");

void
writeTensorAddress(nbostream &output,
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

}

class SparseBinaryFormatSerializer : public TensorVisitor
{
    uint32_t _numCells;
    nbostream _cells;
    eval::ValueType _type;

public:
    SparseBinaryFormatSerializer();
    virtual ~SparseBinaryFormatSerializer() override;
    virtual void visit(const TensorAddress &address, double value) override;
    void serialize(nbostream &stream, const Tensor &tensor);
};

SparseBinaryFormatSerializer::SparseBinaryFormatSerializer()
    : _numCells(0u),
      _cells(),
      _type(eval::ValueType::error_type())
{
}


SparseBinaryFormatSerializer::~SparseBinaryFormatSerializer() = default;

void
SparseBinaryFormatSerializer::visit(const TensorAddress &address, double value)
{
    ++_numCells;
    writeTensorAddress(_cells, _type, address);
    _cells << value;
}


void
SparseBinaryFormatSerializer::serialize(nbostream &stream, const Tensor &tensor)
{
    _type = tensor.type();
    tensor.accept(*this);
    stream.putInt1_4Bytes(_type.dimensions().size());
    for (const auto &dimension : _type.dimensions()) {
        stream.writeSmallString(dimension.name);
    }
    stream.putInt1_4Bytes(_numCells);
    stream.write(_cells.peek(), _cells.size());
}


void
SparseBinaryFormat::serialize(nbostream &stream, const Tensor &tensor)
{
    SparseBinaryFormatSerializer serializer;
    serializer.serialize(stream, tensor);
}


void
SparseBinaryFormat::deserialize(nbostream &stream, TensorBuilder &builder)
{
    vespalib::string str;
    size_t dimensionsSize = stream.getInt1_4Bytes();
    std::vector<TensorBuilder::Dimension> dimensions;
    while (dimensions.size() < dimensionsSize) {
        stream.readSmallString(str);
        dimensions.emplace_back(builder.define_dimension(str));
    }
    size_t cellsSize = stream.getInt1_4Bytes();
    double cellValue = 0.0;
    for (size_t cellIdx = 0; cellIdx < cellsSize; ++cellIdx) {
        for (size_t dimension = 0; dimension < dimensionsSize; ++dimension) {
            stream.readSmallString(str);
            if (!str.empty()) {
                builder.add_label(dimensions[dimension], str);
            }
        }
        stream >> cellValue;
        builder.add_cell(cellValue);
    }
}


}
