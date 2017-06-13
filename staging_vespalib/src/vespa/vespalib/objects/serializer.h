// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fieldbase.h"
#include <vespa/vespalib/util/array.h>
#include <vector>
#include <cstdint>

namespace vespalib {

class Identifiable;

class Serializer : virtual SerializerCommon
{
public:
    virtual ~Serializer() { }
    virtual Serializer & put(const IFieldBase & field, bool value) = 0;
    virtual Serializer & put(const IFieldBase & field, uint8_t value) = 0;
    virtual Serializer & put(const IFieldBase & field, uint16_t value) = 0;
    virtual Serializer & put(const IFieldBase & field, uint32_t value) = 0;
    virtual Serializer & put(const IFieldBase & field, uint64_t value) = 0;
    virtual Serializer & put(const IFieldBase & field, float value) = 0;
    virtual Serializer & put(const IFieldBase & field, double value) = 0;
    virtual Serializer & put(const IFieldBase & field, const stringref & value) = 0;

    virtual Serializer & put(const IFieldBase & field, const Identifiable & value);
    virtual Serializer & put(const IFieldBase & field, int8_t value);
    virtual Serializer & put(const IFieldBase & field, int16_t value);
    virtual Serializer & put(const IFieldBase & field, int32_t value);
    virtual Serializer & put(const IFieldBase & field, int64_t value);

    Serializer & operator << (bool value)     { return put(_unspecifiedField, value); }
    Serializer & operator << (uint8_t value)  { return put(_unspecifiedField, value); }
    Serializer & operator << (int8_t  value)  { return put(_unspecifiedField, value); }
    Serializer & operator << (uint16_t value) { return put(_unspecifiedField, value); }
    Serializer & operator << (int16_t  value) { return put(_unspecifiedField, value); }
    Serializer & operator << (uint32_t value) { return put(_unspecifiedField, value); }
    Serializer & operator << (int32_t  value) { return put(_unspecifiedField, value); }
    Serializer & operator << (uint64_t value) { return put(_unspecifiedField, value); }
    Serializer & operator << (int64_t  value) { return put(_unspecifiedField, value); }
    Serializer & operator << (float value)    { return put(_unspecifiedField, value); }
    Serializer & operator << (double value)   { return put(_unspecifiedField, value); }
    Serializer & operator << (const stringref & value) { return put(_unspecifiedField, value); }
    template <typename T>
    Serializer & operator << (const vespalib::Array<T> & v);
    template <typename T>
    Serializer & operator << (const std::vector<T> & v);
};

}

