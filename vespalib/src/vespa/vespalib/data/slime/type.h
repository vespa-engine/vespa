// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <stdint.h>

namespace vespalib {
namespace slime {

/**
 * Enumeration of slime data types.
 **/
class Type
{
private:
    uint32_t _id;

protected:
    Type(uint32_t id) : _id(id) {}

public:
    uint32_t getId() const { return _id; }
};

/**
 * Separate types for each data type; to be able to specify types at
 * compile-time as well as run-time.
 **/
template<int T>
struct TypeType : public Type {
    static const uint32_t ID = T;
    static const TypeType instance;
    TypeType() : Type(ID) {}
};
typedef TypeType<0> NIX;
typedef TypeType<1> BOOL;
typedef TypeType<2> LONG;
typedef TypeType<3> DOUBLE;
typedef TypeType<4> STRING;
typedef TypeType<5> DATA;
typedef TypeType<6> ARRAY;
typedef TypeType<7> OBJECT;

} // namespace vespalib::slime
} // namespace vespalib

