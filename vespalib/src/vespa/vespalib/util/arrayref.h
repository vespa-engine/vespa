// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstddef>
#include <vector>

namespace vespalib {

/**
 * This is a simple wrapper class for a typed array with no memory ownership.
 * It is similar to vespalib::stringref
 **/
template <typename T>
class ArrayRef {
public:
    constexpr ArrayRef() noexcept : _v(nullptr), _sz(0) { }
    constexpr ArrayRef(T * v, size_t sz) noexcept : _v(v), _sz(sz) { }
    template<typename A=std::allocator<T>>
    ArrayRef(std::vector<T, A> & v) noexcept : _v(v.data()), _sz(v.size()) { }
    T & operator [] (size_t i) noexcept { return _v[i]; }
    const T & operator [] (size_t i) const noexcept { return _v[i]; }
    T * data() noexcept { return _v; }
    const T * data() const noexcept { return _v; }
    [[nodiscard]] size_t size() const noexcept { return _sz; }
    [[nodiscard]] bool empty() const noexcept { return _sz == 0; }
    T *begin() noexcept { return _v; }
    T *end() noexcept { return _v + _sz; }
private:
    T    * _v;
    size_t _sz;
};

template <typename T>
class ConstArrayRef {
public:
    constexpr ConstArrayRef(const T *v, size_t sz) noexcept : _v(v), _sz(sz) { }
    template<typename A=std::allocator<T>>
    ConstArrayRef(const std::vector<T, A> & v) noexcept : _v(v.data()), _sz(v.size()) { }
    ConstArrayRef(const ArrayRef<T> & v) noexcept : _v(v.data()), _sz(v.size()) { }
    constexpr ConstArrayRef() noexcept : _v(nullptr), _sz(0) {}
    const T & operator [] (size_t i) const noexcept { return _v[i]; }
    [[nodiscard]] size_t size() const noexcept { return _sz; }
    [[nodiscard]] bool empty() const noexcept { return _sz == 0; }
    const T *cbegin() const noexcept { return _v; }
    const T *cend() const noexcept { return _v + _sz; }
    const T *begin() const noexcept { return _v; }
    const T *end() const noexcept { return _v + _sz; }
    const T *data() const noexcept { return _v; }
private:
    const T *_v;
    size_t   _sz;
};

// const-cast for array references; use with care
template <typename T>
ArrayRef<T> unconstify(const ConstArrayRef<T> &ref) {
    return ArrayRef<T>(const_cast<T*>(ref.data()), ref.size());
}

}
