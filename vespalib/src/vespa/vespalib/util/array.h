// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <algorithm>
#include <vector>
#include <vespa/vespalib/util/alloc.h>
#include <vespa/vespalib/util/optimized.h>
#include <tr1/type_traits>

namespace vespalib {

template <typename T> class Array;

/**
 * This is a simple wrapper class for a typed array with no memory ownership.
 * It is similar to vespalib::stringref
 **/
template <typename T>
class ArrayRef {
public:
    ArrayRef(T * v, size_t sz) : _v(v), _sz(sz) { }
    ArrayRef(std::vector<T> & v) : _v(&v[0]), _sz(v.size()) { }
    inline ArrayRef(Array<T> &v);
    ArrayRef() : _v(nullptr), _sz(0) {}
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
    inline ConstArrayRef(const Array<T> &v);
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

/**
* This is a small and compact implementation of a resizeable array.
* It has a smaller footprint than std::vector and most important,
* it generates more efficient code.
* It only supports simple objects without constructors/destructors.
**/
template <typename T>
class Array {
public:
    class reverse_iterator {
    public:
        reverse_iterator() : _p(NULL) { }
        reverse_iterator(T * p) : _p(p) { }
        T & operator   *() { return _p[0]; }
        T * operator -> () { return _p; }
        reverse_iterator operator -(size_t n) { return _p + n; }
        reverse_iterator operator +(size_t n) { return _p - n; }
        reverse_iterator & operator ++ () {
            _p--;
            return *this;
        }
        reverse_iterator operator ++ (int) {
            reverse_iterator prev = *this;
            _p--;
            return prev;
        }
        reverse_iterator & operator -- () {
            _p++;
            return *this;
        }
        reverse_iterator operator -- (int) {
            reverse_iterator prev = *this;
            _p++;
            return prev;
        }
        T * get() { return _p; }
    private:
        friend size_t operator -(reverse_iterator a, reverse_iterator b) { return b._p - a._p; }
        T * _p;
    };
    class const_reverse_iterator {
    public:
        const_reverse_iterator() : _p(NULL) { }
        const_reverse_iterator(const T * p) : _p(p) { }
        const_reverse_iterator(reverse_iterator i) : _p(i.get()) { }
        const T & operator   *() const { return _p[0]; }
        const T * operator -> () const { return _p; }
        const_reverse_iterator operator -(size_t n) { return _p + n; }
        const_reverse_iterator operator +(size_t n) { return _p - n; }
        const_reverse_iterator & operator ++ () {
            _p--;
            return *this;
        }
        const_reverse_iterator operator ++ (int) {
            const_reverse_iterator prev = *this;
            _p--;
            return prev;
        }
        const_reverse_iterator & operator -- () {
            _p++;
            return *this;
        }
        const_reverse_iterator operator -- (int) {
            const_reverse_iterator prev = *this;
            _p++;
            return prev;
        }

    private:
        friend size_t operator -(const_reverse_iterator a, const_reverse_iterator b) { return b._p - a._p; }
        const T * _p;
    };
    using Alloc = alloc::Alloc;
    typedef const T * const_iterator;
    typedef T * iterator;
    typedef const T & const_reference;
    typedef T value_type;
    typedef size_t size_type;

    Array(const Alloc & initial=Alloc::alloc()) : _array(initial.create(0)), _sz(0) { }
    Array(size_t sz, const Alloc & initial=Alloc::alloc());
    Array(Alloc && buf, size_t sz);
    Array(Array &&rhs);
    Array(size_t sz, T value, const Alloc & initial=Alloc::alloc());
    Array(const_iterator begin, const_iterator end, const Alloc & initial=Alloc::alloc());
    Array(const Array & rhs);
    Array & operator =(const Array & rhs) {
        if (&rhs != this) {
            Array t(rhs);
            swap(t);
        }
        return *this;
    }
    Array & operator =(Array && rhs) {
        if (&rhs != this) {
            Array t(std::move(rhs));
            swap(t);
        }
        return *this;
    }
    ~Array();
    void swap(Array & rhs) {
        _array.swap(rhs._array);
        std::swap(_sz, rhs._sz);
    }
    void resize(size_t n);
    void assign(const_iterator begin_, const_iterator end_) {
        Array tmp(begin_, end_);
        swap(tmp);
    }
    void reserve(size_t n) {
        if (capacity() < n) {
            increase(n);
        }
    }
    void push_back(const T & v)             { std::_Construct(push_back(), v); }
    iterator push_back()                    { extend(size()+1); return array(_sz++); }
    iterator push_back_fast()               { return array(_sz++); }
    void push_back_fast(const T & v)        { *array(_sz++) = v; }

    void pop_back() {
        _sz--;
        std::_Destroy(array(_sz));
    }

    T & back()                              { return *array(_sz-1); }
    const T & back()                  const { return *array(_sz-1); }
    const_iterator begin()            const { return array(0); }
    const_iterator end()              const { return array(_sz); }
    iterator begin()                        { return array(0); }
    iterator end()                          { return array(_sz); }
    const_reverse_iterator rbegin()   const { return array(_sz) - 1; }
    const_reverse_iterator rend()     const { return array(0) - 1; }
    reverse_iterator rbegin()               { return array(_sz) - 1; }
    reverse_iterator rend()                 { return array(0) - 1; }
    size_t size() const                     { return _sz; }
    size_t byteCapacity() const             { return _array.size(); }
    size_t capacity() const                 { return _array.size()/sizeof(T); }
    void clear() {
        std::_Destroy(array(0), array(_sz));
        _sz = 0;
    }
    bool empty() const                      { return _sz == 0; }
    T & operator [] (size_t i)              { return *array(i); }
    const T & operator [] (size_t i) const  { return *array(i); }
    bool operator == (const Array & rhs) const;
    bool operator != (const Array & rhs) const { return !(*this == rhs); }
private:
    T *       array(size_t i)       { return static_cast<T *>(_array.get()) + i; }
    const T * array(size_t i) const { return static_cast<const T *>(_array.get()) + i; }
    void cleanup();
    void increase(size_t n);
    void extend(size_t n) {
        if (capacity() < n) {
            reserve(roundUp2inN(n));
        }
    }
    Alloc  _array;
    size_t _sz;
};

template <typename T>
ArrayRef<T>::ArrayRef(Array<T> &v)
    : _v(&v[0]),
      _sz(v.size())
{
}

template <typename T>
ConstArrayRef<T>::ConstArrayRef(const Array<T> &v)
    : _v(&v[0]),
      _sz(v.size())
{
}

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
bool Array<T>::operator ==(const Array & rhs) const
{
    bool retval(size() == rhs.size());
    for (size_t i(0); retval && (i < _sz); i++) {
        if (*array(i) != rhs[i]) {
            retval = false;
        }
    }
    return retval;
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

