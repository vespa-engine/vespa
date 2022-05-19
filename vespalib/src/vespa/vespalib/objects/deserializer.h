// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <cstdint>

namespace vespalib {

class Identifiable;

class Deserializer
{
public:
    virtual ~Deserializer() = default;
    virtual Deserializer & get(bool & value) = 0;
    virtual Deserializer & get(uint8_t & value) = 0;
    virtual Deserializer & get(uint16_t & value) = 0;
    virtual Deserializer & get(uint32_t & value) = 0;
    virtual Deserializer & get(uint64_t & value) = 0;
    virtual Deserializer & get(double & value) = 0;
    virtual Deserializer & get(float & value) = 0;
    virtual Deserializer & get(string & value) = 0;

    virtual Deserializer & get(Identifiable & value);
    virtual Deserializer & get(int8_t & value);
    virtual Deserializer & get(int16_t & value);
    virtual Deserializer & get(int32_t & value);
    virtual Deserializer & get(int64_t & value);


    Deserializer & get(std::string & value);
    Deserializer & operator >> (bool & value)     { return get(value); }
    Deserializer & operator >> (uint8_t & value)  { return get(value); }
    Deserializer & operator >> (int8_t  & value)  { return get(value); }
    Deserializer & operator >> (uint16_t & value) { return get(value); }
    Deserializer & operator >> (int16_t  & value) { return get(value); }
    Deserializer & operator >> (uint32_t & value) { return get(value); }
    Deserializer & operator >> (int32_t &  value) { return get(value); }
    Deserializer & operator >> (uint64_t & value) { return get(value); }
    Deserializer & operator >> (int64_t  & value) { return get(value); }
    Deserializer & operator >> (float & value)    { return get(value); }
    Deserializer & operator >> (double & value)   { return get(value); }
    Deserializer & operator >> (string & value)   { return get(value); }
    template <typename T>
    Deserializer & operator >> (vespalib::Array<T> & v);
    template <typename T>
    Deserializer & operator >> (std::vector<T> & v);

};

}

