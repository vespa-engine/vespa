// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "string.h"
#include <vespa/vespalib/util/alloc.h>
#include <cassert>

namespace vespalib {

template <uint32_t StackSize>
void
small_string<StackSize>::append_from_reserved(size_type sz) noexcept {
    assert(size() + sz <= capacity());
    _resize(size() + sz);
}

template <uint32_t StackSize>
void
small_string<StackSize>::_reserveBytes(size_type newBufferSize) noexcept {
    if (isAllocated()) {
         _buf = (char *) realloc(_buf, newBufferSize);
    } else {
        char *tmp = (char *) malloc(newBufferSize);
        memcpy(tmp, _stack, _sz);
        tmp[_sz] = '\0';
        _buf = tmp;
    }
    _bufferSize = newBufferSize;
}

template <uint32_t StackSize>
small_string<StackSize>&
small_string<StackSize>::replace (size_t p1, size_t n1, const small_string& s, size_t p2, size_t n2 ) noexcept {
    assert(s.size() >= (p2+n2));
    return replace(p1, n1, s.c_str()+p2, n2);
}

template <uint32_t StackSize>
small_string<StackSize>&
small_string<StackSize>::replace (size_t p1, size_t n1, const char *s, size_t n2 ) noexcept {
    assert (size() >= (p1 + n1));
    const size_t newSz = n2 + size() - n1;
    if (n1 < n2) {
        reserve(newSz);
    }
    size_t rest = size()-(p1+n1);
    memmove(buffer()+p1+n2, buffer()+p1+n1, rest);
    memcpy(buffer()+p1, s, n2);
    _resize(newSz);
    return *this;
}

template <uint32_t StackSize>
typename small_string<StackSize>::size_type
small_string<StackSize>::rfind(const char * s, size_type e) const noexcept {
    size_type n = strlen(s);
    if (n <= size()) {
        size_type sz = std::min(size()-n, e);
        const char *b = buffer();
        do {
            if (s[0] == b[sz]) {
                bool found(true);
                for(size_t i(1); found && (i < n); i++) {
                    found = s[i] == b[sz+i];
                }
                if (found) {
                    return sz;
                }
            }
        } while (sz-- > 0);
    }
    return npos;
}

template <uint32_t StackSize>
small_string<StackSize> &
small_string<StackSize>::assign(const void * s, size_type sz) noexcept {
    if (__builtin_expect(capacity() >= sz, true)) {
        char *buf = buffer();
        memmove(buf, s, sz);
        buf[sz] = '\0';
        _sz = sz;
    } else {
        assign_slower(s, sz);
    }
    return *this;
}

template <uint32_t StackSize>
void small_string<StackSize>::assign_slower(const void * s, size_type sz) noexcept
{   
    reset();
    append(s, sz);
}

template <uint32_t StackSize>
void small_string<StackSize>::init_slower(const void *s) noexcept
{   
    _bufferSize = _sz+1;
    _buf = (char *) malloc(_bufferSize);
    memcpy(_buf, s, _sz);
    _buf[_sz] = '\0';
}

template <uint32_t StackSize>
void small_string<StackSize>::appendAlloc(const void * s, size_type addSz) noexcept
{   
    size_type newBufferSize = roundUp2inN(_sz+addSz+1);
    char * buf = (char *) malloc(newBufferSize);
    memcpy(buf, buffer(), _sz);
    if (isAllocated()) {
        free(_buf);
    }
    memcpy(buf+_sz, s, addSz);
    _buf = buf; 
    _bufferSize = newBufferSize;
    _sz += addSz;
    _buf[_sz] = '\0';
}

template <uint32_t StackSize>
small_string<StackSize> &
small_string<StackSize>::insert(size_type start, const void * v, size_type sz) noexcept
{
    if (start < size()) {
        if ((static_cast<const char *>(v)+sz < c_str()) || (c_str()+size() < v)) {
            reserve(size() + sz);
            memmove(begin() + start + sz, c_str() + start, size() - start + 1);
            memcpy(begin() + start, v, sz);
            _sz += sz;
        } else {
            small_string n;
            n.reserve(size() + sz);
            n.append(c_str(), start);
            n.append(v, sz);
            n.append(c_str() + start, size() - start);
            swap(n);
        }
    } else {
        append(v, sz);
    }
    return *this;
}


template <uint32_t StackSize>
small_string<StackSize> &
small_string<StackSize>::append(const void * s, size_type addSz) noexcept
{
    if (needAlloc(addSz)) {
        appendAlloc(s, addSz);
    } else {
        char * buf(buffer());
        memmove(buf+_sz, s, addSz);
        _sz += addSz;
        buf[_sz] = '\0';
    }
    return *this;
}

template<uint32_t StackSize>
small_string<StackSize>
operator + (const small_string<StackSize> & a, const small_string<StackSize> & b) noexcept
{   
    small_string<StackSize> t(a);
    t += b;
    return t;
}

template<uint32_t StackSize>
small_string<StackSize>
operator + (const small_string<StackSize> & a, stringref b) noexcept
{
    small_string<StackSize> t(a);
    t += b;
    return t;
}

template<uint32_t StackSize>
small_string<StackSize>
operator + (stringref a, const small_string<StackSize> & b) noexcept
{
    small_string<StackSize> t(a);
    t += b;
    return t;
}

template<uint32_t StackSize>
small_string<StackSize>
operator + (const small_string<StackSize> & a, const char * b) noexcept
{
    small_string<StackSize> t(a);
    t += b;
    return t;
}

template<uint32_t StackSize>
small_string<StackSize>
operator + (const char * a, const small_string<StackSize> & b) noexcept
{
    small_string<StackSize> t(a);
    t += b;
    return t;
}

}
