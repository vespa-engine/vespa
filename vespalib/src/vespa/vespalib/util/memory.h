// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <cstring>
#include <cstdlib>

/// Macro to give you number of elements in a defined array.
#define VESPA_NELEMS(a)    (sizeof(a)/sizeof(a[0]))

namespace vespalib {

/**
 * @brief Helper class
 *
 * Helper to enable auto_arr instances as parameters and return values.
 * You should not use this class directly.
 **/
template<class OtherArray> struct auto_arr_ref {
    OtherArray* _array;
    auto_arr_ref(OtherArray* a) : _array(a) {}
};

/**
 * @brief std::unique_ptr for arrays
 *
 * This class behaves just like unique_ptr, but wraps a pointer allocated
 * with new[]; so it will call delete[] when doing cleanup.
 */
template <class Array> class auto_arr {
private:
    Array* _array; // actual owned array (if any)

public:
    /**
     * @brief constructor from pointer
     *
     * Note: the pointer must have been allocated with new[]
     **/
    explicit auto_arr(Array* a = 0) throw() : _array(a) {}

    /**
     * @brief "copy" contructor
     *
     * Note: non-const parameter; transfers ownership
     * instead of copying.
     **/
    auto_arr(auto_arr& a) throw() : _array(a.release()) {}

    /**
     * @brief assignment operator
     *
     * Note: non-const parameter; transfers ownership
     * instead of copying.
     **/
    auto_arr& operator=(auto_arr& a) throw() {
        reset(a.release());
        return *this;
    }

    /** @brief destructor, calls delete[] on owned pointer */
    ~auto_arr() throw() { delete[] _array; }

    /** @brief value access */
    Array& operator [] (size_t i) const throw() { return _array[i]; }

    /** @brief access underlying array */
    Array* get() const throw() { return _array; }

    /**
     * @brief release ownership
     *
     * The caller of release() must take responsibility for eventually calling delete[].
     * @return previously owned pointer
     **/
    Array* release() throw() {
        Array* tmp = _array;
        _array = 0;
        return tmp;
    }

    /**
     * @brief reset value
     *
     * Behaves like destruct then construct.
     **/
    void reset(Array* a = 0) throw() {
        delete[] _array;
        _array = a;
    }

    /**
     * @brief special implicit conversion from auxiliary type
     * to enable parameter / return value passing
     **/
    auto_arr(auto_arr_ref<Array> ref) throw()
        : _array(ref._array) {}

    /**
     * @brief special assignment from auxiliary type
     * to enable parameter / return value passing
     **/
    auto_arr& operator=(auto_arr_ref<Array> ref) throw() {
        reset(ref._array);
        return *this;
    }

    /**
     * @brief special implicit conversion to auxiliary type
     * to enable parameter / return value passing
     **/
    operator auto_arr_ref<Array>() throw() {
        return auto_arr_ref<Array>(this->release());
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
    MallocAutoPtr(void *p=nullptr) :  _p(p) { }

    /** @brief destructor, calls free() on owned pointer */
    ~MallocAutoPtr() { cleanup(); }

    MallocAutoPtr(MallocAutoPtr && rhs) : _p(rhs._p) { rhs._p = nullptr; }
    MallocAutoPtr & operator = (MallocAutoPtr && rhs) {
        cleanup();
        std::swap(_p, rhs._p);
        return *this;
    }

    /**
     * @brief "copy" contructor
     *
     * Note: non-const parameter; transfers ownership
     * instead of copying.
     **/
    MallocAutoPtr(const MallocAutoPtr & rhs)
        : _p(rhs._p) { const_cast<MallocAutoPtr &>(rhs)._p = nullptr; }

    /**
     * @brief assignment operator
     *
     * Note: non-const parameter; transfers ownership
     * instead of copying.
     **/
    MallocAutoPtr  & operator = (const MallocAutoPtr & rhs) {
        if (this != &rhs) {
            MallocAutoPtr tmp(rhs);
            swap(tmp);
        }
        return *this;
    }

    /** @brief swap contents */
    void swap(MallocAutoPtr & rhs) { std::swap(_p, rhs._p); }

    /** @brief value access */
    const void * get() const { return _p; }

    /** @brief value access */
    void * get()             { return _p; }
private:
    void cleanup() {
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
        if (_sz != 0) {
            memcpy(_p, rhs.get(), _sz);
        }
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

