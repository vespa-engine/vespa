// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/util/serializableexceptions.h>
#include <vespa/eval/tensor/serialization/typed_binary_format.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/vespalib/objects/nbostream.h>

using document::DeserializeException;
using vespalib::tensor::Tensor;
using vespalib::tensor::TypedBinaryFormat;

namespace search::tensor {

std::unique_ptr<Tensor> deserialize_tensor(const void *data, size_t size)
{
    vespalib::nbostream wrapStream(data, size);
    auto tensor = TypedBinaryFormat::deserialize(wrapStream);
    if (wrapStream.size() != 0) {
        throw DeserializeException("Leftover bytes deserializing tensor attribute value.", VESPA_STRLOC);
    }
    return tensor;
}

} // namespace
