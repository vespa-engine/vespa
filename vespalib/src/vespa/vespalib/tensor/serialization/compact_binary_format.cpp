// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "compact_binary_format.h"
#include <vespa/vespalib/tensor/types.h>
#include <vespa/vespalib/tensor/tensor.h>
#include <vespa/vespalib/tensor/tensor_builder.h>
#include <vespa/vespalib/tensor/tensor_visitor.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <sstream>


using vespalib::nbostream;

namespace vespalib {
namespace tensor {


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

class CompactBinaryFormatSerializer : public TensorVisitor
{
    uint32_t _numCells;
    nbostream _cells;
    eval::ValueType _type;

public:
    CompactBinaryFormatSerializer();
    virtual ~CompactBinaryFormatSerializer() override;
    virtual void visit(const TensorAddress &address, double value) override;
    void serialize(nbostream &stream, const Tensor &tensor);
};

CompactBinaryFormatSerializer::CompactBinaryFormatSerializer()
    : _numCells(0u),
      _cells(),
      _type(eval::ValueType::error_type())
{
}


CompactBinaryFormatSerializer::~CompactBinaryFormatSerializer()
{
}

void
CompactBinaryFormatSerializer::visit(const TensorAddress &address,
                                     double value)
{
    ++_numCells;
    writeTensorAddress(_cells, _type, address);
    _cells << value;
}


void
CompactBinaryFormatSerializer::serialize(nbostream &stream,
                                         const Tensor &tensor)
{
    _type = tensor.getType();
    tensor.accept(*this);
    stream.putInt1_4Bytes(_type.dimensions().size());
    for (const auto &dimension : _type.dimensions()) {
        stream.writeSmallString(dimension.name);
    }
    stream.putInt1_4Bytes(_numCells);
    stream.write(_cells.peek(), _cells.size());
}


void
CompactBinaryFormat::serialize(nbostream &stream, const Tensor &tensor)
{
    CompactBinaryFormatSerializer serializer;
    serializer.serialize(stream, tensor);
}


void
CompactBinaryFormat::deserialize(nbostream &stream, TensorBuilder &builder)
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


} // namespace vespalib::tensor
} // namespace vespalib
