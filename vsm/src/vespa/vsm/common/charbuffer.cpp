// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "charbuffer.h"
#include <cstring>

namespace vsm {

CharBuffer::CharBuffer(size_t len) :
    _buffer(len),
    _pos(0)
{ }

void
CharBuffer::put(const char * src, size_t n)
{
    if (n > getRemaining()) {
        resize(_pos + (n * 2));
    }
    char * dst = &_buffer[_pos];
    memcpy(dst, src, n);
    _pos += n;
}

void
CharBuffer::resize(size_t len)
{
    if (len > getLength()) {
        _buffer.resize(len);
    }
}

}

