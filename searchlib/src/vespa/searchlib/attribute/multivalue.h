// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <stdint.h>

namespace search {

namespace multivalue {

template <typename T>
class Value {
public:
    typedef T ValueType;
    Value()
        : _v()
    {
    }
    Value(T v) : _v(v) { }
    Value(T v, int32_t w) : _v(v) { (void) w; }
    T value()           const { return _v; }
    operator T ()       const { return _v; }
    operator T & ()           { return _v; }
    int32_t weight()    const { return 1; }
    void setWeight(int32_t w) { (void) w; }
    void incWeight(int32_t w) { (void) w; }
    bool operator ==(const Value<T> & rhs) const { return _v == rhs._v; }
    bool operator <(const Value<T> & rhs) const { return _v < rhs._v; }
    bool operator >(const Value<T> & rhs) const { return _v > rhs._v; }
    static bool hasWeight() { return false; }

    static const bool _hasWeight = false;
private:
    T _v;
};

template <typename T>
class WeightedValue {
public:
    typedef T ValueType;
    WeightedValue() : _v(), _w(1) { }
    WeightedValue(T v, int32_t w) : _v(v), _w(w) { }
    T value()             const { return _v; }
    operator T ()         const { return _v; }
    operator T & ()             { return _v; }
    int32_t weight()      const { return _w; }
    void setWeight(int32_t w)   { _w = w; }
    void incWeight(int32_t w)   { _w += w; }

    bool operator==(const WeightedValue<T> & rhs) const { return _v == rhs._v; }
    bool operator <(const WeightedValue<T> & rhs) const { return _v < rhs._v; }
    bool operator >(const WeightedValue<T> & rhs) const { return _v > rhs._v; }
    static bool hasWeight() { return true; }

    static const bool _hasWeight = true;
private:
    T       _v;
    int32_t _w;
};

}
}

