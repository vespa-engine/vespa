// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "alloc.h"

namespace vespalib {

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

    Array(const Alloc & initial=Alloc::alloc());
    Array(size_t sz, const Alloc & initial=Alloc::alloc());
    Array(Alloc && buf, size_t sz);
    Array(Array &&rhs) noexcept;
    Array(size_t sz, T value, const Alloc & initial=Alloc::alloc());
    Array(const_iterator begin, const_iterator end, const Alloc & initial=Alloc::alloc());
    Array(const Array & rhs);
    Array & operator =(const Array & rhs);
    Array & operator =(Array && rhs) noexcept;
    ~Array();
    void swap(Array & rhs) {
        _array.swap(rhs._array);
        std::swap(_sz, rhs._sz);
    }
    void resize(size_t n);
    void assign(const_iterator begin_, const_iterator end_);
    void reserve(size_t n);
    /**
     * Try to unreserve memory from the underlying memory buffer inplace down to the given limit.
     * The existing memory buffer is unmodified up to the new size (no copying occurs).
     * Returns true if it was possible to unreserve memory, false if not.
     */
    bool try_unreserve(size_t n);
    void push_back(const T & v)             { ::new (static_cast<void *>(push_back())) T(v); }
    iterator push_back()                    { extend(size()+1); return array(_sz++); }
    iterator push_back_fast()               { return array(_sz++); }
    void push_back_fast(const T & v)        { *array(_sz++) = v; }

    void pop_back() {
        _sz--;
        std::destroy_at(array(_sz));
    }

    T & back()                              { return *array(_sz-1); }
    const T & back()                  const { return *array(_sz-1); }
    const_iterator begin()            const { return array(0); }
    const_iterator end()              const { return array(_sz); }
    iterator begin()                        { return array(0); }
    iterator end()                          { return array(_sz); }
    const_reverse_iterator rbegin()   const { return empty() ? array(0) : array(_sz) - 1; }
    const_reverse_iterator rend()     const { return empty() ? array(0) : array(0) - 1; }
    reverse_iterator rbegin()               { return empty() ? array(0) : array(_sz) - 1; }
    reverse_iterator rend()                 { return empty() ? array(0) : array(0) - 1; }
    size_t size() const                     { return _sz; }
    size_t byteSize() const                 { return _sz * sizeof(T); }
    size_t byteCapacity() const             { return _array.size(); }
    size_t capacity() const                 { return _array.size()/sizeof(T); }
    void clear() {
        std::destroy(array(0), array(_sz));
        _sz = 0;
    }
    void reset();
    bool empty() const                      { return _sz == 0; }
    T * data() noexcept                     { return static_cast<T *>(_array.get()); }
    const T * data() const noexcept         { return static_cast<const T *>(_array.get()); }
    T & operator [] (size_t i)              { return *array(i); }
    const T & operator [] (size_t i) const  { return *array(i); }
    bool operator == (const Array & rhs) const;
    bool operator != (const Array & rhs) const;

    static Alloc stealAlloc(Array && rhs) {
        rhs._sz = 0;
        return std::move(rhs._array);
    }
    Array<T> create() const;
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

}
