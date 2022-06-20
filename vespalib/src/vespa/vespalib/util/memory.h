// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <cstring>
#include <cstdlib>

/// Macro to give you number of elements in a defined array.
#define VESPA_NELEMS(a)    (sizeof(a)/sizeof(a[0]))

namespace vespalib {

inline void *memcpy_safe(void *dest, const void *src, size_t n) noexcept {
    if (n == 0) [[unlikely]] {
        return dest;
    }
    return memcpy(dest, src, n);
}

inline void *memmove_safe(void *dest, const void *src, size_t n) noexcept {
    if (n == 0) [[unlikely]] {
        return dest;
    }
    return memmove(dest, src, n);
}

inline int memcmp_safe(const void *s1, const void *s2, size_t n) noexcept {
    if (n == 0) [[unlikely]] {
        return 0;
    }
    return memcmp(s1, s2, n);
}

/**
 * Wrapper class that enables unaligned access to trivial values.
 **/
template <typename T>
class Unaligned {
private:
    char _data[sizeof(T)];

public:
    Unaligned() = delete;
    Unaligned(const Unaligned &) = delete;
    Unaligned(Unaligned &&) = delete;

    Unaligned &operator=(const Unaligned &) = default;
    Unaligned &operator=(Unaligned &&) = default;

    static_assert(std::is_trivial_v<T>);
    static_assert(alignof(T) > 1, "value is always aligned");

    constexpr static Unaligned &at(void *p) noexcept {
        return *reinterpret_cast<Unaligned*>(p);
    }
    constexpr static const Unaligned &at(const void *p) noexcept {
        return *reinterpret_cast<const Unaligned*>(p);
    }

    constexpr static Unaligned *ptr(void *p) noexcept {
        return reinterpret_cast<Unaligned*>(p);
    }
    constexpr static const Unaligned *ptr(const void *p) noexcept {
        return reinterpret_cast<const Unaligned*>(p);
    }

    T read() const noexcept {
        T value;
        static_assert(sizeof(_data) == sizeof(value));
        memcpy(&value, _data, sizeof(value));
        return value;
    }
    void write(const T &value) noexcept {
        static_assert(sizeof(_data) == sizeof(value));
        memcpy(_data, &value, sizeof(value));
    }
    operator T () const noexcept { return read(); }
    Unaligned &operator=(const T &value) noexcept {
        write(value);
        return *this;
    }
};

/**
 * @brief Keep ownership of memory allocated via malloc()
 *
 * A MallocAutoPtr does for c type alloced objects as std::unique_ptr does
 * for newed objects. Allocate it, use it and forget about it. It is cleaned up.
 * Follows std::unique_ptr semantics in most cases, but is less general.
 */
class MallocAutoPtr
{
public:
    /**
     * @brief constructor from pointer
     *
     * Note: the pointer must have been allocated with malloc()
     **/
    MallocAutoPtr(void *p=nullptr) noexcept :  _p(p) { }

    /** @brief destructor, calls free() on owned pointer */
    ~MallocAutoPtr() { cleanup(); }

    MallocAutoPtr(MallocAutoPtr && rhs) noexcept : _p(rhs._p) { rhs._p = nullptr; }
    MallocAutoPtr & operator = (MallocAutoPtr && rhs) noexcept {
        cleanup();
        std::swap(_p, rhs._p);
        return *this;
    }

    MallocAutoPtr(const MallocAutoPtr & rhs) = delete;
    MallocAutoPtr  & operator = (const MallocAutoPtr & rhs) = delete;

    const void * get() const noexcept { return _p; }
    void * get()             noexcept { return _p; }
private:
    void cleanup() noexcept {
        if (_p) {
            free(_p);
            _p = nullptr;
        }
    }
    void *_p;
};

/**
 * @brief Container for data allocated with malloc().
 *
 * A MallocPtr is a container for data allocated by malloc,
 * (the old fashioned way).  You create any buffer of appropriate size.
 * It does copy and assignement as you expect, copying data.
 * And of course it cleans up after you.
 */
class MallocPtr
{
public:
    /**
     * @brief construct a containing for some bytes of data
     *
     * with default sz=0 you get an empty container
     * @param sz the number of bytes to allocate
     **/
    MallocPtr(const size_t sz=0) noexcept : _sz(sz), _p(_sz ? malloc(sz) : nullptr) {
        if (_p == nullptr) {
            _sz = 0;
        }
    }
    /** @brief destructor doing free() if needed */
    ~MallocPtr() { cleanup(); }

    MallocPtr(MallocPtr && rhs) noexcept :
        _sz(rhs.size()), _p(rhs._p)
    {
        rhs._sz = 0;
        rhs._p = nullptr;
    }

    /**
     * @brief copy constructor
     *
     * Does deep copy of contents (using memcpy).
     * @param rhs container to copy
     **/
    MallocPtr(const MallocPtr & rhs) noexcept
        : _sz(rhs.size()), _p(_sz ? malloc(_sz) : nullptr)
    {
        if (_p == nullptr) {
            _sz = 0;
        }
        memcpy_safe(_p, rhs.get(), _sz);
    }

    /**
     * @brief copying assignment operator
     *
     * works like destruct + copy construct.
     * @param rhs container to copy
     **/
    MallocPtr & operator = (const MallocPtr & rhs) noexcept {
        if (this != &rhs) {
            MallocPtr tmp(rhs);
            swap(tmp);
        }
        return *this;
    }
    MallocPtr & operator = (MallocPtr && rhs) noexcept {
        if (this != &rhs) {
            cleanup();
            _sz = rhs._sz;
            _p = rhs._p;
            rhs._sz = 0;
            rhs._p = 0;
        }
        return *this;
    }

    /**
     * @brief swap contents with another container
     *
     * does not copy anything, just swaps pointers.
     **/
    void swap(MallocPtr & rhs) noexcept {
        std::swap(_sz, rhs._sz); std::swap(_p, rhs._p);
    }

    /** @brief number of bytes contained */
    size_t size()            const { return _sz; }
    /** @brief standard way of saying empty */
    bool empty()            const { return _sz==0; }

    /** @brief value access, returns nullptr if empty */
    const void * get()       const { return _p; }
    /** @brief value access, returns nullptr if empty */
    void * get()                   { return _p; }

    /** @brief access memory as a C string, returns nullptr if container empty */
    const char * c_str()     const { return static_cast<const char *>(_p); }
    /** @brief access memory as a C string, returns nullptr if container empty */
    char *   str()                 { return static_cast<char *>(_p); }

    /** @brief value access, returns nullptr if container empty */
    operator const void * () const { return _p; }
    /** @brief value access, returns nullptr if container empty */
    operator void * ()             { return _p; }
    const char & operator [] (size_t i) const { return c_str()[i]; }
    char & operator [] (size_t i)  { return str()[i]; }
    void resize(size_t sz) { realloc(sz); }
    void reset() { cleanup(); }
    void * release() { void *p(_p); _p = 0; _sz = 0; return p; }

    /** @brief resize */
    void
    realloc(size_t sz)
    {
        if (sz == 0) {
            cleanup();
        } else {
            void *p = ::realloc(_p, sz);
            if (p != nullptr) {
                _p = p;
                _sz = sz;
            } else {
                cleanup();
            }
        }
    }
private:
    void cleanup() noexcept {
        if (_p) {
            free(_p);
            _p = nullptr;
            _sz = 0;
        }
    }
    size_t _sz;
    void  *_p;
};

/**
 * @brief Holder for polymorphic objects with copy and assignment.
 *
 * This is used when you want to store polymorphic objects where you only know
 * that they are of a certain base class.  The base class must have a virtual
 * clone() that can do the copying on assignment and copy constructor.
 */
template <typename T>
class CloneablePtr
{
public:
    /** @brief construct (from pointer) */
    CloneablePtr(T * p=nullptr) noexcept : _p(p) { }

    CloneablePtr(CloneablePtr && rhs) noexcept : _p(rhs._p) { rhs._p = nullptr; }
    CloneablePtr & operator = (CloneablePtr && rhs) noexcept {
        cleanup();
        std::swap(_p, rhs._p);
        return *this;
    }

    /** @brief destructor doing delete on owned pointer */
    ~CloneablePtr() noexcept { cleanup(); }

    /** @brief copy constructor, does deep copy using clone() */
    CloneablePtr(const CloneablePtr & rhs) : _p(nullptr) {
        if (rhs._p) {
            _p = static_cast<T *> (rhs._p->clone());
        }
    }

    /** @brief move constructor, takes over ownership */
    CloneablePtr(std::unique_ptr<T> &&rhs) noexcept
        : _p(rhs.release())
    {
    }

    /** @brief assignment operator, does deep copy using clone() */
    CloneablePtr & operator = (const CloneablePtr & rhs) {
        if (this != &rhs) {
            CloneablePtr tmp(rhs);
            swap(tmp);
        }
        return *this;
    }

    /** @brief move assignment operator, takes over ownership */
    CloneablePtr &operator=(std::unique_ptr<T> &&rhs)
    {
        cleanup();
        _p = rhs.release();
        return *this;
    }

    /** @brief swap contents */
    void swap(CloneablePtr & rhs)  noexcept { std::swap(_p, rhs._p); }

    /** @brief value access */
    const T * get()          const { return _p; }
    /** @brief value access */
    T * get()                      { return _p; }
    /** @brief value access */
    T * operator -> ()             { return _p; }
    /** @brief value access */
    const T * operator -> () const { return _p; }
    /** @brief value access */
    T & operator * ()              { return *_p; }
    /** @brief value access */
    const T & operator * ()  const { return *_p; }
    explicit operator bool () const { return _p != nullptr; }
    bool operator ! ()        const { return _p == nullptr; }
    bool operator == (const CloneablePtr & b) const {
        return ((_p == nullptr) && (b._p == nullptr)) ||
               ((_p && b._p) && (*_p == *b._p) );
    }

    /** @brief reset with new contents; behaves like destruct + construct */
    void reset(T * p=nullptr)       { cleanup(); _p = p; }

    /** @brief release owned pointer */
    T * release()                   { T * p(_p); _p = nullptr; return p; }
private:
    void cleanup() noexcept {
        if (_p) {
            delete _p;
            _p = nullptr;
        }
    }
    T *_p;
};

}

