// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "array.h"
#include <cstdlib>
#include <cstring>
#include <type_traits>

namespace vespalib {

template <typename T>
void construct(T * dest, const T * source, size_t sz, std::false_type)
{
    for (size_t i(0); i < sz; i++) {
        ::new (static_cast<void *>(dest + i)) T(*(source + i));
    }
}

template <typename T>
void construct(T * dest, const T * source, size_t sz, std::true_type) noexcept
{
    memcpy(dest, source, sz*sizeof(T));
}

template <typename T>
void construct(T * dest, size_t sz, std::false_type)
{
    for (size_t i(0); i < sz; i++) {
        void *ptr = &dest[i];
        new(ptr) T();
    }
}

template <typename T>
void construct(T * dest, size_t sz, std::true_type) noexcept
{
    (void) dest;
    (void) sz;
}

template <typename T>
void construct(T * dest, size_t sz, T val, std::false_type)
{
    for (size_t i(0); i < sz; i++) {
        void *ptr = &dest[i];
        new(ptr) T(val);
    }
}

template <typename T>
void construct(T * dest, size_t sz, T val, std::true_type)
{
    for (size_t i(0); i < sz; i++) {
        dest[i] = val;
    }
}

template <typename T>
Array<T>::Array(const Array & rhs)
    : _array(rhs._array.create(rhs.size() * sizeof(T))),
      _sz(rhs.size())
{
    if (_sz > 0) [[likely]] {
        construct(array(0), rhs.array(0), _sz, std::is_trivially_copyable<T>());
    }
}

template <typename T>
Array<T> & Array<T>::operator =(const Array & rhs)
{
    if (&rhs != this) {
        Array t(rhs);
        swap(t);
    }
    return *this;
}

template <typename T>
Array<T> & Array<T>::operator =(Array && rhs) noexcept {
    if (&rhs != this) {
        Array t(std::move(rhs));
        swap(t);
    }
    return *this;
}

template <typename T>
void Array<T>::assign(const_iterator begin_, const_iterator end_) {
    Array tmp(begin_, end_);
    swap(tmp);
}
template <typename T>
void Array<T>::reserve(size_t n) {
    if (capacity() < n) {
        increase(n);
    }
}

template <typename T>
bool Array<T>::try_unreserve(size_t n)
{
    if (n >= capacity()) {
        return false;
    }
    if (n < size()) {
        return false;
    }
    return _array.resize_inplace(n * sizeof(T));
}

template <typename T>
void Array<T>::resize(size_t n)
{
    if (n > capacity()) {
        reserve(n);
    }
    if (n > _sz) {
        construct(array(_sz), n-_sz, std::is_trivially_default_constructible<T>());
    } else if (n < _sz) {
        std::destroy(array(n), array(_sz));
    }
    _sz = n;
}

template <typename T>
void move(T * dest, T * source, size_t sz, std::false_type) noexcept
{
    for (size_t i(0); i < sz; i++) {
        ::new (static_cast<void *>(dest + i)) T(std::move(*(source + i)));
        std::destroy_at(source + i);
    }
}

template <typename T>
void move(T * dest, const T * source, size_t sz, std::true_type) noexcept
{
    memcpy(dest, source, sz*sizeof(T));
}

template <typename T>
void Array<T>::increase(size_t n)
{
    Alloc newArray(_array.create(sizeof(T)*n));
    if (capacity() > 0) {
        move(static_cast<T *>(newArray.get()), array(0), _sz, std::is_trivially_copyable<T>());
    }
    _array.swap(newArray);
}

template <typename T>
Array<T>::Array(const Alloc & initial)
    : _array(initial.create(0)),
      _sz(0)
{ }

template <typename T>
Array<T>::Array(Alloc && buf, size_t sz) noexcept :
    _array(std::move(buf)),
    _sz(sz)
{
}


template <typename T>
Array<T>::Array(Array &&rhs) noexcept
    :  _array(std::move(rhs._array)),
       _sz(rhs._sz)
{
    rhs._sz = 0;
}

template <typename T>
Array<T>::Array(size_t sz, const Alloc & initial) :
    _array(initial.create(sz * sizeof(T))),
    _sz(sz)
{
    construct(array(0), _sz, std::is_trivially_default_constructible<T>());
}

template <typename T>
Array<T>::Array(size_t sz, T value, const Alloc & initial) :
    _array(initial.create(sz * sizeof(T))),
    _sz(sz)
{
    construct(array(0), _sz, value, std::is_trivially_copyable<T>());
}

template <typename T>
Array<T>::Array(const_iterator begin_, const_iterator end_, const Alloc & initial) :
    _array(initial.create(begin_ != end_ ? sizeof(T) * (end_-begin_) : 0)),
    _sz(end_-begin_)
{
    construct(array(0), begin_, _sz, std::is_trivially_copyable<T>());
}

template <typename T>
Array<T>::~Array()
{
    cleanup();
}

template <typename T>
void Array<T>::cleanup()
{
    std::destroy(array(0), array(_sz));
    _sz = 0;
    Alloc().swap(_array);
}

template <typename T>
void Array<T>::reset()
{
    std::destroy(array(0), array(_sz));
    _sz = 0;
    _array.reset();
}

template <typename T>
Array<T>
Array<T>::create() const
{
    return Array<T>(_array); // Use same memory allocator
}

}

