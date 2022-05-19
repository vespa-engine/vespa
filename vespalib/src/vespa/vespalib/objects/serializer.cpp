// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "serializer.h"
#include "identifiable.h"

namespace vespalib {

Serializer & Serializer::put(const Identifiable & value)
{
    return value.serializeDirect(*this);
}

Serializer & Serializer::put(int8_t value) { return put(static_cast< uint8_t>(value)); }
Serializer & Serializer::put(int16_t value) { return put(static_cast<uint16_t>(value)); }
Serializer & Serializer::put(int32_t value) { return put(static_cast<uint32_t>(value)); }
Serializer & Serializer::put(int64_t value) { return put(static_cast<uint64_t>(value)); }

}
