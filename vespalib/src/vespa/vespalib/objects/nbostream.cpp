// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "nbostream.h"
#include "hexdump.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <cassert>

namespace vespalib {

nbostream::nbostream(size_t initialSize) :
    _wbuf(),
    _rbuf(),
    _rp(0),
    _wp(0),
    _state(ok),
    _longLivedBuffer(false)
{
    extend(initialSize);
}

nbostream::nbostream(const void * buf, size_t sz, bool longLivedBuffer) :
    _wbuf(),
    _rbuf(buf, sz),
    _rp(0),
    _wp(sz),
    _state(ok),
    _longLivedBuffer(longLivedBuffer)
{ }

nbostream_longlivedbuf::nbostream_longlivedbuf(const void * buf, size_t sz) :
    nbostream(buf, sz, true)
{ }

nbostream_longlivedbuf::nbostream_longlivedbuf(size_t initialSize) :
    nbostream(initialSize)
{ }

nbostream::nbostream(const void * buf, size_t sz) :
    nbostream(buf, sz, false)
{ }

nbostream::nbostream(Alloc && buf, size_t sz) :
    _wbuf(std::move(buf), sz),
    _rbuf(&_wbuf[0], sz),
    _rp(0),
    _wp(sz),
    _state(ok),
    _longLivedBuffer(false)
{
    assert(_wbuf.size() >= sz);
}

nbostream::nbostream(const nbostream & rhs) :
    _wbuf(),
    _rbuf(),
    _rp(0),
    _wp(0),
    _state(ok),
    _longLivedBuffer(false)
{
    extend(rhs.size());
    _wp = rhs.size();
    memcpy(&_wbuf[0], &rhs._rbuf[rhs._rp], _wp);
}

nbostream &
nbostream::operator = (const nbostream & rhs) {
    if (this != &rhs) {
        nbostream n(rhs);
        swap(n);
    }
    return *this;
}

nbostream::~nbostream() = default;

void nbostream::fail(State s)
{
    _state = static_cast<State>(_state | s);
    throw IllegalStateException(make_string("Stream failed bufsize(%zu), readp(%zu), writep(%zu)", _wbuf.size(), _rp, _wp), VESPA_STRLOC);
}

std::ostream & operator << (std::ostream & os, const nbostream & s) {
    return os << HexDump(&s._rbuf[s._rp], s.left());
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
