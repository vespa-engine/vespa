// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_deserialize.h"
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/objects/nbostream.h>

using document::DeserializeException;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::Value;

namespace search::tensor {

std::unique_ptr<Value> deserialize_tensor(vespalib::nbostream &buffer)
{
    try {
        auto tensor = vespalib::eval::decode_value(buffer, FastValueBuilderFactory::get());
        if (buffer.size() != 0) {
            throw DeserializeException("Leftover bytes deserializing tensor attribute value.", VESPA_STRLOC);
        }
        return tensor;
    } catch (const vespalib::eval::DecodeValueException &e) {
        throw DeserializeException("tensor value decode failed", e, VESPA_STRLOC);
    }
}

} // namespace
