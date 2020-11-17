// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_deserialize.h"
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/eval/eval/engine_or_factory.h>
#include <vespa/eval/eval/value.h>

using document::DeserializeException;
using vespalib::eval::EngineOrFactory;
using vespalib::eval::Value;

namespace search::tensor {

std::unique_ptr<Value> deserialize_tensor(vespalib::nbostream &buffer)
{
    auto tensor = EngineOrFactory::get().decode(buffer);
    if (buffer.size() != 0) {
        throw DeserializeException("Leftover bytes deserializing tensor attribute value.", VESPA_STRLOC);
    }
    return tensor;
}

std::unique_ptr<Value> deserialize_tensor(const void *data, size_t size)
{
    vespalib::nbostream wrapStream(data, size);
    return deserialize_tensor(wrapStream);
}

} // namespace
