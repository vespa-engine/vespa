// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/objects/nbo.h>
#include <cstring>
#include <functional>
#include <limits>
#include <algorithm>

namespace vespalib {

template<typename T, bool asc=true>
class convertForSort
{
};

template<>
class convertForSort<float, true>
{
public:
    using InputType = float;
    using IntType = int32_t;
    using UIntType = uint32_t;
    using Compare = std::less<InputType>;
    static inline UIntType convert(float value)
    {
        union { float f; UIntType u; } val;
        val.f=value;
        return (static_cast<IntType>(val.u) >= 0)
                ? (val.u ^ (UIntType(std::numeric_limits<IntType>::max()) + 1))
                : (val.u ^ std::numeric_limits<UIntType>::max());
    }
};

template<>
class convertForSort<float, false>
{
public:
    using InputType = float;
    using IntType = int32_t;
    using UIntType = uint32_t;
    using Compare = std::greater<InputType>;
    static inline UIntType convert(float value)
    {
        union { float f; UIntType u; } val;
        val.f=value;
        return (static_cast<IntType>(val.u) >= 0)
                ? (val.u ^ std::numeric_limits<IntType>::max())
                : val.u;
    }
};


template<>
class convertForSort<double, true>
{
public:
    using InputType = double;
    using IntType = int64_t;
    using UIntType = uint64_t;
    using Compare = std::less<InputType>;
    static inline UIntType convert(double value)
    {
        union { double f; UIntType u; } val;
        val.f=value;
        return (static_cast<IntType>(val.u) >= 0)
                ? (val.u ^ (UIntType(std::numeric_limits<IntType>::max()) + 1))
                : (val.u ^ std::numeric_limits<UIntType>::max());
    }
};

template<>
class convertForSort<double, false>
{
public:
    using InputType = double;
    using IntType = int64_t;
    using UIntType = uint64_t;
    using Compare = std::greater<InputType>;
    static inline UIntType convert(double value)
    {
        union { double f; UIntType u; } val;
        val.f=value;
        return (static_cast<IntType>(val.u) >= 0)
                ? (val.u ^ std::numeric_limits<IntType>::max())
                : val.u;
    }
};

template<>
class convertForSort<uint8_t, true>
{
public:
    using InputType = uint8_t;
    using IntType = int8_t;
    using UIntType = uint8_t;
    using Compare = std::less<InputType>;
    static inline UIntType convert(UIntType value)   { return value; }
};

template<>
class convertForSort<uint8_t, false>
{
public:
    using InputType = uint8_t;
    using IntType = int8_t;
    using UIntType = uint8_t;
    using Compare = std::greater<InputType>;
    static inline UIntType convert(UIntType value)  { return ~value; }
};
template<>
class convertForSort<uint16_t, true>
{
public:
    using InputType = uint16_t;
    using IntType = int16_t;
    using UIntType = uint16_t;
    using Compare = std::less<InputType>;
    static inline UIntType convert(UIntType value)  { return value; }
};
template<>
class convertForSort<uint16_t, false>
{
public:
    using InputType = uint16_t;
    using IntType = int16_t;
    using UIntType = uint16_t;
    using Compare = std::greater<InputType>;
    static inline UIntType convert(UIntType value) { return ~value; }
};
template<>
class convertForSort<uint32_t, true>
{
public:
    using InputType = uint32_t;
    using IntType = int32_t;
    using UIntType = uint32_t;
    using Compare = std::less<InputType>;
    static inline UIntType convert(UIntType value)  { return value; }
};
template<>
class convertForSort<uint32_t, false>
{
public:
    using InputType = uint32_t;
    using IntType = int32_t;
    using UIntType = uint32_t;
    using Compare = std::greater<InputType>;
    static inline UIntType convert(UIntType value) { return ~value; }
};
template<>
class convertForSort<uint64_t, true>
{
public:
    using InputType = uint64_t;
    using IntType = int64_t;
    using UIntType = uint64_t;
    using Compare = std::less<InputType>;
    static inline UIntType convert(UIntType value)  { return value; }
};
template<>
class convertForSort<uint64_t, false>
{
public:
    using InputType = uint64_t;
    using IntType = int64_t;
    using UIntType = uint64_t;
    using Compare = std::greater<InputType>;
    static inline UIntType convert(UIntType value) { return ~value; }
};

template<>
class convertForSort<bool, true>
{
public:
    using InputType = bool;
    using IntType = bool;
    using UIntType = bool;
    using Compare = std::less<InputType>;
    static inline UIntType convert(IntType value)   { return value; }
};
template<>
class convertForSort<bool, false>
{
public:
    using InputType = bool;
    using IntType = bool;
    using UIntType = bool;
    using Compare = std::less<InputType>;
    static inline UIntType convert(IntType value)   { return !value; }
};

template<>
class convertForSort<int8_t, true>
{
public:
    using InputType = int8_t;
    using IntType = int8_t;
    using UIntType = uint8_t;
    using Compare = std::less<InputType>;
    static inline UIntType convert(IntType value)   { return value ^ (std::numeric_limits<IntType>::max() + 1); }
};
template<>
class convertForSort<int8_t, false>
{
public:
    using InputType = int8_t;
    using IntType = int8_t;
    using UIntType = uint8_t;
    using Compare = std::greater<InputType>;
    static inline UIntType convert(IntType value)  { return value ^ std::numeric_limits<IntType>::max(); }
};
template<>
class convertForSort<int16_t, true>
{
public:
    using InputType = int16_t;
    using IntType = int16_t;
    using UIntType = uint16_t;
    using Compare = std::less<InputType>;
    static inline UIntType convert(IntType value)  { return value ^ (std::numeric_limits<IntType>::max() + 1); }
};
template<>
class convertForSort<int16_t, false>
{
public:
    using InputType = int16_t;
    using IntType = int16_t;
    using UIntType = uint16_t;
    using Compare = std::greater<InputType>;
    static inline UIntType convert(IntType value) { return value ^ std::numeric_limits<IntType>::max(); }
};
template<>
class convertForSort<int32_t, true>
{
public:
    using InputType = int32_t;
    using IntType = int32_t;
    using UIntType = uint32_t;
    using Compare = std::less<InputType>;
    static inline UIntType convert(IntType value)  { return value ^ (UIntType(std::numeric_limits<IntType>::max()) + 1); }
};
template<>
class convertForSort<int32_t, false>
{
public:
    using InputType = int32_t;
    using IntType = int32_t;
    using UIntType = uint32_t;
    using Compare = std::greater<InputType>;
    static inline UIntType convert(IntType value) { return value ^ std::numeric_limits<IntType>::max(); }
};
template<>
class convertForSort<int64_t, true>
{
public:
    using InputType = int64_t;
    using IntType = int64_t;
    using UIntType = uint64_t;
    using Compare = std::less<InputType>;
    static inline UIntType convert(IntType value)  { return value ^ (UIntType(std::numeric_limits<IntType>::max()) + 1); }
};
template<>
class convertForSort<int64_t, false>
{
public:
    using InputType = int64_t;
    using IntType = int64_t;
    using UIntType = uint64_t;
    using Compare = std::greater<InputType>;
    static inline UIntType convert(IntType value) { return value ^ std::numeric_limits<IntType>::max(); }
};

template<typename C>
int32_t serializeForSort(typename C::InputType v, void * dst, uint32_t available) {
    typename C::UIntType nbo(vespalib::nbo::n2h(C::convert(v)));
    if (available < sizeof(nbo)) return -1;
    memcpy(dst, &nbo, sizeof(nbo));
    return sizeof(nbo);
}

} // namespace vespalib

