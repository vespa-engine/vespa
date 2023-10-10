// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search::attribute {

class BasicType
{
 public:
    enum Type : uint8_t {
        NONE      =  0,
        STRING    =  1,
        BOOL      =  2,
        UINT2     =  3,
        UINT4     =  4,
        INT8      =  5,
        INT16     =  6,
        INT32     =  7,
        INT64     =  8,
        FLOAT     =  9,
        DOUBLE    = 10,
        PREDICATE = 11,
        TENSOR    = 12,
        REFERENCE = 13,
        RAW       = 14,
        MAX_TYPE
    };

    explicit BasicType(int t) noexcept : _type(Type(t)) { }
    explicit BasicType(unsigned int t) noexcept : _type(Type(t)) { }
    BasicType(Type t) noexcept : _type(t) { }
    explicit BasicType(const vespalib::string & t) : _type(asType(t)) { }

    Type type() const noexcept { return _type; }
    const char * asString() const noexcept { return asString(_type); }
    size_t fixedSize() const noexcept { return fixedSize(_type); }
    static BasicType fromType(bool) noexcept { return BOOL; }
    static BasicType fromType(int8_t)  noexcept { return INT8; }
    static BasicType fromType(int16_t) noexcept { return INT16; }
    static BasicType fromType(int32_t) noexcept { return INT32; }
    static BasicType fromType(int64_t) noexcept { return INT64; }
    static BasicType fromType(float)  noexcept { return FLOAT; }
    static BasicType fromType(double) noexcept { return DOUBLE; }
    bool operator==(const BasicType &b) const noexcept { return _type == b._type; }
    bool operator!=(const BasicType &b) const noexcept { return _type != b._type; }

  private:
    static const char * asString(Type t) noexcept { return _typeTable[t]._name; }
    static size_t fixedSize(Type t) noexcept { return _typeTable[t]._fixedSize; }
    static Type asType(const vespalib::string & t);

    Type _type;

    struct TypeInfo {
        Type         _type;
        unsigned int _fixedSize;
        const char * _name;
    };
    static const TypeInfo _typeTable[MAX_TYPE];
};

}
