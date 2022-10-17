// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/objects/nbo.h>
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
    typedef float InputType;
    typedef int32_t  IntType;
    typedef uint32_t UIntType;
    typedef std::less<InputType> Compare;
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
    typedef float InputType;
    typedef int32_t  IntType;
    typedef uint32_t UIntType;
    typedef std::greater<InputType> Compare;
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
    typedef double InputType;
    typedef int64_t  IntType;
    typedef uint64_t UIntType;
    typedef std::less<InputType> Compare;
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
    typedef double InputType;
    typedef int64_t  IntType;
    typedef uint64_t UIntType;
    typedef std::greater<InputType> Compare;
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
    typedef uint8_t InputType;
    typedef int8_t  IntType;
    typedef uint8_t UIntType;
    typedef std::less<InputType> Compare;
    static inline UIntType convert(UIntType value)   { return value; }
};

template<>
class convertForSort<uint8_t, false>
{
public:
    typedef uint8_t InputType;
    typedef int8_t  IntType;
    typedef uint8_t UIntType;
    typedef std::greater<InputType> Compare;
    static inline UIntType convert(UIntType value)  { return ~value; }
};
template<>
class convertForSort<uint16_t, true>
{
public:
    typedef uint16_t InputType;
    typedef int16_t  IntType;
    typedef uint16_t UIntType;
    typedef std::less<InputType> Compare;
    static inline UIntType convert(UIntType value)  { return value; }
};
template<>
class convertForSort<uint16_t, false>
{
public:
    typedef uint16_t InputType;
    typedef int16_t  IntType;
    typedef uint16_t UIntType;
    typedef std::greater<InputType> Compare;
    static inline UIntType convert(UIntType value) { return ~value; }
};
template<>
class convertForSort<uint32_t, true>
{
public:
    typedef uint32_t InputType;
    typedef int32_t  IntType;
    typedef uint32_t UIntType;
    typedef std::less<InputType> Compare;
    static inline UIntType convert(UIntType value)  { return value; }
};
template<>
class convertForSort<uint32_t, false>
{
public:
    typedef uint32_t InputType;
    typedef int32_t  IntType;
    typedef uint32_t UIntType;
    typedef std::greater<InputType> Compare;
    static inline UIntType convert(UIntType value) { return ~value; }
};
template<>
class convertForSort<uint64_t, true>
{
public:
    typedef uint64_t InputType;
    typedef int64_t  IntType;
    typedef uint64_t UIntType;
    typedef std::less<InputType> Compare;
    static inline UIntType convert(UIntType value)  { return value; }
};
template<>
class convertForSort<uint64_t, false>
{
public:
    typedef uint64_t InputType;
    typedef int64_t  IntType;
    typedef uint64_t UIntType;
    typedef std::greater<InputType> Compare;
    static inline UIntType convert(UIntType value) { return ~value; }
};

template<>
class convertForSort<bool, true>
{
public:
    typedef bool  InputType;
    typedef bool  IntType;
    typedef bool UIntType;
    typedef std::less<InputType> Compare;
    static inline UIntType convert(IntType value)   { return value; }
};
template<>
class convertForSort<bool, false>
{
public:
    typedef bool  InputType;
    typedef bool  IntType;
    typedef bool UIntType;
    typedef std::less<InputType> Compare;
    static inline UIntType convert(IntType value)   { return !value; }
};

template<>
class convertForSort<int8_t, true>
{
public:
    typedef int8_t  InputType;
    typedef int8_t  IntType;
    typedef uint8_t UIntType;
    typedef std::less<InputType> Compare;
    static inline UIntType convert(IntType value)   { return value ^ (std::numeric_limits<IntType>::max() + 1); }
};
template<>
class convertForSort<int8_t, false>
{
public:
    typedef int8_t  InputType;
    typedef int8_t  IntType;
    typedef uint8_t UIntType;
    typedef std::greater<InputType> Compare;
    static inline UIntType convert(IntType value)  { return value ^ std::numeric_limits<IntType>::max(); }
};
template<>
class convertForSort<int16_t, true>
{
public:
    typedef int16_t  InputType;
    typedef int16_t  IntType;
    typedef uint16_t UIntType;
    typedef std::less<InputType> Compare;
    static inline UIntType convert(IntType value)  { return value ^ (std::numeric_limits<IntType>::max() + 1); }
};
template<>
class convertForSort<int16_t, false>
{
public:
    typedef int16_t  InputType;
    typedef int16_t  IntType;
    typedef uint16_t UIntType;
    typedef std::greater<InputType> Compare;
    static inline UIntType convert(IntType value) { return value ^ std::numeric_limits<IntType>::max(); }
};
template<>
class convertForSort<int32_t, true>
{
public:
    typedef int32_t  InputType;
    typedef int32_t  IntType;
    typedef uint32_t UIntType;
    typedef std::less<InputType> Compare;
    static inline UIntType convert(IntType value)  { return value ^ (UIntType(std::numeric_limits<IntType>::max()) + 1); }
};
template<>
class convertForSort<int32_t, false>
{
public:
    typedef int32_t  InputType;
    typedef int32_t  IntType;
    typedef uint32_t UIntType;
    typedef std::greater<InputType> Compare;
    static inline UIntType convert(IntType value) { return value ^ std::numeric_limits<IntType>::max(); }
};
template<>
class convertForSort<int64_t, true>
{
public:
    typedef int64_t  InputType;
    typedef int64_t  IntType;
    typedef uint64_t UIntType;
    typedef std::less<InputType> Compare;
    static inline UIntType convert(IntType value)  { return value ^ (UIntType(std::numeric_limits<IntType>::max()) + 1); }
};
template<>
class convertForSort<int64_t, false>
{
public:
    typedef int64_t  InputType;
    typedef int64_t  IntType;
    typedef uint64_t UIntType;
    typedef std::greater<InputType> Compare;
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

