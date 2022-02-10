// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstring>
#include <cstdint>

namespace vespamalloc {

class asciistream
{
public:
    asciistream();
    ~asciistream();
    asciistream(asciistream && rhs) noexcept;
    asciistream & operator = (asciistream && ) noexcept;
    asciistream(const asciistream & rhs);
    asciistream & operator = (const asciistream & rhs);
    void swap(asciistream & rhs);
    asciistream & operator << (char v)                { write(&v, 1); return *this; }
    asciistream & operator << (unsigned char v)       { write(&v, 1); return *this; }
    asciistream & operator << (const char * v)        { if (v != nullptr) { write(v, strlen(v)); } return *this; }
    asciistream & operator << (int32_t v);
    asciistream & operator << (uint32_t v);
    asciistream & operator << (int64_t v);
    asciistream & operator << (uint64_t v);
    asciistream & operator << (float v);
    asciistream & operator << (double v);
    const char * c_str() const { return _buffer + _rPos; }
    size_t        size() const { return _wPos - _rPos; }
    size_t    capacity() const { return _sz; }
private:
    void write(const void * buf, size_t len);
    size_t read(void * buf, size_t len);
    size_t _rPos;
    size_t _wPos;
    char * _buffer;
    size_t _sz;
};

class string : public asciistream
{
public:
    string(const char * v = nullptr) : asciistream() { *this << v; }
    string & operator += (const char * v) { *this << v; return *this; }
    string & operator += (const asciistream & v) { *this << v.c_str(); return *this; }
};

}

