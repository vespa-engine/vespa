// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "slime_binary_format.h"
#include <vespa/eval/tensor/types.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/tensor_builder.h>
#include <vespa/eval/tensor/tensor_visitor.h>
#include <vespa/vespalib/data/slime/inserter.h>
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/memory.h>

namespace vespalib {
namespace tensor {


using slime::Inserter;
using slime::SlimeInserter;
using slime::Cursor;
using slime::ObjectInserter;

namespace {

Memory memory_address("address");
Memory memory_cells("cells");
Memory memory_dimensions("dimensions");
Memory memory_value("value");

void writeTensorAddress(Cursor &cursor, const TensorAddress &value) {
    ObjectInserter addressInserter(cursor, memory_address);
    Cursor &addressCursor = addressInserter.insertObject();
    for (const auto &elem : value.elements()) {
        Memory dimension(elem.dimension());
        Memory label(elem.label());
        addressCursor.setString(dimension, label);
    }
}

}

class SlimeBinaryFormatSerializer : public TensorVisitor
{
    Cursor &_tensor;      // cursor for whole tensor
    Cursor &_dimensions;  // cursor for dimensions array
    Cursor &_cells;       // cursor for cells array
public:
    SlimeBinaryFormatSerializer(Inserter &inserter);
    virtual ~SlimeBinaryFormatSerializer() override;
    virtual void visit(const TensorAddress &address, double value) override;
    void serialize(const Tensor &tensor);
};

SlimeBinaryFormatSerializer::SlimeBinaryFormatSerializer(Inserter &inserter)
    : _tensor(inserter.insertObject()),
      _dimensions(_tensor.setArray(memory_dimensions)),
      _cells(_tensor.setArray(memory_cells))
{
}


SlimeBinaryFormatSerializer::~SlimeBinaryFormatSerializer()
{
}

void
SlimeBinaryFormatSerializer::visit(const TensorAddress &address,
                                     double value)
{
    Cursor &cellCursor = _cells.addObject();
    writeTensorAddress(cellCursor, address);
    cellCursor.setDouble(memory_value, value);
}


void
SlimeBinaryFormatSerializer::serialize(const Tensor &tensor)
{
    eval::ValueType type(tensor.type());
    for (const auto & dimension : type.dimensions()) {
        _dimensions.addString(Memory(dimension.name));
    }
    tensor.accept(*this);
}


void
SlimeBinaryFormat::serialize(Inserter &inserter, const Tensor &tensor)
{
    SlimeBinaryFormatSerializer serializer(inserter);
    serializer.serialize(tensor);
}


std::unique_ptr<Slime>
SlimeBinaryFormat::serialize(const Tensor &tensor)
{
    auto slime = std::make_unique<Slime>();
    SlimeInserter inserter(*slime);
    serialize(inserter, tensor);
    return slime;
}


} // namespace vespalib::tensor
} // namespace vespalib
