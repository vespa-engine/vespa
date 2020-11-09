// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "deserializer.h"
#include "identifiable.h"

namespace vespalib {

Deserializer & Deserializer::get(Identifiable & value)
{
    return value.deserializeDirect(*this);
}
Deserializer & Deserializer::get(int8_t & value)
{
    uint8_t v(0);
    get(v);
    value = v;
    return *this;
}
Deserializer & Deserializer::get(int16_t & value)
{
    uint16_t v(0);
    get(v);
    value = v;
    return *this;
}
Deserializer & Deserializer::get(int32_t & value)
{
    uint32_t v(0);
    get(v);
    value = v;
    return *this;
}
Deserializer & Deserializer::get(int64_t & value)
{
    uint64_t v(0);
    get(v);
    value = v;
    return *this;
}
Deserializer & Deserializer::get(std::string & value)
{
    string v;
    get(v);
    value = v;
    return *this;
}
}
