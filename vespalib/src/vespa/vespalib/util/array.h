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
        reverse_iterator() noexcept : _p(nullptr) { }
        reverse_iterator(T * p) noexcept : _p(p) { }
        T & operator   *() noexcept { return _p[0]; }
        T * operator -> () noexcept { return _p; }
        reverse_iterator operator -(size_t n) noexcept { return _p + n; }
        reverse_iterator operator +(size_t n) noexcept { return _p - n; }
        reverse_iterator & operator ++ () noexcept {
            _p--;
            return *this;
        }
        reverse_iterator operator ++ (int) noexcept {
            reverse_iterator prev = *this;
            _p--;
            return prev;
        }
        reverse_iterator & operator -- () noexcept {
            _p++;
            return *this;
        }
        reverse_iterator operator -- (int) noexcept {
            reverse_iterator prev = *this;
            _p++;
            return prev;
        }
        T * get() noexcept { return _p; }
    private:
        friend size_t operator -(reverse_iterator a, reverse_iterator b) noexcept { return b._p - a._p; }
        T * _p;
    };
    class const_reverse_iterator {
    public:
        const_reverse_iterator() noexcept : _p(nullptr) { }
        const_reverse_iterator(const T * p) noexcept : _p(p) { }
        const_reverse_iterator(reverse_iterator i) noexcept : _p(i.get()) { }
        const T & operator   *() const noexcept { return _p[0]; }
        const T * operator -> () const noexcept { return _p; }
        const_reverse_iterator operator -(size_t n) noexcept { return _p + n; }
        const_reverse_iterator operator +(size_t n) noexcept { return _p - n; }
        const_reverse_iterator & operator ++ () noexcept {
            _p--;
            return *this;
        }
        const_reverse_iterator operator ++ (int) noexcept {
            const_reverse_iterator prev = *this;
            _p--;
            return prev;
        }
        const_reverse_iterator & operator -- () noexcept {
            _p++;
            return *this;
        }
        const_reverse_iterator operator -- (int) noexcept {
            const_reverse_iterator prev = *this;
            _p++;
            return prev;
        }

    private:
        friend size_t operator -(const_reverse_iterator a, const_reverse_iterator b) noexcept { return b._p - a._p; }
        const T * _p;
    };
    using Alloc = alloc::Alloc;
    using const_iterator = const T *;
    using iterator = T *;
    using value_type = T;
    using size_type = size_t;

    Array(const Alloc & initial=Alloc::alloc());
    Array(size_t sz, const Alloc & initial=Alloc::alloc());
    Array(Alloc && buf, size_t sz) noexcept;
    Array(Array &&rhs) noexcept;
    Array(size_t sz, T value, const Alloc & initial=Alloc::alloc());
    Array(const_iterator begin, const_iterator end, const Alloc & initial=Alloc::alloc());
    Array(const Array & rhs);
    Array & operator =(const Array & rhs);
    Array & operator =(Array && rhs) noexcept;
    ~Array();
    void swap(Array & rhs) noexcept {
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

    T & back()                              noexcept { return *array(_sz-1); }
    const T & back()                  const noexcept { return *array(_sz-1); }
    const_iterator begin()            const noexcept { return array(0); }
    const_iterator end()              const noexcept { return array(_sz); }
    iterator begin()                        noexcept { return array(0); }
    iterator end()                          noexcept { return array(_sz); }
    const_reverse_iterator rbegin()   const noexcept { return empty() ? array(0) : array(_sz) - 1; }
    const_reverse_iterator rend()     const noexcept { return empty() ? array(0) : array(0) - 1; }
    reverse_iterator rbegin()               noexcept { return empty() ? array(0) : array(_sz) - 1; }
    reverse_iterator rend()                 noexcept { return empty() ? array(0) : array(0) - 1; }
    size_t size()                     const noexcept { return _sz; }
    size_t capacity()                 const noexcept { return _array.size()/sizeof(T); }
    void clear() {
        std::destroy(array(0), array(_sz));
        _sz = 0;
    }
    void reset();
    bool empty()                         const noexcept { return _sz == 0; }
    T * data()                                 noexcept { return static_cast<T *>(_array.get()); }
    const T * data()                     const noexcept { return static_cast<const T *>(_array.get()); }
    T & operator [] (size_t i)                 noexcept { return *array(i); }
    const T & operator [] (size_t i)     const noexcept { return *array(i); }
    bool operator == (const Array & rhs) const noexcept;
    bool operator != (const Array & rhs) const noexcept;

    static Alloc stealAlloc(Array && rhs)  noexcept {
        rhs._sz = 0;
        return std::move(rhs._array);
    }
    Array<T> create() const;
private:
    T *       array(size_t i)       noexcept { return static_cast<T *>(_array.get()) + i; }
    const T * array(size_t i) const noexcept { return static_cast<const T *>(_array.get()) + i; }
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
