// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::multivalue {

template <typename T>
class WeightedValue {
public:
    WeightedValue() noexcept : _v(), _w(1) { }
    WeightedValue(T v, int32_t w) noexcept : _v(v), _w(w) { }
    T value()            const noexcept { return _v; }
    const T& value_ref() const noexcept { return _v; }
    T& value_ref()             noexcept { return _v; }
    operator T ()        const noexcept { return _v; }
    operator T & ()            noexcept { return _v; }
    int32_t weight()     const noexcept { return _w; }

    bool operator==(const WeightedValue<T> & rhs) const { return _v == rhs._v; }
    bool operator <(const WeightedValue<T> & rhs) const { return _v < rhs._v; }
    bool operator >(const WeightedValue<T> & rhs) const { return _v > rhs._v; }
private:
    T       _v;
    int32_t _w;
};

template <typename T>
inline int32_t get_weight(const T&) noexcept { return 1; }

template <typename T>
inline int32_t get_weight(const WeightedValue<T>& value) noexcept { return value.weight(); }

template <typename T>
inline T get_value(const T& value) noexcept { return value; }

template <typename T>
inline T get_value(const WeightedValue<T>& value) noexcept { return value.value(); }

template <typename T>
inline const T& get_value_ref(const T& value) noexcept { return value; }

template <typename T>
inline const T& get_value_ref(const WeightedValue<T>& value) noexcept { return value.value_ref(); }

template <typename T>
inline T& get_value_ref(T& value) noexcept { return value; }

template <typename T>
inline T& get_value_ref(WeightedValue<T>& value) noexcept { return value.value_ref(); }

template <typename M>
struct ValueBuilder
{
    static M build(M value, int32_t) noexcept { return value; }
};

template <typename T>
struct ValueBuilder<WeightedValue<T>>
{
    static WeightedValue<T> build(T value, int32_t weight) noexcept { return WeightedValue<T>(value, weight); }
};

}
