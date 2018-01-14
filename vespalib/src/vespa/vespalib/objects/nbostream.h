// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/util/buffer.h>
#include "nbo.h"

namespace vespalib {

/**
 * Class for streaming data in network byte order, used to serialize
 * and deserialize objects.  The java code corresponding to the C++
 * code using this class will typically use a bytebuffer or a
 * GrowableByteBuffer for serialization and deserialization.
 */
class nbostream
{
 public:
    using Buffer = Array<char>;
    using Alloc = alloc::Alloc;
    enum State { ok=0, eof=0x01};
    nbostream(size_t initialSize=1024);
protected:
    nbostream(const void * buf, size_t sz, bool longLivedBuffer);
public:
    nbostream(const void * buf, size_t sz);
    nbostream(Alloc && buf, size_t sz);
    nbostream(const nbostream & rhs);
    nbostream(nbostream && rhs) noexcept = default;

    ~nbostream();
    nbostream & operator = (const nbostream & rhs);
    nbostream & operator = (nbostream && rhs) noexcept = default;

    nbostream & operator << (double v)     { double n(nbo::n2h(v)); write8(&n); return *this; }
    nbostream & operator >> (double & v)   { double n; read8(&n); v = nbo::n2h(n); return *this; }
    nbostream & operator << (float v)      { float n(nbo::n2h(v)); write4(&n); return *this; }
    nbostream & operator >> (float & v)    { float n; read4(&n); v = nbo::n2h(n); return *this; }
    nbostream & operator << (int64_t v)    { int64_t n(nbo::n2h(v)); write8(&n); return *this; }
    nbostream & operator >> (int64_t & v)  { int64_t n; read8(&n); v = nbo::n2h(n); return *this; }
    nbostream & operator << (uint64_t v)   { uint64_t n(nbo::n2h(v)); write8(&n); return *this; }
    nbostream & operator >> (uint64_t & v) { uint64_t n; read8(&n); v = nbo::n2h(n); return *this; }
    nbostream & operator << (int32_t v)    { int32_t n(nbo::n2h(v)); write4(&n); return *this; }
    nbostream & operator >> (int32_t & v)  { int32_t n; read4(&n); v = nbo::n2h(n); return *this; }
    nbostream & operator << (uint32_t v)   { uint32_t n(nbo::n2h(v)); write4(&n); return *this; }
    nbostream & operator >> (uint32_t & v) { uint32_t n; read4(&n); v = nbo::n2h(n); return *this; }
    nbostream & operator << (int16_t v)    { int16_t n(nbo::n2h(v)); write2(&n); return *this; }
    nbostream & operator >> (int16_t & v)  { int16_t n; read2(&n); v = nbo::n2h(n); return *this; }
    nbostream & operator << (uint16_t v)   { uint16_t n(nbo::n2h(v)); write2(&n); return *this; }
    nbostream & operator >> (uint16_t & v) { uint16_t n; read2(&n); v = nbo::n2h(n); return *this; }
    nbostream & operator << (int8_t v)     { write1(&v); return *this; }
    nbostream & operator >> (int8_t & v)   { read1(&v); return *this; }
    nbostream & operator << (uint8_t v)    { write1(&v); return *this; }
    nbostream & operator >> (uint8_t & v)  { read1(&v); return *this; }
    nbostream & operator << (char v)       { write1(&v); return *this; }
    nbostream & operator >> (char & v)     { read1(&v); return *this; }
    nbostream & operator << (bool v)       { write1(&v); return *this; }
    nbostream & operator >> (bool & v)     { read1(&v); return *this; }
    nbostream & operator << (const std::string & v)      { uint32_t sz(v.size()); (*this) << sz; write(v.c_str(), sz); return *this; }
    nbostream & operator >> (std::string & v) {
        uint32_t sz;
        (*this) >> sz;
        if (__builtin_expect(left() >= sz, true)) {
            v.assign(&_rbuf[_rp], sz);
            _rp += sz;
        } else {
            fail(eof);
        }
        return *this;
     }
    nbostream & operator << (const char * v) { uint32_t sz(strlen(v)); (*this) << sz; write(v, sz); return *this; }
    nbostream & operator << (const vespalib::stringref & v) { uint32_t sz(v.size()); (*this) << sz; write(v.c_str(), sz); return *this; }
    nbostream & operator << (const vespalib::string & v) { uint32_t sz(v.size()); (*this) << sz; write(v.c_str(), sz); return *this; }
    nbostream & operator >> (vespalib::string & v) {
        uint32_t sz; (*this) >> sz;
        if (__builtin_expect(left() >= sz, true)) {
            v.assign(&_rbuf[_rp], sz);
           _rp += sz;
        } else {
            fail(eof);
        }
        return *this;
    }
    template <typename T>
    nbostream & operator << (const vespalib::Array<T> & v) {
        uint32_t sz(v.size());
        (*this) << sz;
        for(size_t i(0); i < sz; i++) {
            (*this) << v[i];
        }
        return *this;
    }
    template <typename T>
    nbostream & operator >> (vespalib::Array<T> & v) {
        uint32_t sz;
        (*this) >> sz;
        v.resize(sz);
        for(size_t i(0); i < sz; i++) {
            (*this) >> v[i];
        }
        return *this;
    }
    template <typename T>
    nbostream & operator << (const std::vector<T> & v) {
        uint32_t sz(v.size());
        (*this) << sz;
        for(size_t i(0); i < sz; i++) {
            (*this) << v[i];
        }
        return *this;
    }
    template <typename T>
    nbostream & operator >> (std::vector<T> & v) {
        uint32_t sz;
        (*this) >> sz;
        v.resize(sz);
        for(size_t i(0); i < sz; i++) {
            (*this) >> v[i];
        }
        return *this;
    }

    template <typename T, typename U>
    nbostream &
    operator<<(const std::pair<T, U> &val)
    {
        *this << val.first << val.second;
        return *this;
    }

    template <typename T, typename U>
    nbostream &
    operator>>(std::pair<T, U> &val)
    {
        *this >> val.first >> val.second;
        return *this;
    }

    size_t size() const { return left(); }
    size_t capacity() const { return _wbuf.size(); }
    bool empty()  const { return size() == 0; }
    const char * c_str() const { return &_rbuf[0]; }
    const char * peek() const { return &_rbuf[_rp]; }
    size_t rp() const { return _rp; }
    nbostream & rp(size_t pos) { if (pos > _wp) fail(eof); _rp = pos; return *this; }
    size_t wp() const { return _wp; }
    State state() const { return _state; }
    bool good() const { return _state == ok; }
    void clear()        { _wbuf.clear(); _wp = _rp = 0; _state = ok; }
    void adjustReadPos(ssize_t adj) { size_t npos = _rp + adj; if (__builtin_expect(npos > _wp, false)) { fail(eof); } _rp = npos; }
    friend std::ostream & operator << (std::ostream & os, const nbostream & s);
    void write(const void *v, size_t sz) {
        if (__builtin_expect(space() < sz, false)) {
            extend(sz);
        }
        memcpy(&_wbuf[_wp], v, sz);
        _wp += sz;
    }
    void read(void *v, size_t sz) {
        if (__builtin_expect(left() >= sz, true)) {
            memcpy(v, &_rbuf[_rp], sz);
            _rp += sz;
        } else {
            memset(v, 0, sz);
            fail(eof);
        }
    }
    void swap(Buffer & buf);
    void swap(nbostream & os);
    /**
     * This flag can be used to tell that a buffer will live at least as long as
     * any objects it will be the backing for. In those cases there is no need for
     * client to make a copy of the buffer content. Use it care and in environments
     * you have full control over.
     *
     */
    bool isLongLivedBuffer() const { return _longLivedBuffer; }
    void reserve(size_t sz);
    void putInt1_4Bytes(uint32_t val) {
        if (val < 0x80) {
            *this << static_cast<uint8_t>(val);
        } else {
            *this << (val | 0x80000000);
        }
    }
    template <typename T>
    T readValue() {
        T val;
        *this >> val;
        return val;
    }
    uint32_t getInt1_4Bytes() {
        char first_byte = *peek();
        if (!(first_byte & 0x80)) {
            return readValue<uint8_t>();
        } else {
            return readValue<uint32_t>() & 0x7fffffff;
        }
    }
    void writeSmallString(vespalib::stringref value) {
        putInt1_4Bytes(value.size());
        write(value.data(), value.size());
    }
    void readSmallString(vespalib::string &value) {
        size_t strSize = getInt1_4Bytes();
        const char *cstr = peek();
        value.assign(cstr, strSize);
        adjustReadPos(strSize);
    }
 private:
    void read1(void *v) { read(v, 1); }
    void read2(void *v) { read(v, 2); }
    void read4(void *v) { read(v, 4); }
    void read8(void *v) { read(v, 8); }
    void write1(const void *v) { write(v, 1); }
    void write2(const void *v) { write(v, 2); }
    void write4(const void *v) { write(v, 4); }
    void write8(const void *v) { write(v, 8); }
    void fail(State s);
    size_t left()  const { return _wp - _rp; }
    size_t space() const { return _wbuf.size() - _wp; }
    void compact();
    void extend(size_t newSize);
    Buffer         _wbuf;
    ConstBufferRef _rbuf;
    size_t         _rp;
    size_t         _wp;
    State          _state;
    const bool     _longLivedBuffer;
};

class nbostream_longlivedbuf : public nbostream {
public:
    nbostream_longlivedbuf(size_t initialSize=1024);
    nbostream_longlivedbuf(const void * buf, size_t sz);
};

}

