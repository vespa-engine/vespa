// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "array.h"
#include <vector>

namespace vespalib {

/**
 * This is a simple wrapper class for a typed array with no memory ownership.
 * It is similar to vespalib::stringref
 **/
template <typename T>
class ArrayRef {
public:
    ArrayRef() : _v(nullptr), _sz(0) { }
    ArrayRef(T * v, size_t sz) : _v(v), _sz(sz) { }
    ArrayRef(std::vector<T> & v) : _v(&v[0]), _sz(v.size()) { }
    ArrayRef(Array<T> &v) : _v(&v[0]), _sz(v.size()) { }
    T & operator [] (size_t i) { return _v[i]; }
    const T & operator [] (size_t i) const { return _v[i]; }
    size_t size() const { return _sz; }
    T *begin() { return _v; }
    T *end() { return _v + _sz; }
private:
    T    * _v;
    size_t _sz;
};

template <typename T>
class ConstArrayRef {
public:
    ConstArrayRef(const T *v, size_t sz) : _v(v), _sz(sz) { }
    ConstArrayRef(const std::vector<T> & v) : _v(&v[0]), _sz(v.size()) { }
    ConstArrayRef(const ArrayRef<T> & v) : _v(&v[0]), _sz(v.size()) { }
    ConstArrayRef(const Array<T> &v) : _v(&v[0]), _sz(v.size()) { }
    ConstArrayRef() : _v(nullptr), _sz(0) {}
    const T & operator [] (size_t i) const { return _v[i]; }
    size_t size() const { return _sz; }
    const T *cbegin() const { return _v; }
    const T *cend() const { return _v + _sz; }
    const T *begin() const { return _v; }
    const T *end() const { return _v + _sz; }
private:
    const T *_v;
    size_t   _sz;
};

// const-cast for array references; use with care
template <typename T>
ArrayRef<T> unconstify(const ConstArrayRef<T> &ref) {
    return ArrayRef<T>(const_cast<T*>(&ref[0]), ref.size());
}

}
