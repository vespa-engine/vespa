// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "typed_binary_format.h"
#include "compact_binary_format.h"
#include "dense_binary_format.h"
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/tensor/default_tensor.h>
#include <vespa/vespalib/tensor/tensor.h>
#include <vespa/vespalib/tensor/dense/dense_tensor.h>

using vespalib::nbostream;

namespace vespalib {
namespace tensor {


void
TypedBinaryFormat::serialize(nbostream &stream, const Tensor &tensor)
{
    const DenseTensor *denseTensor = dynamic_cast<const DenseTensor *>(&tensor);
    if (denseTensor != nullptr) {
        stream.putInt1_4Bytes(DENSE_BINARY_FORMAT_TYPE);
        DenseBinaryFormat::serialize(stream, *denseTensor);
    } else {
        stream.putInt1_4Bytes(COMPACT_BINARY_FORMAT_TYPE);
        CompactBinaryFormat::serialize(stream, tensor);
    }
}


void
TypedBinaryFormat::deserialize(nbostream &stream, TensorBuilder &builder)
{
    auto formatId = stream.getInt1_4Bytes();
    assert(formatId == COMPACT_BINARY_FORMAT_TYPE);
    CompactBinaryFormat::deserialize(stream, builder);
}


std::unique_ptr<Tensor>
TypedBinaryFormat::deserialize(nbostream &stream)
{
    auto formatId = stream.getInt1_4Bytes();
    if (formatId == COMPACT_BINARY_FORMAT_TYPE) {
        DefaultTensor::builder builder;
        CompactBinaryFormat::deserialize(stream, builder);
        return builder.build();
    }
    if (formatId == DENSE_BINARY_FORMAT_TYPE) {
        return DenseBinaryFormat::deserialize(stream);
    }
    abort();
}


} // namespace vespalib::tensor
} // namespace vespalib
