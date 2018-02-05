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

using vespalib::nbostream;

namespace vespalib {
namespace tensor {


void
TypedBinaryFormat::serialize(nbostream &stream, const Tensor &tensor)
{
    if (auto denseTensor = dynamic_cast<const DenseTensorView *>(&tensor)) {
        stream.putInt1_4Bytes(DENSE_BINARY_FORMAT_TYPE);
        DenseBinaryFormat::serialize(stream, *denseTensor);
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
        return DenseBinaryFormat::deserialize(stream);
    }
    if (formatId == MIXED_BINARY_FORMAT_TYPE) {
        stream.adjustReadPos(read_pos - stream.rp());
        return std::make_unique<WrappedSimpleTensor>(eval::SimpleTensor::decode(stream));
    }
    abort();
}


} // namespace vespalib::tensor
} // namespace vespalib
