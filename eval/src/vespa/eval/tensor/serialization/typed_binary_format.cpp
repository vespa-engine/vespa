// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "typed_binary_format.h"
#include "sparse_binary_format.h"
#include "dense_binary_format.h"
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/tensor/wrapped_simple_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/engine_or_factory.h>

#include <vespa/log/log.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exceptions.h>

LOG_SETUP(".eval.tensor.serialization.typed_binary_format");

using vespalib::nbostream;
using vespalib::eval::ValueType;
using vespalib::eval::CellType;

namespace vespalib::tensor {

namespace  {

const eval::EngineOrFactory &simple_engine() {
    static eval::EngineOrFactory engine(eval::SimpleValueBuilderFactory::get());
    return engine;
}

constexpr uint32_t SPARSE_BINARY_FORMAT_TYPE = 1u;
constexpr uint32_t DENSE_BINARY_FORMAT_TYPE = 2u;
constexpr uint32_t MIXED_BINARY_FORMAT_TYPE = 3u;
constexpr uint32_t SPARSE_BINARY_FORMAT_WITH_CELLTYPE = 5u;
constexpr uint32_t DENSE_BINARY_FORMAT_WITH_CELLTYPE = 6u;
constexpr uint32_t MIXED_BINARY_FORMAT_WITH_CELLTYPE = 7u;

constexpr uint32_t DOUBLE_VALUE_TYPE = 0;
constexpr uint32_t FLOAT_VALUE_TYPE = 1;

uint32_t cell_type_to_encoding(CellType cell_type) {
    switch (cell_type) {
    case CellType::DOUBLE:
        return DOUBLE_VALUE_TYPE;
    case CellType::FLOAT:
        return FLOAT_VALUE_TYPE;
    }
    abort();
}

CellType
encoding_to_cell_type(uint32_t cell_encoding) {
    switch (cell_encoding) {
    case DOUBLE_VALUE_TYPE:
        return CellType::DOUBLE;
    case FLOAT_VALUE_TYPE:
        return CellType::FLOAT;
    default:
        throw IllegalArgumentException(make_string("Received unknown tensor value type = %u. Only 0(double), or 1(float) are legal.", cell_encoding));
    }
}

std::unique_ptr<Tensor>
wrap_simple_value(std::unique_ptr<eval::Value> simple)
{
    if (Tensor::supported({simple->type()})) {
        nbostream data;
        simple_engine().encode(*simple, data);
        // note: some danger of infinite recursion here
        return TypedBinaryFormat::deserialize(data);
    }
    return std::make_unique<WrappedSimpleValue>(std::move(simple));
}

} // namespace <unnamed>

void
TypedBinaryFormat::serialize(nbostream &stream, const Tensor &tensor)
{
    auto cell_type = tensor.type().cell_type();
    bool default_cell_type = (cell_type == CellType::DOUBLE);
    if (auto denseTensor = dynamic_cast<const DenseTensorView *>(&tensor)) {
        if (default_cell_type) {
            stream.putInt1_4Bytes(DENSE_BINARY_FORMAT_TYPE);
        } else {
            stream.putInt1_4Bytes(DENSE_BINARY_FORMAT_WITH_CELLTYPE);
            stream.putInt1_4Bytes(cell_type_to_encoding(cell_type));
        }
        DenseBinaryFormat::serialize(stream, *denseTensor);
    } else if (dynamic_cast<const WrappedSimpleValue *>(&tensor)) {
        eval::encode_value(tensor, stream);
    } else {
        if (default_cell_type) {
            stream.putInt1_4Bytes(SPARSE_BINARY_FORMAT_TYPE);
        } else {
            stream.putInt1_4Bytes(SPARSE_BINARY_FORMAT_WITH_CELLTYPE);
            stream.putInt1_4Bytes(cell_type_to_encoding(cell_type));
        }
        SparseBinaryFormat::serialize(stream, tensor);
    }
}


std::unique_ptr<Tensor>
TypedBinaryFormat::deserialize(nbostream &stream)
{
    auto cell_type = CellType::DOUBLE;
    auto read_pos = stream.rp();
    auto formatId = stream.getInt1_4Bytes();
    switch (formatId) {
    case SPARSE_BINARY_FORMAT_WITH_CELLTYPE:
        cell_type = encoding_to_cell_type(stream.getInt1_4Bytes());
        [[fallthrough]];
    case SPARSE_BINARY_FORMAT_TYPE:
        return SparseBinaryFormat::deserialize(stream, cell_type);
    case DENSE_BINARY_FORMAT_WITH_CELLTYPE:
        cell_type = encoding_to_cell_type(stream.getInt1_4Bytes());
        [[fallthrough]];
    case DENSE_BINARY_FORMAT_TYPE:
        return DenseBinaryFormat::deserialize(stream, cell_type);
    case MIXED_BINARY_FORMAT_TYPE:
    case MIXED_BINARY_FORMAT_WITH_CELLTYPE:
        stream.adjustReadPos(read_pos - stream.rp());
        return wrap_simple_value(simple_engine().decode(stream));
    default:
        throw IllegalArgumentException(make_string("Received unknown tensor format type = %du.", formatId));
    }
}

template <typename T>
void
TypedBinaryFormat::deserializeCellsOnlyFromDenseTensors(nbostream &stream, std::vector<T> &cells)
{
    auto cell_type = CellType::DOUBLE;
    auto formatId = stream.getInt1_4Bytes();
    switch (formatId) {
    case DENSE_BINARY_FORMAT_WITH_CELLTYPE:
        cell_type = encoding_to_cell_type(stream.getInt1_4Bytes());
        [[fallthrough]];
    case DENSE_BINARY_FORMAT_TYPE:
        return DenseBinaryFormat::deserializeCellsOnly(stream, cells, cell_type);
    }
    abort();
}

template void TypedBinaryFormat::deserializeCellsOnlyFromDenseTensors(nbostream &stream, std::vector<double> &cells);
template void TypedBinaryFormat::deserializeCellsOnlyFromDenseTensors(nbostream &stream, std::vector<float> &cells);
    
}
