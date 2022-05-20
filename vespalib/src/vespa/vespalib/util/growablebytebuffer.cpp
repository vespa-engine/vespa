// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "growablebytebuffer.h"
#include <arpa/inet.h>

using namespace vespalib;

GrowableByteBuffer::GrowableByteBuffer(uint32_t initialLen) :
    _buffer(Alloc::alloc(initialLen)),
    _position(0)
{
}

char*
GrowableByteBuffer::allocate(uint32_t len)
{
    size_t need(_position + len);
    if (need > _buffer.size()) {
        uint32_t newSize = vespalib::roundUp2inN(need);
        Alloc newBuf(Alloc::alloc(newSize));
        memcpy(newBuf.get(), _buffer.get(), _position);
        _buffer.swap(newBuf);
    }

    char* pos = static_cast<char *>(_buffer.get()) + _position;
    _position += len;
    return pos;
}

void
GrowableByteBuffer::putBytes(const void * buffer, uint32_t length)
{
    char* buf = allocate(length);
    memcpy(buf, buffer, length);
}

void
GrowableByteBuffer::putShort(uint16_t v)
{
    uint16_t val = htons(v);
    putBytes(reinterpret_cast<const char*>(&val), sizeof(v));
}

void
GrowableByteBuffer::putInt(uint32_t v)
{
    uint32_t val = htonl(v);
    putBytes(reinterpret_cast<const char*>(&val), sizeof(v));
}

void
GrowableByteBuffer::putReverse(const char* buffer, uint32_t length)
{
    char* buf = allocate(length);
    for (uint32_t i = 0; i < length; i++) {
        buf[(length - i - 1)] = buffer[i];
    }
}

void
GrowableByteBuffer::putLong(uint64_t v)
{
    putReverse(reinterpret_cast<const char*>(&v), sizeof(v));
}

void
GrowableByteBuffer::putDouble(double v)
{
    putReverse(reinterpret_cast<const char*>(&v), sizeof(v));
}

void
GrowableByteBuffer::putString(vespalib::stringref v)
{
    putInt(v.size());
    putBytes(v.data(), v.size());
}

void
GrowableByteBuffer::putByte(uint8_t v)
{
    putBytes(reinterpret_cast<const char*>(&v), sizeof(v));
}

void
GrowableByteBuffer::putBoolean(bool v)
{
    putByte(v);
}
