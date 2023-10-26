// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstddef>

namespace vespalib {

/**
 * Reference to a memory buffer with external ownership.
 */
class BufferRef
{
public:
    BufferRef() noexcept : _buf(nullptr), _sz(0) { }
    BufferRef(void * buf, size_t sz) noexcept : _buf(buf), _sz(sz) { }
    const char * c_str() const noexcept { return static_cast<const char *>(_buf); }
    char * str()               noexcept { return static_cast<char *>(_buf); }
    const void * data()  const noexcept { return _buf; }
    void * data()              noexcept { return _buf; }
    size_t size()        const noexcept { return _sz; }
    void setSize(size_t sz)    noexcept { _sz = sz; }
    char & operator [] (size_t i) noexcept { return str()[i]; }
    const char & operator [] (size_t i) const noexcept { return c_str()[i]; }
private:
    void * _buf;
    size_t _sz;
};

/**
 * Reference to a constant memory buffer with external ownership.
 */
class ConstBufferRef
{
public:
    ConstBufferRef() noexcept : _buf(nullptr), _sz(0) { }
    ConstBufferRef(const void * buf, size_t sz) noexcept : _buf(buf), _sz(sz) { }
    ConstBufferRef(const BufferRef & rhs) noexcept : _buf(rhs.data()), _sz(rhs.size()) { }
    const char * c_str() const noexcept { return static_cast<const char *>(_buf); }
    const void * data()  const noexcept { return _buf; }
    size_t size()        const noexcept { return _sz; }
    const char & operator [] (size_t i) const noexcept { return c_str()[i]; }
private:
    const void * _buf;
    size_t _sz;
};

}

