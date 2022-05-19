// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <cstdint>

namespace vespalib {

class Identifiable;

class Serializer
{
public:
    virtual ~Serializer() = default;
    virtual Serializer & put(bool value) = 0;
    virtual Serializer & put(uint8_t value) = 0;
    virtual Serializer & put(uint16_t value) = 0;
    virtual Serializer & put(uint32_t value) = 0;
    virtual Serializer & put(uint64_t value) = 0;
    virtual Serializer & put(float value) = 0;
    virtual Serializer & put(double value) = 0;
    virtual Serializer & put(stringref value) = 0;

    virtual Serializer & put(const Identifiable & value);
    virtual Serializer & put(int8_t value);
    virtual Serializer & put(int16_t value);
    virtual Serializer & put(int32_t value);
    virtual Serializer & put(int64_t value);

    Serializer & operator << (bool value)     { return put(value); }
    Serializer & operator << (uint8_t value)  { return put(value); }
    Serializer & operator << (int8_t  value)  { return put(value); }
    Serializer & operator << (uint16_t value) { return put(value); }
    Serializer & operator << (int16_t  value) { return put(value); }
    Serializer & operator << (uint32_t value) { return put(value); }
    Serializer & operator << (int32_t  value) { return put(value); }
    Serializer & operator << (uint64_t value) { return put(value); }
    Serializer & operator << (int64_t  value) { return put(value); }
    Serializer & operator << (float value)    { return put(value); }
    Serializer & operator << (double value)   { return put(value); }
    Serializer & operator << (stringref value) { return put(value); }
    template <typename T>
    Serializer & operator << (const vespalib::Array<T> & v);
    template <typename T>
    Serializer & operator << (const std::vector<T> & v);
};

}

