// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "typed_binary_format.h"
#include "sparse_binary_format.h"
#include "dense_binary_format.h"
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/eval/tensor/default_tensor.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/eval/eval/simple_tensor.h>
#include <vespa/eval/tensor/wrapped_simple_tensor.h>

#include <vespa/log/log.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exceptions.h>

LOG_SETUP(".eval.tensor.serialization.typed_binary_format");

using vespalib::nbostream;

namespace vespalib::tensor {

namespace  {

constexpr uint32_t SPARSE_BINARY_FORMAT_TYPE = 1u;
constexpr uint32_t DENSE_BINARY_FORMAT_TYPE = 2u;
constexpr uint32_t MIXED_BINARY_FORMAT_TYPE = 3u;
constexpr uint32_t SPARSE_BINARY_FORMAT_WITH_CELLTYPE = 5u; //Future
constexpr uint32_t DENSE_BINARY_FORMAT_WITH_CELLTYPE = 6u;
constexpr uint32_t MIXED_BINARY_FORMAT_WITH_CELLTYPE = 7u; //Future

constexpr uint32_t DOUBLE_VALUE_TYPE = 0;
constexpr uint32_t FLOAT_VALUE_TYPE = 1;

uint32_t
format2Encoding(SerializeFormat format) {
    switch (format) {
        case SerializeFormat::DOUBLE:
            return DOUBLE_VALUE_TYPE;
        case SerializeFormat::FLOAT:
            return FLOAT_VALUE_TYPE;
    }
    abort();
}

SerializeFormat
encoding2Format(uint32_t serializedType) {
    switch (serializedType) {
        case DOUBLE_VALUE_TYPE:
            return SerializeFormat::DOUBLE;
        case FLOAT_VALUE_TYPE:
            return  SerializeFormat::FLOAT;
        default:
            throw IllegalArgumentException(make_string("Received unknown tensor value type = %u. Only 0(double), or 1(float) are legal.", serializedType));
    }
}

}

void
TypedBinaryFormat::serialize(nbostream &stream, const Tensor &tensor, SerializeFormat format)
{
    if (auto denseTensor = dynamic_cast<const DenseTensorView *>(&tensor)) {
        if (format != SerializeFormat::DOUBLE) {
            stream.putInt1_4Bytes(DENSE_BINARY_FORMAT_WITH_CELLTYPE);
            stream.putInt1_4Bytes(format2Encoding(format));
            DenseBinaryFormat(format).serialize(stream, *denseTensor);
        } else {
            stream.putInt1_4Bytes(DENSE_BINARY_FORMAT_TYPE);
            DenseBinaryFormat(SerializeFormat::DOUBLE).serialize(stream, *denseTensor);
        }
    } else if (auto wrapped = dynamic_cast<const WrappedSimpleTensor *>(&tensor)) {
        eval::SimpleTensor::encode(wrapped->get(), stream);
    } else {
        stream.putInt1_4Bytes(SPARSE_BINARY_FORMAT_TYPE);
        SparseBinaryFormat::serialize(stream, tensor);
    }
}


std::unique_ptr<Tensor>
TypedBinaryFormat::deserialize(nbostream &stream)
{
    auto read_pos = stream.rp();
    auto formatId = stream.getInt1_4Bytes();
    if (formatId == SPARSE_BINARY_FORMAT_TYPE) {
        DefaultTensor::builder builder;
        SparseBinaryFormat::deserialize(stream, builder);
        return builder.build();
    }
    if (formatId == DENSE_BINARY_FORMAT_TYPE) {
        return DenseBinaryFormat(SerializeFormat::DOUBLE).deserialize(stream);
    }
    if (formatId == DENSE_BINARY_FORMAT_WITH_CELLTYPE) {
        return DenseBinaryFormat(encoding2Format(stream.getInt1_4Bytes())).deserialize(stream);
    }
    if (formatId == MIXED_BINARY_FORMAT_TYPE) {
        stream.adjustReadPos(read_pos - stream.rp());
        return std::make_unique<WrappedSimpleTensor>(eval::SimpleTensor::decode(stream));
    }
    abort();
}

template <typename T>
void
TypedBinaryFormat::deserializeCellsOnlyFromDenseTensors(nbostream &stream, std::vector<T> & cells)
{
    auto formatId = stream.getInt1_4Bytes();
    if (formatId == DENSE_BINARY_FORMAT_TYPE) {
        return DenseBinaryFormat(SerializeFormat::DOUBLE).deserializeCellsOnly(stream, cells);
    }
    if (formatId == DENSE_BINARY_FORMAT_WITH_CELLTYPE) {
        return DenseBinaryFormat(encoding2Format(stream.getInt1_4Bytes())).deserializeCellsOnly(stream, cells);
    }
    abort();
}

template void TypedBinaryFormat::deserializeCellsOnlyFromDenseTensors(nbostream &stream, std::vector<double> & cells);
template void TypedBinaryFormat::deserializeCellsOnlyFromDenseTensors(nbostream &stream, std::vector<float> & cells);
    
}
