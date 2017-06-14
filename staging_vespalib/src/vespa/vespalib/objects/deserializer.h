// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fieldbase.h"
#include <vespa/vespalib/util/array.h>
#include <vector>
#include <cstdint>

namespace vespalib {

class Identifiable;

class Deserializer : virtual SerializerCommon
{
public:
    virtual ~Deserializer() { }
    virtual Deserializer & get(const IFieldBase & field, bool & value) = 0;
    virtual Deserializer & get(const IFieldBase & field, uint8_t & value) = 0;
    virtual Deserializer & get(const IFieldBase & field, uint16_t & value) = 0;
    virtual Deserializer & get(const IFieldBase & field, uint32_t & value) = 0;
    virtual Deserializer & get(const IFieldBase & field, uint64_t & value) = 0;
    virtual Deserializer & get(const IFieldBase & field, double & value) = 0;
    virtual Deserializer & get(const IFieldBase & field, float & value) = 0;
    virtual Deserializer & get(const IFieldBase & field, string & value) = 0;

    virtual Deserializer & get(const IFieldBase & field, Identifiable & value);
    virtual Deserializer & get(const IFieldBase & field, int8_t & value);
    virtual Deserializer & get(const IFieldBase & field, int16_t & value);
    virtual Deserializer & get(const IFieldBase & field, int32_t & value);
    virtual Deserializer & get(const IFieldBase & field, int64_t & value);

    uint8_t    getBool(const IFieldBase & field) { bool v; get(field, v); return v; }
    uint8_t   getUInt8(const IFieldBase & field) { uint8_t v; get(field, v); return v; }
    int8_t     getInt8(const IFieldBase & field) { int8_t v; get(field, v); return v; }
    uint16_t getUInt16(const IFieldBase & field) { uint16_t v; get(field, v); return v; }
    int16_t   getInt16(const IFieldBase & field) { int16_t v; get(field, v); return v; }
    uint32_t getUInt32(const IFieldBase & field) { uint32_t v; get(field, v); return v; }
    int32_t   getInt32(const IFieldBase & field) { int32_t v; get(field, v); return v; }
    uint64_t getUInt64(const IFieldBase & field) { uint64_t v; get(field, v); return v; }
    int64_t   getInt64(const IFieldBase & field) { int64_t v; get(field, v); return v; }
    float     getFloat(const IFieldBase & field) { float v; get(field, v); return v; }
    double   getDouble(const IFieldBase & field) { double v; get(field, v); return v; }
    string   getString(const IFieldBase & field) { string v; get(field, v); return v; }

    Deserializer & get(const IFieldBase & field, std::string & value);
    Deserializer & operator >> (bool & value)     { return get(_unspecifiedField, value); }
    Deserializer & operator >> (uint8_t & value)  { return get(_unspecifiedField, value); }
    Deserializer & operator >> (int8_t  & value)  { return get(_unspecifiedField, value); }
    Deserializer & operator >> (uint16_t & value) { return get(_unspecifiedField, value); }
    Deserializer & operator >> (int16_t  & value) { return get(_unspecifiedField, value); }
    Deserializer & operator >> (uint32_t & value) { return get(_unspecifiedField, value); }
    Deserializer & operator >> (int32_t &  value) { return get(_unspecifiedField, value); }
    Deserializer & operator >> (uint64_t & value) { return get(_unspecifiedField, value); }
    Deserializer & operator >> (int64_t  & value) { return get(_unspecifiedField, value); }
    Deserializer & operator >> (float & value)    { return get(_unspecifiedField, value); }
    Deserializer & operator >> (double & value)   { return get(_unspecifiedField, value); }
    Deserializer & operator >> (string & value)   { return get(_unspecifiedField, value); }
    template <typename T>
    Deserializer & operator >> (vespalib::Array<T> & v);
    template <typename T>
    Deserializer & operator >> (std::vector<T> & v);

};

}

