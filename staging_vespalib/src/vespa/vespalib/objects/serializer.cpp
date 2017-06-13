// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "serializer.h"
#include "identifiable.h"

namespace vespalib {

Serializer & Serializer::put(const IFieldBase & field,  const Identifiable & value)
{
    (void) field;
    return value.serializeDirect(*this);
}

Serializer & Serializer::put(const IFieldBase & field,  int8_t value) { return put(field, static_cast< uint8_t>(value)); }
Serializer & Serializer::put(const IFieldBase & field, int16_t value) { return put(field, static_cast<uint16_t>(value)); }
Serializer & Serializer::put(const IFieldBase & field, int32_t value) { return put(field, static_cast<uint32_t>(value)); }
Serializer & Serializer::put(const IFieldBase & field, int64_t value) { return put(field, static_cast<uint64_t>(value)); }

}
