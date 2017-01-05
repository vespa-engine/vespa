// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "array.h"
#include <stdlib.h>
#include <string.h>

namespace vespalib {

template <typename T>
void construct(T * dest, const T * source, size_t sz, std::tr1::false_type)
{
    for (size_t i(0); i < sz; i++) {
        std::_Construct(dest + i, *(source + i));
    }
}

template <typename T>
void construct(T * dest, const T * source, size_t sz, std::tr1::true_type)
{
    memcpy(dest, source, sz*sizeof(T));
}

template <typename T>
void construct(T * dest, size_t sz, std::tr1::false_type)
{
    for (size_t i(0); i < sz; i++) {
        void *ptr = &dest[i];
        new(ptr) T();
    }
}

template <typename T>
void construct(T * dest, size_t sz, std::tr1::true_type)
{
    (void) dest;
    (void) sz;
}

template <typename T>
void construct(T * dest, size_t sz, T val, std::tr1::false_type)
{
    for (size_t i(0); i < sz; i++) {
        void *ptr = &dest[i];
        new(ptr) T(val);
    }
}

template <typename T>
void construct(T * dest, size_t sz, T val, std::tr1::true_type)
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
    construct(array(0), rhs.array(0), _sz, std::tr1::has_trivial_destructor<T>());
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
Array<T> & Array<T>::operator =(Array && rhs) {
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
        construct(array(_sz), n-_sz, std::tr1::has_trivial_destructor<T>());
    } else if (n < _sz) {
        std::_Destroy(array(n), array(_sz));
    }
    _sz = n;
}

template <typename T>
void move(T * dest, T * source, size_t sz, std::tr1::false_type)
{
    for (size_t i(0); i < sz; i++) {
        std::_Construct(dest + i, std::move(*(source + i)));
        std::_Destroy(source + i);
    }
}

template <typename T>
void move(T * dest, const T * source, size_t sz, std::tr1::true_type)
{
    memcpy(dest, source, sz*sizeof(T));
}

template <typename T>
void Array<T>::increase(size_t n)
{
    Alloc newArray(_array.create(sizeof(T)*n));
    if (capacity() > 0) {
        move(static_cast<T *>(newArray.get()), array(0), _sz, std::tr1::has_trivial_destructor<T>());
    }
    _array.swap(newArray);
}

template <typename T>
Array<T>::Array(const Alloc & initial)
    : _array(initial.create(0)),
      _sz(0)
{ }

template <typename T>
Array<T>::Array(Alloc && buf, size_t sz) :
    _array(std::move(buf)),
    _sz(sz)
{
}


template <typename T>
Array<T>::Array(Array &&rhs)
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
    construct(array(0), _sz, std::tr1::has_trivial_destructor<T>());
}

template <typename T>
Array<T>::Array(size_t sz, T value, const Alloc & initial) :
    _array(initial.create(sz * sizeof(T))),
    _sz(sz)
{
    construct(array(0), _sz, value, std::tr1::has_trivial_destructor<T>());
}

template <typename T>
Array<T>::Array(const_iterator begin_, const_iterator end_, const Alloc & initial) :
    _array(initial.create(begin_ != end_ ? sizeof(T) * (end_-begin_) : 0)),
    _sz(end_-begin_)
{
    construct(array(0), begin_, _sz, std::tr1::has_trivial_destructor<T>());
}

template <typename T>
Array<T>::~Array()
{
    cleanup();
}

template <typename T>
void Array<T>::cleanup()
{
    std::_Destroy(array(0), array(_sz));
    _sz = 0;
    Alloc().swap(_array);
}

}

