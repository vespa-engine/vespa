// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/optimized.h>
#include <stdexcept>
#include <iostream>

namespace vespalib {

std::ostream & operator << (std::ostream & os, const HexDump & hd)
{
    static const char * hexChar = "0123456789ABCDEF";
    os << hd._sz << ' ';
    const uint8_t *c = static_cast<const uint8_t *>(hd._buf);
    for (size_t i(0); i < hd._sz; i++) {
        os << hexChar[c[i] >> 4] << hexChar[c[i] & 0xf];
    }
    return os;
}

void nbostream::fail(State s)
{
    _state = static_cast<State>(_state | s);
    throw IllegalStateException(make_string("Stream failed bufsize(%lu), readp(%ld), writep(%ld)", (unsigned long)_wbuf.size(), _rp, _wp), VESPA_STRLOC);
}

void nbostream::reserve(size_t sz)
{
    if (capacity() < sz) {
        extend(sz - capacity());
    }
}

void nbostream::compact()
{
    memmove(&_wbuf[0], &_rbuf[_rp], left());
    _wp = left();
    _rp = 0;
}


void nbostream::extend(size_t extraSize)
{
    if (&_wbuf[0] != _rbuf.c_str()) {
        _wbuf.resize(roundUp2inN(_rbuf.size() + extraSize));
        compact();
        _rbuf = ConstBufferRef(&_wbuf[0], _wbuf.capacity());
    }
    if (_rp != 0) {
        compact();
    }
    if (space() < extraSize) {
        _wbuf.resize(roundUp2inN(_wbuf.size() + extraSize));
        _rbuf = ConstBufferRef(&_wbuf[0], _wbuf.capacity());
    }
}

void nbostream::swap(Buffer & buf) {
    if (_rp != 0) {
        compact();
    }
    _wbuf.resize(size());
    _wbuf.swap(buf);
    _rbuf = ConstBufferRef(&_wbuf[0], _wbuf.capacity());
    _wp = _wbuf.size();
    _rp = 0;
    _state = ok;
}

void nbostream::swap(nbostream & os)
{
    std::swap(_rp, os._rp);
    std::swap(_wp, os._wp);
    std::swap(_state, os._state);
    _wbuf.swap(os._wbuf);
    std::swap(_rbuf, os._rbuf);
}


}
