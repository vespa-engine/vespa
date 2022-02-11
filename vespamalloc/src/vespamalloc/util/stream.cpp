// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "stream.h"
#include <algorithm>
#include <stdio.h>

namespace vespamalloc {

asciistream::asciistream()
    : _rPos(0),
      _wPos(0),
      _buffer(static_cast<char *>(malloc(1024))),
      _sz(1024)
{
}

asciistream::~asciistream()
{
    if (_buffer != nullptr) {
        free(_buffer);
        _buffer = nullptr;
    }
}

asciistream::asciistream(asciistream && rhs) noexcept
    : _rPos(rhs._rPos),
      _wPos(rhs._wPos),
      _buffer(rhs._buffer),
      _sz(rhs._sz)
{
    rhs._rPos = 0;
    rhs._wPos = 0;
    rhs._sz = 0;
    rhs._buffer = nullptr;
}

asciistream &
asciistream::operator = (asciistream && rhs) noexcept
{
    if (this != &rhs) {
        if (_buffer) free(_buffer);

        _rPos = rhs._rPos;
        _wPos = rhs._wPos;
        _buffer = rhs._buffer;
        _sz = rhs._sz;
        rhs._rPos = 0;
        rhs._wPos = 0;
        rhs._sz = 0;
        rhs._buffer = nullptr;
    }
    return *this;
}

asciistream::asciistream(const asciistream & rhs) :
    _rPos(0),
    _wPos(rhs._wPos - rhs._rPos),
    _buffer(static_cast<char *>(malloc(_wPos+1))),
    _sz(_wPos)
{
    memcpy(_buffer, (rhs._buffer + rhs._rPos), _sz);
    _buffer[_wPos] = 0;
}

asciistream &
asciistream::operator = (const asciistream & rhs)
{
    if (this != &rhs) {
        asciistream newStream(rhs);
        swap(newStream);
    }
    return *this;
}


void asciistream::swap(asciistream & rhs)
{
    std::swap(_rPos, rhs._rPos);
    std::swap(_wPos, rhs._wPos);
    std::swap(_buffer, rhs._buffer);
    std::swap(_sz, rhs._sz);
}

asciistream & asciistream::operator << (int32_t v)
{
    char tmp[16];
    int len = snprintf(tmp, sizeof(tmp), "%d", v);
    write(tmp, len);
    return *this;
}

asciistream & asciistream::operator << (uint32_t v)
{
    char tmp[16];
    int len = snprintf(tmp, sizeof(tmp), "%u", v);
    write(tmp, len);
    return *this;
}

asciistream & asciistream::operator << (int64_t v)
{
    char tmp[32];
    int len = snprintf(tmp, sizeof(tmp), "%ld", v);
    write(tmp, len);
    return *this;
}

asciistream & asciistream::operator << (uint64_t v)
{
    char tmp[32];
    int len = snprintf(tmp, sizeof(tmp), "%lu", v);
    write(tmp, len);
    return *this;
}

asciistream & asciistream::operator << (float v)
{
    char tmp[64];
    int len = snprintf(tmp, sizeof(tmp), "%g", v);
    write(tmp, len);
    return *this;
}

asciistream & asciistream::operator << (double v)
{
    char tmp[64];
    int len = snprintf(tmp, sizeof(tmp), "%g", v);
    write(tmp, len);
    return *this;
}

void asciistream::write(const void * buf, size_t len)
{
    if (_rPos == _wPos) {
        _rPos = _wPos = 0;
    }
    if ((_sz - _wPos) < len + 1) {
        _buffer = static_cast<char *>(realloc(_buffer, _sz * 2 + len));
        _sz = _sz * 2 + len + 1;
    }
    memcpy(_buffer + _wPos, buf, len);
    _wPos += len;
    _buffer[_wPos] = 0;
}

size_t asciistream::read(void * buf, size_t len)
{
    size_t available = _wPos - _rPos;
    size_t toRead(std::min(len, available));
    memcpy(buf, _buffer+_rPos, toRead);
    _rPos += toRead;
    return toRead;
}

}
