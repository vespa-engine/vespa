// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "typed_binary_format.h"
#include "sparse_binary_format.h"
#include "dense_binary_format.h"
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/eval/tensor/default_tensor.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/dense/dense_tensor.h>

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
        stream.putInt1_4Bytes(SPARSE_BINARY_FORMAT_TYPE);
        SparseBinaryFormat::serialize(stream, tensor);
    }
}


std::unique_ptr<Tensor>
TypedBinaryFormat::deserialize(nbostream &stream)
{
    auto formatId = stream.getInt1_4Bytes();
    if (formatId == SPARSE_BINARY_FORMAT_TYPE) {
        DefaultTensor::builder builder;
        SparseBinaryFormat::deserialize(stream, builder);
        return builder.build();
    }
    if (formatId == DENSE_BINARY_FORMAT_TYPE) {
        return DenseBinaryFormat::deserialize(stream);
    }
    abort();
}


} // namespace vespalib::tensor
} // namespace vespalib
