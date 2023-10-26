// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace vespalib::slime {

/**
 * Enumeration of slime data types.
 **/
class Type
{
private:
    uint32_t _id;

protected:
    Type(uint32_t id) noexcept : _id(id) {}

public:
    uint32_t getId() const noexcept { return _id; }
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
using NIX = TypeType<0>;
using BOOL = TypeType<1>;
using LONG = TypeType<2>;
using DOUBLE = TypeType<3>;
using STRING = TypeType<4>;
using DATA = TypeType<5>;
using ARRAY = TypeType<6>;
using OBJECT = TypeType<7>;
extern template struct TypeType<NIX::ID>;
extern template struct TypeType<BOOL::ID>;
extern template struct TypeType<LONG::ID>;
extern template struct TypeType<DOUBLE::ID>;
extern template struct TypeType<STRING::ID>;
extern template struct TypeType<DATA::ID>;
extern template struct TypeType<ARRAY::ID>;
extern template struct TypeType<OBJECT::ID>;

} // namespace vespalib::slime
